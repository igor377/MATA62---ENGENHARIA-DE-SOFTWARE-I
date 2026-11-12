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
import br.ufba.veridt.identity.internal.domain.Uf;
import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.internal.sender.EmailSender;
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

	@Test
	void usuarioCadastradoConsegueFazerLogin() throws Exception {
		postar(CADASTRO, TestData.comum("ana@exemplo.com", CPF_ANA)).andExpect(status().isCreated());

		login("ana@exemplo.com", TestData.SENHA).andExpect(status().isNoContent());
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

		await().atMost(ESPERA).until(() -> emails.received().size() == 1);
		assertThat(emails.received().getFirst().recipient()).isEqualTo("ana@exemplo.com");
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
		await().atMost(ESPERA).until(() -> emails.received().size() == 1);
		String link = emails.received().getFirst().parameters().get("link");
		String token = link.substring(link.indexOf("token=") + "token=".length());

		postar(CONFIRMACAO, Map.of("token", token, "newPassword", "Nova#Senha2")).andExpect(status().isNoContent());

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
