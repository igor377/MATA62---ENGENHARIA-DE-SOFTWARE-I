package br.ufba.veridt.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.TestcontainersConfiguration;
import br.ufba.veridt.identity.internal.dto.UserResponse;
import br.ufba.veridt.identity.internal.service.RegistrationService;
import jakarta.servlet.http.Cookie;

/** ID-01 e ID-04 pela cadeia real de filtros: Spring Session JDBC + Spring Security. */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import({ TestcontainersConfiguration.class, SecurityIntegrationTests.Endpoints.class })
class SecurityIntegrationTests {

	private static final String CPF = TestData.cpf("529982247");

	private final MockMvc mvc;
	private final RegistrationService registration;
	private final JdbcTemplate jdbc;

	private UserResponse ana;

	SecurityIntegrationTests(MockMvc mvc, RegistrationService registration, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.registration = registration;
		this.jdbc = jdbc;
	}

	@BeforeEach
	void preparar() {
		jdbc.update("DELETE FROM spring_session");
		jdbc.update("DELETE FROM token_recuperacao_senha");
		jdbc.update("DELETE FROM usuario");
		ana = registration.register(TestData.comum("ana@exemplo.com", CPF));
	}

	@Test
	void rotaSemAutenticacaoRetorna401Padronizado() throws Exception {
		mvc.perform(get("/teste/eu"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
	}

	@Test
	void requisicaoAnonimaNaoCriaSessao() throws Exception {
		MvcResult result = mvc.perform(get("/teste/eu")).andExpect(status().isUnauthorized()).andReturn();

		assertThat(result.getResponse().getHeaders("Set-Cookie")).noneMatch(value -> value.startsWith("SESSION="));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session", Integer.class)).isZero();
	}

	@Test
	void healthEPublico() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void loginValidoAutenticaEONomeDoPrincipalEOUuid() throws Exception {
		Cookie sessao = login("ANA@exemplo.com", TestData.SENHA, null);

		mvc.perform(get("/teste/eu").cookie(sessao))
			.andExpect(status().isOk())
			.andExpect(content().string(ana.id().toString()));
	}

	@Test
	void credencialInvalidaNaoRevelaSeOEmailExiste() throws Exception {
		String senhaErrada = falhaDeLogin("ana@exemplo.com", "Errada#123");
		String emailInexistente = falhaDeLogin("ninguem@exemplo.com", "Errada#123");

		assertThat(senhaErrada).isEqualTo(emailInexistente);
	}

	@Test
	void loginTrocaOIdDaSessao() throws Exception {
		Cookie primeira = login("ana@exemplo.com", TestData.SENHA, null);

		Cookie segunda = login("ana@exemplo.com", TestData.SENHA, primeira);

		assertThat(segunda.getValue()).isNotEqualTo(primeira.getValue());
		mvc.perform(get("/teste/eu").cookie(primeira)).andExpect(status().isUnauthorized());
		mvc.perform(get("/teste/eu").cookie(segunda)).andExpect(status().isOk());
	}

	@Test
	void logoutInvalidaASessao() throws Exception {
		Cookie sessao = login("ana@exemplo.com", TestData.SENHA, null);

		mvc.perform(post("/api/auth/logout").cookie(sessao).with(TestData.csrfDaSpa())).andExpect(status().isNoContent());

		mvc.perform(get("/teste/eu").cookie(sessao)).andExpect(status().isUnauthorized());
	}

	@Test
	void loginSemCsrfERecusado() throws Exception {
		mvc.perform(requisicaoDeLogin("ana@exemplo.com", TestData.SENHA))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
	}

	/** Sem o atalho {@code csrf()}: faz o que a SPA fará, com o cookie e o cabeçalho reais. */
	@Test
	void fluxoRealDaSpaComTokenCsrf() throws Exception {
		Cookie csrfLogin = csrfCookie();
		MvcResult login = mvc.perform(requisicaoDeLogin("ana@exemplo.com", TestData.SENHA).cookie(csrfLogin)
			.header("X-XSRF-TOKEN", csrfLogin.getValue())).andExpect(status().isNoContent()).andReturn();
		Cookie sessao = sessionCookie(login);
		mvc.perform(get("/teste/eu").cookie(sessao)).andExpect(status().isOk());

		Cookie csrfLogout = csrfCookie();
		mvc.perform(post("/api/auth/logout").cookie(sessao, csrfLogout).header("X-XSRF-TOKEN", csrfLogout.getValue()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/teste/eu").cookie(sessao)).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenCsrfErradoERecusado() throws Exception {
		Cookie csrf = csrfCookie();

		mvc.perform(requisicaoDeLogin("ana@exemplo.com", TestData.SENHA).cookie(csrf).header("X-XSRF-TOKEN", "errado"))
			.andExpect(status().isForbidden());
	}

	@Test
	void logoutSemSessaoRetorna401() throws Exception {
		mvc.perform(post("/api/auth/logout").with(TestData.csrfDaSpa())).andExpect(status().isUnauthorized());
	}

	@Test
	void loginSemCamposRetorna400() throws Exception {
		mvc.perform(post("/api/auth/login").with(TestData.csrfDaSpa()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DADOS_INVALIDOS"));
	}

	@Test
	void meSemSessaoRetorna401() throws Exception {
		mvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
	}

	@Test
	void meRetornaOUsuarioDaSessaoSemCpf() throws Exception {
		Cookie sessao = login("ana@exemplo.com", TestData.SENHA, null);

		mvc.perform(get("/api/me").cookie(sessao))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ana.id().toString()))
			.andExpect(jsonPath("$.email").value("ana@exemplo.com"))
			.andExpect(jsonPath("$.type").value("COMUM"))
			.andExpect(jsonPath("$.cpf").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	private Cookie csrfCookie() throws Exception {
		MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
		String header = result.getResponse().getHeaders("Set-Cookie").stream()
			.filter(value -> value.startsWith("XSRF-TOKEN="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Cookie XSRF-TOKEN ausente"));
		return new Cookie("XSRF-TOKEN", header.substring("XSRF-TOKEN=".length(), header.indexOf(';')));
	}

	private static MockHttpServletRequestBuilder requisicaoDeLogin(String email, String senha) {
		return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, senha));
	}

	private Cookie login(String email, String senha, Cookie sessaoAtual) throws Exception {
		var request = requisicaoDeLogin(email, senha).with(TestData.csrfDaSpa());
		if (sessaoAtual != null) {
			request.cookie(sessaoAtual);
		}
		MvcResult result = mvc.perform(request).andExpect(status().isNoContent()).andReturn();
		return sessionCookie(result);
	}

	private String falhaDeLogin(String email, String senha) throws Exception {
		MvcResult result = mvc.perform(requisicaoDeLogin(email, senha).with(TestData.csrfDaSpa()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"))
			.andReturn();
		// O timestamp muda a cada resposta; o resto do corpo precisa ser idêntico.
		return result.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]+\",?", "");
	}

	/** O Spring Session escreve o cookie direto no cabeçalho {@code Set-Cookie}. */
	private static Cookie sessionCookie(MvcResult result) {
		String header = result.getResponse().getHeaders("Set-Cookie").stream()
			.filter(value -> value.startsWith("SESSION="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Cookie SESSION ausente"));
		String value = header.substring("SESSION=".length(), header.indexOf(';'));
		return new Cookie("SESSION", value);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Endpoints {

		@Bean
		WhoAmIController whoAmIController() {
			return new WhoAmIController();
		}

	}

	@RestController
	static class WhoAmIController {

		@GetMapping("/teste/eu")
		String eu(Authentication authentication) {
			return authentication.getName();
		}

	}

}
