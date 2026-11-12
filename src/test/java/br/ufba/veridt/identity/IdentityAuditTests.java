package br.ufba.veridt.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.event.TransactionalEventListener;

import br.ufba.veridt.TestcontainersConfiguration;
import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.internal.sender.EmailSender;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/** ID-07: cada operação de identidade registra quem, quando, operação e resultado, sem dados pessoais. */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import({ TestcontainersConfiguration.class, IdentityAuditTests.TestBeans.class })
class IdentityAuditTests {

	private static final Duration ESPERA = Duration.ofSeconds(10);
	private static final String EMAIL = "ana@exemplo.com";

	record Linha(UUID usuario, String operacao, String resultado, String detalhe) {
	}

	@MockitoBean
	private EmailSender emailSender;

	private final MockMvc mvc;
	private final JsonMapper json;
	private final JdbcTemplate jdbc;
	private final EmailRecorder emails;

	private OffsetDateTime inicio;
	private UUID ana;

	IdentityAuditTests(MockMvc mvc, JsonMapper json, JdbcTemplate jdbc, EmailRecorder emails) {
		this.mvc = mvc;
		this.json = json;
		this.jdbc = jdbc;
		this.emails = emails;
	}

	@BeforeEach
	void preparar() throws Exception {
		emails.clear();
		jdbc.update("DELETE FROM event_publication");
		jdbc.update("DELETE FROM spring_session");
		jdbc.update("DELETE FROM token_recuperacao_senha");
		jdbc.update("DELETE FROM usuario");
		inicio = OffsetDateTime.now(ZoneOffset.UTC);
		MvcResult cadastro = postar("/api/auth/register", TestData.comum(EMAIL, TestData.cpf("529982247")), null)
			.andExpect(status().isCreated())
			.andReturn();
		ana = UUID.fromString(json.readTree(cadastro.getResponse().getContentAsString()).path("id").asString());
	}

	@Test
	void cadastroERegistrado() {
		assertThat(linhas()).containsExactly(new Linha(ana, "CADASTRO", "SUCESSO", null));
	}

	@Test
	void loginELogoutSaoRegistrados() throws Exception {
		Cookie sessao = login(TestData.SENHA);
		postar("/api/auth/logout", null, sessao).andExpect(status().isNoContent());

		assertThat(linhas()).containsExactly(new Linha(ana, "CADASTRO", "SUCESSO", null),
				new Linha(ana, "LOGIN", "SUCESSO", null), new Linha(ana, "LOGOUT", "SUCESSO", null));
	}

	@Test
	void falhaDeLoginGuardaAContaAtacadaENuncaOEmail() throws Exception {
		postar("/api/auth/login", Map.of("email", EMAIL, "password", "Errada#123"), null)
			.andExpect(status().isUnauthorized());
		postar("/api/auth/login", Map.of("email", "ninguem@exemplo.com", "password", "Errada#123"), null)
			.andExpect(status().isUnauthorized());

		assertThat(linhas()).endsWith(new Linha(ana, "LOGIN", "FALHA", "CREDENCIAIS_INVALIDAS"),
				new Linha(null, "LOGIN", "FALHA", "CREDENCIAIS_INVALIDAS"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE detalhe LIKE '%@%'", Integer.class))
			.isZero();
	}

	@Test
	void redefinicaoDeSenhaComSucessoEComTokenInvalidoSaoRegistradas() throws Exception {
		postar("/api/auth/password-reset", Map.of("email", EMAIL), null).andExpect(status().isAccepted());
		await().atMost(ESPERA).until(() -> emails.received().size() == 1);
		String link = emails.received().getFirst().parameters().get("link");
		String token = link.substring(link.indexOf("token=") + "token=".length());

		postar("/api/auth/password-reset/confirm", Map.of("token", token, "newPassword", "Nova#Senha2"), null)
			.andExpect(status().isNoContent());
		postar("/api/auth/password-reset/confirm", Map.of("token", token, "newPassword", "Nova#Senha2"), null)
			.andExpect(status().isUnprocessableContent());

		assertThat(linhas()).endsWith(new Linha(ana, "REDEFINICAO_SENHA", "SUCESSO", null),
				new Linha(null, "REDEFINICAO_SENHA", "FALHA", "TOKEN_INVALIDO"));
	}

	private List<Linha> linhas() {
		return jdbc.query("SELECT usuario_id, operacao, resultado, detalhe FROM auditoria WHERE ocorrido_em >= ? "
				+ "ORDER BY ocorrido_em", (rs, i) -> new Linha(rs.getObject("usuario_id", UUID.class),
						rs.getString("operacao"), rs.getString("resultado"), rs.getString("detalhe")),
				inicio);
	}

	private Cookie login(String senha) throws Exception {
		MvcResult result = postar("/api/auth/login", Map.of("email", EMAIL, "password", senha), null)
			.andExpect(status().isNoContent())
			.andReturn();
		String header = result.getResponse().getHeaders("Set-Cookie").stream()
			.filter(value -> value.startsWith("SESSION="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Cookie SESSION ausente"));
		return new Cookie("SESSION", header.substring("SESSION=".length(), header.indexOf(';')));
	}

	private org.springframework.test.web.servlet.ResultActions postar(String rota, Object corpo, Cookie sessao)
			throws Exception {
		var request = post(rota).with(TestData.csrfDaSpa()).contentType(MediaType.APPLICATION_JSON);
		if (corpo != null) {
			request.content(json.writeValueAsString(corpo));
		}
		if (sessao != null) {
			request.cookie(sessao);
		}
		return mvc.perform(request);
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
