package br.ufba.veridt.identity;

import static br.ufba.veridt.identity.TestData.oab;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import br.ufba.veridt.TestcontainersConfiguration;
import br.ufba.veridt.identity.internal.domain.OabRegistrationType;
import br.ufba.veridt.identity.internal.domain.PasswordResetToken;
import br.ufba.veridt.identity.internal.domain.Uf;
import br.ufba.veridt.identity.internal.domain.User;
import br.ufba.veridt.identity.internal.dto.OabRegistrationResponse;
import br.ufba.veridt.identity.internal.dto.ResetPasswordRequest;
import br.ufba.veridt.identity.internal.dto.UserResponse;
import br.ufba.veridt.identity.internal.exception.CpfAlreadyRegisteredException;
import br.ufba.veridt.identity.internal.exception.EmailAlreadyRegisteredException;
import br.ufba.veridt.identity.internal.exception.InvalidResetTokenException;
import br.ufba.veridt.identity.internal.exception.OabAlreadyRegisteredException;
import br.ufba.veridt.identity.internal.repository.OabRegistrationRepository;
import br.ufba.veridt.identity.internal.repository.PasswordResetTokenRepository;
import br.ufba.veridt.identity.internal.repository.UserRepository;
import br.ufba.veridt.identity.internal.service.PasswordResetService;
import br.ufba.veridt.identity.internal.service.RegistrationService;
import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.EmailType;
import br.ufba.veridt.shared.audit.AuditLog;
import br.ufba.veridt.shared.audit.AuditRepository;
import jakarta.validation.ConstraintViolationException;

@ApplicationModuleTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
// O teste de módulo só carrega o pacote identity; a auditoria compartilhada vem de shared.
@Import({ TestcontainersConfiguration.class, IdentityModuleTests.TestBeans.class, AuditLog.class,
		AuditRepository.class })
class IdentityModuleTests {

	private static final Duration ESPERA = Duration.ofSeconds(10);
	private static final String CPF_ANA = TestData.cpf("529982247");
	private static final String CPF_BRUNO = TestData.cpf("111444777");
	private static final String CPF_CARLA = TestData.cpf("935411347");

	private final RegistrationService registration;
	private final PasswordResetService passwordReset;
	private final UserRepository users;
	private final OabRegistrationRepository oabRegistrations;
	private final PasswordResetTokenRepository tokens;
	private final PasswordEncoder passwordEncoder;
	private final FindByIndexNameSessionRepository<? extends Session> sessions;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;
	private final EmailRecorder emails;

	IdentityModuleTests(RegistrationService registration, PasswordResetService passwordReset, UserRepository users,
			OabRegistrationRepository oabRegistrations, PasswordResetTokenRepository tokens,
			PasswordEncoder passwordEncoder, FindByIndexNameSessionRepository<? extends Session> sessions,
			JdbcTemplate jdbc, PlatformTransactionManager transactionManager, EmailRecorder emails) {
		this.registration = registration;
		this.passwordReset = passwordReset;
		this.users = users;
		this.oabRegistrations = oabRegistrations;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.jdbc = jdbc;
		this.transaction = new TransactionTemplate(transactionManager);
		this.emails = emails;
	}

	@BeforeEach
	void limpar() {
		emails.clear();
		jdbc.update("DELETE FROM event_publication");
		jdbc.update("DELETE FROM spring_session");
		jdbc.update("DELETE FROM token_recuperacao_senha");
		jdbc.update("DELETE FROM usuario");
	}

	// ID-01 e ID-03: cadastro

	@Test
	void cadastroGravaSenhaSoComoHashArgon2idComParametrosDoOwasp() {
		UserResponse response = registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));

		User user = users.findById(response.id()).orElseThrow();
		assertThat(user.getPasswordHash()).startsWith("$argon2id$")
			.contains("m=19456,t=2,p=1")
			.doesNotContain(TestData.SENHA);
		assertThat(passwordEncoder.matches(TestData.SENHA, user.getPasswordHash())).isTrue();
	}

	@Test
	void cadastroNormalizaEmailECpf() {
		String cpfComMascara = CPF_ANA.substring(0, 3) + "." + CPF_ANA.substring(3, 6) + "." + CPF_ANA.substring(6, 9)
				+ "-" + CPF_ANA.substring(9);

		UserResponse response = registration.register(TestData.comum("Ana@Exemplo.COM", cpfComMascara));

		User user = users.findById(response.id()).orElseThrow();
		assertThat(user.getEmail()).isEqualTo("ana@exemplo.com");
		assertThat(user.getCpf()).isEqualTo(CPF_ANA);
		assertThat(response.email()).isEqualTo("ana@exemplo.com");
	}

	@Test
	void emailRepetidoERecusadoMesmoComOutraCaixa() {
		registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));

		assertThatThrownBy(() -> registration.register(TestData.comum("ANA@exemplo.com", CPF_BRUNO)))
			.isInstanceOf(EmailAlreadyRegisteredException.class)
			.hasMessage("E-mail já cadastrado.");
	}

	@Test
	void cpfRepetidoERecusado() {
		registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));

		assertThatThrownBy(() -> registration.register(TestData.comum("outra@exemplo.com", CPF_ANA)))
			.isInstanceOf(CpfAlreadyRegisteredException.class);
	}

	@Test
	void cpfInvalidoERecusado() {
		assertThatThrownBy(() -> registration.register(TestData.comum("ana@exemplo.com", "529.982.247-26")))
			.isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("cpf");
		assertThat(users.count()).isZero();
	}

	@Test
	void senhaFracaERecusada() {
		assertThatThrownBy(
				() -> registration.register(TestData.comum("ana@exemplo.com", CPF_ANA, "senhafraca", null)))
			.isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("letra, número e símbolo");
	}

	// Item 5: inscrições na OAB

	@Test
	void advogadoPodeTerVariasInscricoes() {
		UserResponse response = registration.register(TestData.advogado("bruno@exemplo.com", CPF_BRUNO,
				oab("12345", Uf.BA, OabRegistrationType.PRINCIPAL), oab("777", Uf.SP, OabRegistrationType.SUPLEMENTAR)));

		assertThat(response.oabRegistrations()).containsExactlyInAnyOrder(
				new OabRegistrationResponse("12345", Uf.BA, OabRegistrationType.PRINCIPAL),
				new OabRegistrationResponse("777", Uf.SP, OabRegistrationType.SUPLEMENTAR));
		assertThat(oabRegistrations.findByUserId(response.id())).hasSize(2);
	}

	@Test
	void advogadoExigeAoMenosUmaInscricao() {
		assertThatThrownBy(() -> registration.register(TestData.advogado("bruno@exemplo.com", CPF_BRUNO)))
			.isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("ao menos uma inscrição");
	}

	@Test
	void numeroInvalidoOuSemUfOuSemTipoERecusado() {
		assertThatThrownBy(() -> registration.register(
				TestData.advogado("bruno@exemplo.com", CPF_BRUNO, oab("1234567", Uf.BA, OabRegistrationType.PRINCIPAL))))
			.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> registration.register(
				TestData.advogado("bruno@exemplo.com", CPF_BRUNO, oab("000", Uf.BA, OabRegistrationType.PRINCIPAL))))
			.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> registration.register(
				TestData.advogado("bruno@exemplo.com", CPF_BRUNO, oab("123", null, OabRegistrationType.PRINCIPAL))))
			.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> registration.register(TestData.advogado("bruno@exemplo.com", CPF_BRUNO, oab("123", Uf.BA, null))))
			.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void usuarioComumNaoPodeInformarOab() {
		var inscricoes = List.of(oab("12345", Uf.BA, OabRegistrationType.PRINCIPAL));

		assertThatThrownBy(
				() -> registration.register(TestData.comum("ana@exemplo.com", CPF_ANA, TestData.SENHA, inscricoes)))
			.isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("só pode ser informado por advogado");
	}

	@Test
	void duasInscricoesPrincipaisSaoRecusadas() {
		assertThatThrownBy(() -> registration.register(TestData.advogado("bruno@exemplo.com", CPF_BRUNO,
				oab("111", Uf.BA, OabRegistrationType.PRINCIPAL), oab("222", Uf.SP, OabRegistrationType.PRINCIPAL))))
			.isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("uma inscrição principal");
	}

	@Test
	void inscricoesRepetidasNaListaSaoRecusadasConsiderandoZerosAEsquerda() {
		assertThatThrownBy(() -> registration.register(TestData.advogado("bruno@exemplo.com", CPF_BRUNO,
				oab("123", Uf.BA, OabRegistrationType.SUPLEMENTAR), oab("00123", Uf.BA, OabRegistrationType.SUPLEMENTAR))))
			.isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("repetidas");
	}

	@Test
	void mesmaInscricaoNaoPodeSerUsadaPorOutroAdvogadoMesmoComZerosAEsquerda() {
		registration.register(TestData.advogado("bruno@exemplo.com", CPF_BRUNO, oab("123", Uf.BA, OabRegistrationType.PRINCIPAL)));

		assertThatThrownBy(() -> registration.register(
				TestData.advogado("carla@exemplo.com", CPF_CARLA, oab("000123", Uf.BA, OabRegistrationType.PRINCIPAL))))
			.isInstanceOf(OabAlreadyRegisteredException.class)
			.hasMessage("Inscrição na OAB já cadastrada.");
	}

	@Test
	void mesmoNumeroEUfComOutroTipoEAceito() {
		registration.register(TestData.advogado("bruno@exemplo.com", CPF_BRUNO, oab("123", Uf.BA, OabRegistrationType.PRINCIPAL)));

		UserResponse carla = registration.register(
				TestData.advogado("carla@exemplo.com", CPF_CARLA, oab("123", Uf.BA, OabRegistrationType.ESTAGIARIO)));

		assertThat(oabRegistrations.findByUserId(carla.id())).hasSize(1);
	}

	// ID-02: garantias no banco, independentes da aplicação

	@Test
	void bancoRecusaEmailECpfDuplicadosEEmailComMaiuscula() {
		inserirUsuario(UUID.randomUUID(), "ana@exemplo.com", CPF_ANA, "COMUM");

		assertThatThrownBy(() -> inserirUsuario(UUID.randomUUID(), "ana@exemplo.com", CPF_BRUNO, "COMUM"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inserirUsuario(UUID.randomUUID(), "outra@exemplo.com", CPF_ANA, "COMUM"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inserirUsuario(UUID.randomUUID(), "Maiuscula@exemplo.com", CPF_BRUNO, "COMUM"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void bancoRecusaAdvogadoSemInscricao() {
		assertThatThrownBy(() -> inserirUsuario(UUID.randomUUID(), "adv@exemplo.com", CPF_BRUNO, "ADVOGADO"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(users.count()).isZero();
	}

	@Test
	void bancoRecusaInscricaoParaUsuarioComum() {
		UUID id = UUID.randomUUID();
		inserirUsuario(id, "ana@exemplo.com", CPF_ANA, "COMUM");

		assertThatThrownBy(() -> inserirInscricao(id, "123", "BA", "PRINCIPAL"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void bancoRecusaRemoverAUnicaInscricaoDoAdvogado() {
		UUID id = advogadoNoBanco("adv@exemplo.com", CPF_BRUNO, "123");

		assertThatThrownBy(() -> jdbc.update("DELETE FROM inscricao_oab WHERE usuario_id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void bancoRecusaNumeroComZeroAEsquerdaInscricaoDuplicadaESegundaPrincipal() {
		UUID id = advogadoNoBanco("adv@exemplo.com", CPF_BRUNO, "123");
		UUID outro = advogadoNoBanco("outro@exemplo.com", CPF_ANA, "456");

		assertThatThrownBy(() -> inserirInscricao(id, "0123", "SP", "SUPLEMENTAR"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inserirInscricao(outro, "123", "BA", "PRINCIPAL"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inserirInscricao(id, "789", "SP", "PRINCIPAL"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inserirInscricao(id, "789", "XX", "SUPLEMENTAR"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inserirInscricao(id, "789", "SP", "OUTRO"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ID-05: recuperação de senha

	@Test
	void pedidoRodaEmSegundoPlano() {
		registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));

		passwordReset.requestReset("ana@exemplo.com");

		await().atMost(ESPERA).until(() -> emails.received().size() == 1);
		assertThat(emails.threads()).doesNotContain(Thread.currentThread().getName());
	}

	@Test
	void pedidoParaEmailInexistenteNaoFazNada() {
		passwordReset.requestReset("ninguem@exemplo.com");

		await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).until(() -> emails.received().isEmpty());
		assertThat(tokens.count()).isZero();
	}

	@Test
	void pedidoEnviaLinkEGuardaSoOHashDoToken() {
		registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));

		EmailRequested email = pedirRecuperacao("ANA@exemplo.com");

		assertThat(email.recipient()).isEqualTo("ana@exemplo.com");
		assertThat(email.type()).isEqualTo(EmailType.RECUPERACAO_SENHA);
		assertThat(email.parameters()).containsEntry("nome", "Ana Souza").containsEntry("validadeMinutos", "15");
		String token = tokenDoLink(email);
		PasswordResetToken salvo = tokens.findAll().getFirst();
		assertThat(salvo.getTokenHash()).isEqualTo(sha256(token)).isNotEqualTo(token);
		assertThat(salvo.getExpiresAt()).isBetween(Instant.now().plusSeconds(14 * 60), Instant.now().plusSeconds(15 * 60));
	}

	@Test
	void redefinicaoTrocaSenhaEInvalidaOToken() {
		UserResponse ana = registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));
		String token = tokenDoLink(pedirRecuperacao("ana@exemplo.com"));

		passwordReset.resetPassword(new ResetPasswordRequest(token, "Nova#Senha2"));

		User user = users.findById(ana.id()).orElseThrow();
		assertThat(passwordEncoder.matches("Nova#Senha2", user.getPasswordHash())).isTrue();
		assertThatThrownBy(() -> passwordReset.resetPassword(new ResetPasswordRequest(token, "Outra#Senha3")))
			.isInstanceOf(InvalidResetTokenException.class);
	}

	@Test
	void tokenExpiradoOuInexistenteERecusado() {
		UserResponse ana = registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));
		Instant passado = Instant.now().minusSeconds(3600);
		tokens.save(new PasswordResetToken(ana.id(), sha256("expirado"), passado, passado.plusSeconds(900)));

		assertThatThrownBy(() -> passwordReset.resetPassword(new ResetPasswordRequest("expirado", "Nova#Senha2")))
			.isInstanceOf(InvalidResetTokenException.class)
			.hasMessage("Link de redefinição de senha inválido ou expirado.");
		assertThatThrownBy(() -> passwordReset.resetPassword(new ResetPasswordRequest("inexistente", "Nova#Senha2")))
			.isInstanceOf(InvalidResetTokenException.class);
	}

	@Test
	void novoPedidoInvalidaOTokenAnterior() {
		registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));
		String primeiro = tokenDoLink(pedirRecuperacao("ana@exemplo.com"));
		emails.clear();
		String segundo = tokenDoLink(pedirRecuperacao("ana@exemplo.com"));

		assertThatThrownBy(() -> passwordReset.resetPassword(new ResetPasswordRequest(primeiro, "Nova#Senha2")))
			.isInstanceOf(InvalidResetTokenException.class);
		passwordReset.resetPassword(new ResetPasswordRequest(segundo, "Nova#Senha2"));
	}

	@Test
	void redefinicaoEncerraAsSessoesSoDepoisDoCommit() {
		UserResponse ana = registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));
		criarSessao(sessions, ana.id());
		criarSessao(sessions, ana.id());
		String token = tokenDoLink(pedirRecuperacao("ana@exemplo.com"));

		transaction.executeWithoutResult(status -> {
			passwordReset.resetPassword(new ResetPasswordRequest(token, "Nova#Senha2"));
			assertThat(sessions.findByPrincipalName(ana.id().toString())).hasSize(2);
		});

		assertThat(sessions.findByPrincipalName(ana.id().toString())).isEmpty();
	}

	@Test
	void redefinicaoDesfeitaMantemAsSessoes() {
		UserResponse ana = registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));
		criarSessao(sessions, ana.id());
		String token = tokenDoLink(pedirRecuperacao("ana@exemplo.com"));

		transaction.executeWithoutResult(status -> {
			passwordReset.resetPassword(new ResetPasswordRequest(token, "Nova#Senha2"));
			status.setRollbackOnly();
		});

		assertThat(sessions.findByPrincipalName(ana.id().toString())).hasSize(1);
	}

	@Test
	void novaSenhaFracaERecusada() {
		registration.register(TestData.comum("ana@exemplo.com", CPF_ANA));
		String token = tokenDoLink(pedirRecuperacao("ana@exemplo.com"));

		assertThatThrownBy(() -> passwordReset.resetPassword(new ResetPasswordRequest(token, "fraca")))
			.isInstanceOf(ConstraintViolationException.class);
	}

	private EmailRequested pedirRecuperacao(String email) {
		passwordReset.requestReset(email);
		await().atMost(ESPERA).until(() -> emails.received().size() == 1);
		return emails.received().getFirst();
	}

	/** Genérico porque o tipo concreto da sessão do Spring Session JDBC não é público. */
	private static <S extends Session> void criarSessao(FindByIndexNameSessionRepository<S> repository, UUID userId) {
		S session = repository.createSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.toString());
		repository.save(session);
	}

	/** Usuário e inscrição na mesma transação, como exige a verificação adiada para o commit. */
	private UUID advogadoNoBanco(String email, String cpf, String numero) {
		UUID id = UUID.randomUUID();
		transaction.executeWithoutResult(status -> {
			inserirUsuario(id, email, cpf, "ADVOGADO");
			inserirInscricao(id, numero, "BA", "PRINCIPAL");
		});
		return id;
	}

	private void inserirUsuario(UUID id, String email, String cpf, String tipo) {
		jdbc.update("INSERT INTO usuario (id, nome_completo, email, senha_hash, cpf, tipo, criado_em) "
				+ "VALUES (?, 'Teste', ?, 'hash', ?, ?, now())", id, email, cpf, tipo);
	}

	private void inserirInscricao(UUID userId, String numero, String uf, String tipo) {
		jdbc.update("INSERT INTO inscricao_oab (id, usuario_id, numero, uf, tipo, criado_em) "
				+ "VALUES (?, ?, ?, ?, ?, now())", UUID.randomUUID(), userId, numero, uf, tipo);
	}

	private static String tokenDoLink(EmailRequested email) {
		String link = email.parameters().get("link");
		assertThat(link).startsWith("http://localhost:8080/redefinir-senha?token=");
		return link.substring(link.indexOf("token=") + "token=".length());
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Recebe os eventos após o commit, de qualquer thread: o pedido de recuperação roda em segundo plano. */
	static class EmailRecorder {

		private final List<EmailRequested> received = new CopyOnWriteArrayList<>();
		private final List<String> threads = new CopyOnWriteArrayList<>();

		@TransactionalEventListener
		void on(EmailRequested event) {
			threads.add(Thread.currentThread().getName());
			received.add(event);
		}

		List<EmailRequested> received() {
			return List.copyOf(received);
		}

		List<String> threads() {
			return List.copyOf(threads);
		}

		void clear() {
			received.clear();
			threads.clear();
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestBeans {

		@Bean
		EmailRecorder emailRecorder() {
			return new EmailRecorder();
		}

	}

}
