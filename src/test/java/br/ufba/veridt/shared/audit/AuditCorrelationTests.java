package br.ufba.veridt.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.TestcontainersConfiguration;

/** Origem e correlação pela cadeia real de filtros, com o proxy do frontend simulado em 10.9.9.9. */
@SpringBootTest(properties = "veridt.audit.trusted-proxies=10.9.9.9")
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import({ TestcontainersConfiguration.class, AuditCorrelationTests.RotaDeTeste.class })
class AuditCorrelationTests {

	enum TestOperation implements AuditOperation {
		TESTE_CORRELACAO
	}

	/** Grava um sucesso e, na mesma transação, uma falha com {@code REQUIRES_NEW}. */
	@RestController
	static class RotaDeTeste {

		private final AuditLog auditLog;
		private final TransactionTemplate transaction;

		RotaDeTeste(AuditLog auditLog, PlatformTransactionManager transactionManager) {
			this.auditLog = auditLog;
			this.transaction = new TransactionTemplate(transactionManager);
		}

		@GetMapping("/teste/auditoria")
		void auditar(Authentication authentication) {
			UUID usuario = UUID.fromString(authentication.getName());
			transaction.executeWithoutResult(status -> {
				auditLog.recordSuccess(TestOperation.TESTE_CORRELACAO, usuario);
				auditLog.recordFailure(TestOperation.TESTE_CORRELACAO, usuario, "FALHA_TESTE");
			});
		}

	}

	private final MockMvc mvc;
	private final JdbcTemplate jdbc;

	AuditCorrelationTests(MockMvc mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
	}

	@Test
	void sucessoEFalhaComRequiresNewGuardamOMesmoIdDaRequisicao() throws Exception {
		UUID usuario = UUID.randomUUID();
		UUID requestId = UUID.randomUUID();

		mvc.perform(get("/teste/auditoria").with(user(usuario.toString()))
			.header(RequestIdFilter.HEADER, requestId.toString())
			.header("User-Agent", "Navegador/1.0"))
			.andExpect(status().isOk())
			.andExpect(header().string(RequestIdFilter.HEADER, requestId.toString()));

		List<Map<String, Object>> linhas = linhas(usuario);
		assertThat(linhas).extracting(linha -> linha.get("resultado")).containsExactlyInAnyOrder("SUCESSO", "FALHA");
		assertThat(linhas).allSatisfy(linha -> {
			assertThat(linha.get("requisicao_id")).isEqualTo(requestId);
			assertThat(linha.get("agente_usuario")).isEqualTo("Navegador/1.0");
			assertThat(linha.get("ip")).isEqualTo("127.0.0.1");
		});
	}

	@Test
	void xRequestIdInvalidoESubstituidoNaRespostaENaAuditoria() throws Exception {
		UUID usuario = UUID.randomUUID();

		String devolvido = mvc.perform(get("/teste/auditoria").with(user(usuario.toString()))
			.header(RequestIdFilter.HEADER, "id-forjado"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getHeader(RequestIdFilter.HEADER);

		assertThat(devolvido).isNotEqualTo("id-forjado");
		assertThat(linhas(usuario)).hasSize(2)
			.allSatisfy(linha -> assertThat(linha.get("requisicao_id")).isEqualTo(UUID.fromString(devolvido)));
	}

	@Test
	void ipForjadoPorClienteNaoConfiavelEIgnorado() throws Exception {
		UUID usuario = UUID.randomUUID();

		mvc.perform(get("/teste/auditoria").with(user(usuario.toString())).with(request -> {
			request.setRemoteAddr("203.0.113.5");
			request.addHeader("X-Forwarded-For", "1.2.3.4");
			return request;
		})).andExpect(status().isOk());

		assertThat(linhas(usuario)).hasSize(2).allSatisfy(linha -> assertThat(linha.get("ip")).isEqualTo("203.0.113.5"));
	}

	@Test
	void proxyConfiavelInformaOIpRealDoCliente() throws Exception {
		UUID usuario = UUID.randomUUID();

		mvc.perform(get("/teste/auditoria").with(user(usuario.toString())).with(request -> {
			request.setRemoteAddr("10.9.9.9");
			request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.7");
			return request;
		})).andExpect(status().isOk());

		assertThat(linhas(usuario)).hasSize(2)
			.allSatisfy(linha -> assertThat(linha.get("ip")).isEqualTo("198.51.100.7"));
	}

	@Test
	void respostaSemLoginTambemTrazOId() throws Exception {
		mvc.perform(get("/teste/auditoria")).andExpect(status().isUnauthorized())
			.andExpect(header().exists(RequestIdFilter.HEADER));
	}

	private List<Map<String, Object>> linhas(UUID usuario) {
		return jdbc.queryForList(
				"SELECT resultado, requisicao_id, agente_usuario, host(ip_origem) AS ip FROM auditoria WHERE usuario_id = ?",
				usuario);
	}

}
