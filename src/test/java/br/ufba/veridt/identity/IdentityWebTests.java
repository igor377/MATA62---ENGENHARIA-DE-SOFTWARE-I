package br.ufba.veridt.identity;

import static br.ufba.veridt.identity.TestData.oab;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.event.TransactionalEventListener;

import br.ufba.veridt.TestcontainersConfiguration;
import br.ufba.veridt.identity.internal.domain.OabRegistrationType;
import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.EmailType;
import br.ufba.veridt.notification.internal.sender.EmailSender;
import br.ufba.veridt.shared.validation.Uf;
import tools.jackson.databind.json.JsonMapper;

/** Rotas de cadastro e recuperação de senha pela cadeia real de filtros. */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import({ TestcontainersConfiguration.class, IdentityWebTests.TestBeans.class })
class IdentityWebTests {

	private static final Duration ESPERA = Duration.ofSeconds(10);
	private static final String CPF_ANA = TestData.cpf("529982247");
	private static final String CPF_BRUNO = TestData.cpf("111444777");
	private static final String CADASTRO = "/api/auth/register";
	private static final String RECUPERACAO = "/api/auth/password-reset";
	private static final String CONFIRMACAO = "/api/auth/password-reset/confirm";
	private static final String CONFIRMAR_EMAIL = "/api/auth/email-confirmation/confirm";
	private static final String REENVIAR = "/api/auth/email-confirmation/resend";

	@MockitoBean
	private EmailSender emailSender;

	private final MockMvc mvc;
	private final JsonMapper json;
	private final JdbcTemplate jdbc;
	private final EmailRecorder emails;

	IdentityWebTests(MockMvc mvc, JsonMapper json, JdbcTemplate jdbc, EmailRecorder emails) {
		this.mvc = mvc;
		this.json = json;
		this.jdbc = jdbc;
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

	@Test
	void cadastroRetorna201SemSenhaNemCpf() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").exists())
			.andExpect(jsonPath("$.email").value("ana@exemplo.com"))
			.andExpect(jsonPath("$.type").value("COMUM"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.cpf").doesNotExist());
	}

	@Test
	void cadastroDeAdvogadoRetornaAsInscricoes() throws Exception {
		postar(CADASTRO, TestData.advogado("bruno@exemplo.com", CPF_BRUNO, oab("12345", Uf.BA, OabRegistrationType.PRINCIPAL)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.oabRegistrations[0].number").value("12345"))
			.andExpect(jsonPath("$.oabRegistrations[0].state").value("BA"))
			.andExpect(jsonPath("$.oabRegistrations[0].type").value("PRINCIPAL"));
	}

	// ID-08: confirmação de e-mail

	@Test
	void loginSoDepoisDeConfirmarOEmail() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA)).andExpect(status().isCreated());

		login("ana@exemplo.com", TestData.SENHA)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EMAIL_NAO_CONFIRMADO"));
		postar(CONFIRMAR_EMAIL, Map.of("token", token(emailDo(EmailType.CONFIRMACAO_EMAIL))))
			.andExpect(status().isNoContent());

		login("ana@exemplo.com", TestData.SENHA).andExpect(status().isNoContent());
	}

	@Test
	void semConfirmacaoSenhaErradaContinuaComO401GenericoENenhumaSessaoECriada() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA)).andExpect(status().isCreated());

		login("ana@exemplo.com", "Errada#123")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
		login("ana@exemplo.com", TestData.SENHA).andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session", Integer.class)).isZero();
	}

	@Test
	void tokenDeConfirmacaoUsadoOuInventadoRetorna422() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA)).andExpect(status().isCreated());
		String token = token(emailDo(EmailType.CONFIRMACAO_EMAIL));
		postar(CONFIRMAR_EMAIL, Map.of("token", token)).andExpect(status().isNoContent());

		postar(CONFIRMAR_EMAIL, Map.of("token", token))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALIDO"));
		postar(CONFIRMAR_EMAIL, Map.of("token", "inventado"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALIDO"));
	}

	@Test
	void reenvioRetorna202ComEmailCadastradoOuNao() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA)).andExpect(status().isCreated());
		jdbc.update("UPDATE token_confirmacao_email SET criado_em = criado_em - INTERVAL '2 minutes'");

		postar(REENVIAR, Map.of("email", "ana@exemplo.com")).andExpect(status().isAccepted());
		postar(REENVIAR, Map.of("email", "ninguem@exemplo.com")).andExpect(status().isAccepted());

		await().atMost(ESPERA).until(() -> emailsDo(EmailType.CONFIRMACAO_EMAIL).size() == 2);
		assertThat(emailsDo(EmailType.CONFIRMACAO_EMAIL)).extracting(EmailRequested::recipient)
			.containsOnly("ana@exemplo.com");
	}

	@Test
	void rotasDeConfirmacaoExigemCsrf() throws Exception {
		mvc.perform(post(CONFIRMAR_EMAIL).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
	}

	@Test
	void cadastroRepetidoRetorna409Padronizado() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA)).andExpect(status().isCreated());

		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_BRUNO))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_JA_CADASTRADO"));
	}

	@Test
	void cadastroInvalidoRetorna400ComOsCampos() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", "529.982.247-26"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DADOS_INVALIDOS"))
			.andExpect(jsonPath("$.errors[0].field").value("cpf"));
	}

	@Test
	void advogadoSemInscricaoRetorna400NoCampoDasInscricoes() throws Exception {
		postar(CADASTRO, TestData.advogado("bruno@exemplo.com", CPF_BRUNO))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("oabRegistrations"));
	}

	@Test
	void rotaSemCsrfRetorna403() throws Exception {
		mvc.perform(post(CADASTRO).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(TestData.comum("ana@exemplo.com", CPF_ANA))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
	}

	@Test
	void pedidoDeRecuperacaoRetorna202ComEmailCadastradoOuNao() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA)).andExpect(status().isCreated());

		postar(RECUPERACAO, Map.of("email", "ana@exemplo.com")).andExpect(status().isAccepted());
		postar(RECUPERACAO, Map.of("email", "ninguem@exemplo.com")).andExpect(status().isAccepted());

		await().atMost(ESPERA).until(() -> emailsDo(EmailType.RECUPERACAO_SENHA).size() == 1);
		assertThat(emailDo(EmailType.RECUPERACAO_SENHA).recipient()).isEqualTo("ana@exemplo.com");
	}

	@Test
	void pedidoComEmailMalformadoRetorna400() throws Exception {
		postar(RECUPERACAO, Map.of("email", "nao-e-email"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("email"));
	}

	@Test
	void fluxoCompletoDeRecuperacaoTrocaASenha() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA)).andExpect(status().isCreated());
		postar(RECUPERACAO, Map.of("email", "ana@exemplo.com")).andExpect(status().isAccepted());
		await().atMost(ESPERA).until(() -> emailsDo(EmailType.RECUPERACAO_SENHA).size() == 1);
		String token = token(emailDo(EmailType.RECUPERACAO_SENHA));

		postar(CONFIRMACAO, Map.of("token", token, "newPassword", "Nova#Senha2")).andExpect(status().isNoContent());

		// Sem ter confirmado o e-mail: o link de redefinição também prova que o endereço é dela.
		login("ana@exemplo.com", "Nova#Senha2").andExpect(status().isNoContent());
		login("ana@exemplo.com", TestData.SENHA).andExpect(status().isUnauthorized());
		postar(CONFIRMACAO, Map.of("token", token, "newPassword", "Outra#Senha3"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALIDO"));
	}

	@Test
	void confirmacaoComSenhaFracaRetorna400() throws Exception {
		postar(CONFIRMACAO, Map.of("token", "qualquer", "newPassword", "fraca"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("newPassword"));
	}

	private ResultActions postar(String rota, Object corpo) throws Exception {
		return mvc.perform(post(rota).with(TestData.csrfDaSpa())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(corpo)));
	}

	private List<EmailRequested> emailsDo(EmailType tipo) {
		return emails.received().stream().filter(email -> email.type() == tipo).toList();
	}

	private EmailRequested emailDo(EmailType tipo) {
		return emailsDo(tipo).getFirst();
	}

	private static String token(EmailRequested email) {
		String link = email.parameters().get("link");
		return link.substring(link.indexOf("token=") + "token=".length());
	}

	private ResultActions login(String email, String senha) throws Exception {
		return postar("/api/auth/login", Map.of("email", email, "password", senha));
	}

	static class EmailRecorder {

		private final List<EmailRequested> received = new CopyOnWriteArrayList<>();

		@TransactionalEventListener
		void on(EmailRequested event) {
			received.add(event);
		}

		List<EmailRequested> received() {
			return List.copyOf(received);
		}

		void clear() {
			received.clear();
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
