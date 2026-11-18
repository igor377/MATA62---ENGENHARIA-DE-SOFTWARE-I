package br.ufba.veridt.credits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.json.JsonMapper;

import br.ufba.veridt.TestcontainersConfiguration;
import br.ufba.veridt.credits.internal.domain.PaymentMethod;
import br.ufba.veridt.notification.internal.sender.EmailSender;

/** Rotas de créditos pela cadeia real de filtros, com a simulação de pagamento ligada. */
@SpringBootTest(properties = "veridt.payment.simulation-enabled=true")
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(TestcontainersConfiguration.class)
class CreditsWebTests {

	private static final String COMPRAS = "/api/credits/purchases";

	/** A aprovação pede o e-mail de compra; aqui não há servidor SMTP. */
	@MockitoBean
	private EmailSender emailSender;

	private final MockMvc mvc;
	private final JsonMapper json;
	private final JdbcTemplate jdbc;

	private UUID ana;
	private UUID bruno;

	CreditsWebTests(MockMvc mvc, JsonMapper json, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.json = json;
		this.jdbc = jdbc;
	}

	@BeforeEach
	void preparar() {
		jdbc.update("DELETE FROM pedido_credito");
		ana = UUID.randomUUID();
		bruno = UUID.randomUUID();
	}

	@Test
	void rotasExigemLogin() throws Exception {
		mvc.perform(get("/api/credits/packages"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
		mvc.perform(get("/api/credits/balance")).andExpect(status().isUnauthorized());
		mvc.perform(post(COMPRAS).with(CreditsTestData.csrfDaSpa())
			.header("Idempotency-Key", CreditsTestData.chave())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(CreditsTestData.compra("BASICO", PaymentMethod.PIX))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void compraSemCsrfERecusada() throws Exception {
		mvc.perform(post(COMPRAS).with(user(ana.toString()))
			.header("Idempotency-Key", CreditsTestData.chave())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(CreditsTestData.compra("BASICO", PaymentMethod.PIX))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
	}

	@Test
	void listaOsPacotesESaldoComecaEmZero() throws Exception {
		mvc.perform(get("/api/credits/packages").with(user(ana.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("BASICO"))
			.andExpect(jsonPath("$[0].price").value(50.0))
			.andExpect(jsonPath("$[1].pricePerCredit").value(4.5))
			.andExpect(jsonPath("$[2].credits").value(100));
		mvc.perform(get("/api/credits/balance").with(user(ana.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.balance").value(0));
	}

	@Test
	void valorEnviadoPeloClienteEIgnorado() throws Exception {
		Map<String, Object> corpo = Map.of("packageId", "PREMIUM", "method", "MERCADO_PAGO", "amount", 1,
				"price", 1, "credits", 9999, "billing", CreditsTestData.faturamento());

		comprar(ana, CreditsTestData.chave(), corpo)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDENTE"))
			.andExpect(jsonPath("$.amount").value(400.0))
			.andExpect(jsonPath("$.credits").value(100))
			.andExpect(jsonPath("$.checkoutUrl").exists())
			.andExpect(jsonPath("$.billing").doesNotExist());
	}

	@Test
	void mesmaChaveDevolveOMesmoPedidoCom200() throws Exception {
		String chave = CreditsTestData.chave();
		String id = idDe(comprar(ana, chave, CreditsTestData.compra("BASICO", PaymentMethod.PIX))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.pix.copyPaste").exists()));

		comprar(ana, chave, CreditsTestData.compra("BASICO", PaymentMethod.PIX))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id));
	}

	@Test
	void compraSemChaveDeIdempotenciaRetorna400() throws Exception {
		mvc.perform(post(COMPRAS).with(user(ana.toString())).with(CreditsTestData.csrfDaSpa())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(CreditsTestData.compra("BASICO", PaymentMethod.PIX))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void faturamentoInvalidoRetorna400ComOsCampos() throws Exception {
		Map<String, Object> faturamento = Map.of("fullName", "", "email", "nao-e-email", "phone", "123", "cpf",
				"111.111.111-11", "cep", "4017", "street", "Rua", "number", "1", "neighborhood", "Centro", "city",
				"Salvador", "state", "XX");

		comprar(ana, CreditsTestData.chave(), Map.of("packageId", "BASICO", "method", "PIX", "billing", faturamento))
			.andExpect(status().isBadRequest());
		comprar(ana, CreditsTestData.chave(), Map.of("packageId", "BASICO", "method", "PIX", "billing",
				Map.of("fullName", "", "email", "nao-e-email", "phone", "123", "cpf", "111.111.111-11", "cep", "4017",
						"street", "Rua", "number", "1", "neighborhood", "Centro", "city", "Salvador", "state", "BA")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DADOS_INVALIDOS"))
			.andExpect(jsonPath("$.errors[*].field",
					hasItems("billing.fullName", "billing.email", "billing.phone", "billing.cpf", "billing.cep")));
	}

	@Test
	void pacoteIndisponivelRetorna422() throws Exception {
		comprar(ana, CreditsTestData.chave(), CreditsTestData.compra("DIAMANTE", PaymentMethod.PIX))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PACOTE_INDISPONIVEL"));
	}

	@Test
	void usuarioNaoVePedidoNemSaldoDeOutro() throws Exception {
		String id = idDe(comprar(ana, CreditsTestData.chave(), CreditsTestData.compra("BASICO", PaymentMethod.PIX)));
		simular(ana, id, true).andExpect(status().isOk());

		mvc.perform(get("/api/credits/payments/" + id).with(user(bruno.toString())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAGAMENTO_NAO_ENCONTRADO"));
		mvc.perform(get("/api/credits/payments").with(user(bruno.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty());
		mvc.perform(get("/api/credits/balance").with(user(bruno.toString())))
			.andExpect(jsonPath("$.balance").value(0));
		simular(bruno, id, false).andExpect(status().isNotFound());

		mvc.perform(get("/api/credits/payments").with(user(ana.toString())))
			.andExpect(jsonPath("$.items[0].id").value(id))
			.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/credits/balance").with(user(ana.toString())))
			.andExpect(jsonPath("$.balance").value(10));
	}

	@Test
	void simulacaoAprovaUmaVezSo() throws Exception {
		String id = idDe(comprar(ana, CreditsTestData.chave(), CreditsTestData.compra("MEDIO", PaymentMethod.PIX)));

		simular(ana, id, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADO"));
		simular(ana, id, true).andExpect(status().isOk());
		simular(ana, id, false).andExpect(jsonPath("$.status").value("APROVADO"));

		mvc.perform(get("/api/credits/balance").with(user(ana.toString())))
			.andExpect(jsonPath("$.balance").value(50));
		mvc.perform(get("/api/credits/payments/" + id).with(user(ana.toString())))
			.andExpect(jsonPath("$.status").value("APROVADO"))
			.andExpect(jsonPath("$.decidedAt").exists());
	}

	@Test
	void simulacaoSemCsrfERecusada() throws Exception {
		String id = idDe(comprar(ana, CreditsTestData.chave(), CreditsTestData.compra("BASICO", PaymentMethod.PIX)));

		mvc.perform(post("/api/credits/payments/" + id + "/simulation").with(user(ana.toString()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"approved\":true}"))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/credits/balance").with(user(ana.toString())))
			.andExpect(jsonPath("$.balance").value(0));
	}

	private ResultActions comprar(UUID usuario, String chave, Object corpo) throws Exception {
		MockHttpServletRequestBuilder request = post(COMPRAS).with(user(usuario.toString()))
			.with(CreditsTestData.csrfDaSpa())
			.header("Idempotency-Key", chave)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(corpo));
		return mvc.perform(request);
	}

	private ResultActions simular(UUID usuario, String id, boolean aprovado) throws Exception {
		return mvc.perform(post("/api/credits/payments/" + id + "/simulation").with(user(usuario.toString()))
			.with(CreditsTestData.csrfDaSpa())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("approved", aprovado))));
	}

	private String idDe(ResultActions resultado) throws Exception {
		String corpo = resultado.andReturn().getResponse().getContentAsString();
		String id = json.readTree(corpo).path("id").asString();
		assertThat(id).isNotBlank();
		return id;
	}

}
