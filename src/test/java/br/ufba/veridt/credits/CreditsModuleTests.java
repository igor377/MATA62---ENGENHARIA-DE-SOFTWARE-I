package br.ufba.veridt.credits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.event.TransactionalEventListener;

import br.ufba.veridt.TestcontainersConfiguration;
import br.ufba.veridt.credits.internal.domain.PaymentMethod;
import br.ufba.veridt.credits.internal.domain.PurchaseStatus;
import br.ufba.veridt.credits.internal.dto.CreditPackageResponse;
import br.ufba.veridt.credits.internal.dto.PaymentResponse;
import br.ufba.veridt.credits.internal.exception.PackageUnavailableException;
import br.ufba.veridt.credits.internal.exception.PurchaseNotFoundException;
import br.ufba.veridt.credits.internal.service.PackageService;
import br.ufba.veridt.credits.internal.service.PaymentConfirmationService;
import br.ufba.veridt.credits.internal.service.PurchaseResult;
import br.ufba.veridt.credits.internal.service.PurchaseService;
import br.ufba.veridt.credits.internal.web.PaymentSimulationController;
import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.EmailType;
import br.ufba.veridt.shared.audit.AuditLog;
import br.ufba.veridt.shared.audit.AuditRepository;

@ApplicationModuleTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import({ TestcontainersConfiguration.class, CreditsModuleTests.TestBeans.class, AuditLog.class,
		AuditRepository.class })
class CreditsModuleTests {

	private final CreditLedger ledger;
	private final PackageService packages;
	private final PurchaseService purchases;
	private final PaymentConfirmationService confirmation;
	private final JdbcTemplate jdbc;
	private final EmailRecorder emails;
	private final ObjectProvider<PaymentSimulationController> simulation;

	private UUID ana;
	private OffsetDateTime inicio;

	CreditsModuleTests(CreditLedger ledger, PackageService packages, PurchaseService purchases,
			PaymentConfirmationService confirmation, JdbcTemplate jdbc, EmailRecorder emails,
			ObjectProvider<PaymentSimulationController> simulation) {
		this.ledger = ledger;
		this.packages = packages;
		this.purchases = purchases;
		this.confirmation = confirmation;
		this.jdbc = jdbc;
		this.emails = emails;
		this.simulation = simulation;
	}

	@BeforeEach
	void preparar() {
		emails.clear();
		jdbc.update("DELETE FROM event_publication");
		jdbc.update("DELETE FROM pedido_credito");
		jdbc.update("DELETE FROM saldo_credito");
		// As movimentações não podem ser apagadas: cada teste usa um usuário novo.
		ana = UUID.randomUUID();
		inicio = OffsetDateTime.now(ZoneOffset.UTC);
	}

	// CRE-01: pacotes

	@Test
	void listaOsTresPacotesComOTotalCalculado() {
		List<CreditPackageResponse> lista = packages.listActive();

		assertThat(lista).extracting(CreditPackageResponse::id).containsExactly("BASICO", "MEDIO", "PREMIUM");
		assertThat(lista).extracting(CreditPackageResponse::credits).containsExactly(10, 50, 100);
		assertThat(lista).extracting(CreditPackageResponse::price)
			.usingElementComparator(BigDecimal::compareTo)
			.containsExactly(new BigDecimal("50.00"), new BigDecimal("225.00"), new BigDecimal("400.00"));
	}

	// CRE-02: saldo e movimentações

	@Test
	void saldoComecaEmZeroEAcompanhaCreditosEDebitos() {
		assertThat(ledger.balance(ana)).isZero();

		ledger.credit(ana, 10, CreditOrigin.COMPRA, null, chave());
		CreditMovement debito = ledger.debit(ana, 3, CreditOrigin.CAPTURA, null, chave());

		assertThat(debito.balanceAfter()).isEqualTo(7);
		assertThat(ledger.balance(ana)).isEqualTo(7);
	}

	@Test
	void debitoSemSaldoSuficienteERecusadoSemAlterarNada() {
		ledger.credit(ana, 2, CreditOrigin.COMPRA, null, chave());

		assertThatThrownBy(() -> ledger.debit(ana, 3, CreditOrigin.CAPTURA, null, chave()))
			.isInstanceOf(InsufficientCreditsException.class);
		assertThatThrownBy(() -> ledger.debit(UUID.randomUUID(), 1, CreditOrigin.CAPTURA, null, chave()))
			.isInstanceOf(InsufficientCreditsException.class);

		assertThat(ledger.balance(ana)).isEqualTo(2);
		assertThat(movimentacoes(ana)).isEqualTo(1);
	}

	@Test
	void debitosSimultaneosNuncaDeixamOSaldoNegativo() throws Exception {
		ledger.credit(ana, 5, CreditOrigin.COMPRA, null, chave());
		int tentativas = 20;
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<Boolean>> resultados = new ArrayList<>();
		ExecutorService pool = Executors.newFixedThreadPool(tentativas);
		try {
			for (int i = 0; i < tentativas; i++) {
				resultados.add(pool.submit(() -> {
					largada.await();
					try {
						ledger.debit(ana, 1, CreditOrigin.CAPTURA, null, chave());
						return true;
					}
					catch (InsufficientCreditsException ex) {
						return false;
					}
				}));
			}
			largada.countDown();
			int sucessos = 0;
			for (Future<Boolean> resultado : resultados) {
				sucessos += resultado.get(30, TimeUnit.SECONDS) ? 1 : 0;
			}

			assertThat(sucessos).isEqualTo(5);
			assertThat(ledger.balance(ana)).isZero();
			assertThat(somaDasMovimentacoes(ana)).isEqualTo(ledger.balance(ana));
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void repetirAChaveNaoDuplicaAMovimentacao() {
		String chave = chave();

		CreditMovement primeira = ledger.credit(ana, 10, CreditOrigin.COMPRA, null, chave);
		CreditMovement segunda = ledger.credit(ana, 10, CreditOrigin.COMPRA, null, chave);

		assertThat(segunda).isEqualTo(primeira);
		assertThat(ledger.balance(ana)).isEqualTo(10);
		assertThat(movimentacoes(ana)).isEqualTo(1);
	}

	@Test
	void chaveReusadaEmOutraOperacaoGeraConflito() {
		String chave = chave();
		ledger.credit(ana, 10, CreditOrigin.COMPRA, null, chave);

		assertThatThrownBy(() -> ledger.credit(ana, 11, CreditOrigin.COMPRA, null, chave))
			.isInstanceOf(IdempotencyKeyConflictException.class);
		assertThatThrownBy(() -> ledger.debit(ana, 10, CreditOrigin.COMPRA, null, chave))
			.isInstanceOf(IdempotencyKeyConflictException.class);
		assertThat(ledger.balance(ana)).isEqualTo(10);
	}

	@Test
	void movimentacoesSaoSomenteDeInsercao() {
		ledger.credit(ana, 10, CreditOrigin.COMPRA, null, chave());

		assertThatThrownBy(() -> jdbc.update("UPDATE movimentacao_credito SET quantidade = 99 WHERE usuario_id = ?", ana))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM movimentacao_credito WHERE usuario_id = ?", ana))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void saldoNuncaFicaNegativoNemDiretoNoBanco() {
		ledger.credit(ana, 1, CreditOrigin.COMPRA, null, chave());

		assertThatThrownBy(() -> jdbc.update("UPDATE saldo_credito SET saldo = -1 WHERE usuario_id = ?", ana))
			.isInstanceOf(DataAccessException.class);
	}

	// CRE-03: pedido

	@Test
	void pedidoUsaOValorDoPacoteEGuardaUmaCopia() {
		PurchaseResult resultado = purchases.create(ana, chave(), CreditsTestData.compra("MEDIO", PaymentMethod.PIX));

		PaymentResponse pedido = resultado.payment();
		assertThat(resultado.created()).isTrue();
		assertThat(pedido.status()).isEqualTo(PurchaseStatus.PENDENTE);
		assertThat(pedido.amount()).isEqualByComparingTo("225.00");
		assertThat(pedido.credits()).isEqualTo(50);
		assertThat(pedido.pix()).isNotNull();
		assertThat(pedido.checkoutUrl()).isNull();

		jdbc.update("UPDATE pacote_credito SET valor_por_credito = 9.99, creditos = 1 WHERE codigo = 'MEDIO'");
		try {
			PaymentResponse depois = purchases.find(ana, pedido.id());
			assertThat(depois.amount()).isEqualByComparingTo("225.00");
			assertThat(depois.credits()).isEqualTo(50);
		}
		finally {
			jdbc.update("UPDATE pacote_credito SET valor_por_credito = 4.50, creditos = 50 WHERE codigo = 'MEDIO'");
		}
	}

	@Test
	void pedidoGuardaOFaturamentoNormalizado() {
		PaymentResponse pedido = purchases.create(ana, chave(), CreditsTestData.compra("BASICO", PaymentMethod.MERCADO_PAGO))
			.payment();

		var linha = jdbc.queryForMap("SELECT fat_email, fat_cpf, fat_telefone, fat_cep, fat_uf FROM pedido_credito WHERE id = ?",
				pedido.id());
		assertThat(linha).containsEntry("fat_email", "ana@exemplo.com")
			.containsEntry("fat_cpf", CreditsTestData.CPF)
			.containsEntry("fat_telefone", "71999998888")
			.containsEntry("fat_cep", "40170110")
			.containsEntry("fat_uf", "BA");
		assertThat(pedido.checkoutUrl()).endsWith("/creditos/pagamento/" + pedido.id());
	}

	@Test
	void mesmaChaveDevolveOMesmoPedido() {
		String chave = chave();

		PurchaseResult primeiro = purchases.create(ana, chave, CreditsTestData.compra("BASICO", PaymentMethod.PIX));
		PurchaseResult segundo = purchases.create(ana, chave, CreditsTestData.compra("PREMIUM", PaymentMethod.PIX));

		assertThat(segundo.created()).isFalse();
		assertThat(segundo.payment().id()).isEqualTo(primeiro.payment().id());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_credito", Integer.class)).isOne();
	}

	@Test
	void pacoteInexistenteERecusado() {
		assertThatThrownBy(() -> purchases.create(ana, chave(), CreditsTestData.compra("DIAMANTE", PaymentMethod.PIX)))
			.isInstanceOf(PackageUnavailableException.class);
	}

	@Test
	void pedidoDeOutroUsuarioNaoEEncontrado() {
		PaymentResponse pedido = purchases.create(ana, chave(), CreditsTestData.compra("BASICO", PaymentMethod.PIX))
			.payment();

		assertThatThrownBy(() -> purchases.find(UUID.randomUUID(), pedido.id()))
			.isInstanceOf(PurchaseNotFoundException.class);
		assertThat(purchases.list(UUID.randomUUID(), 0, 20).items()).isEmpty();
		assertThat(purchases.list(ana, 0, 20).items()).extracting(PaymentResponse::id).containsExactly(pedido.id());
	}

	// Confirmação de pagamento

	@Test
	void aprovacaoConcedeOsCreditosEPedeOEmail() {
		PaymentResponse pedido = novoPedido("MEDIO");

		PaymentResponse aprovado = confirmation.confirm(pedido.id(), true);

		assertThat(aprovado.status()).isEqualTo(PurchaseStatus.APROVADO);
		assertThat(aprovado.decidedAt()).isNotNull();
		assertThat(ledger.balance(ana)).isEqualTo(50);
		assertThat(emails.received()).singleElement().satisfies(email -> {
			assertThat(email.recipient()).isEqualTo("ana@exemplo.com");
			assertThat(email.type()).isEqualTo(EmailType.COMPRA_CONFIRMADA);
			assertThat(email.parameters()).containsEntry("nome", "Ana Souza")
				.containsEntry("pacote", "Médio")
				.containsEntry("creditos", "50")
				.containsEntry("valor", "R$ 225,00");
		});
	}

	@Test
	void confirmacaoRepetidaNaoDuplicaCreditosNemEmail() {
		PaymentResponse pedido = novoPedido("BASICO");

		confirmation.confirm(pedido.id(), true);
		confirmation.confirm(pedido.id(), true);
		PaymentResponse depoisDeRecusar = confirmation.confirm(pedido.id(), false);

		assertThat(depoisDeRecusar.status()).isEqualTo(PurchaseStatus.APROVADO);
		assertThat(ledger.balance(ana)).isEqualTo(10);
		assertThat(movimentacoes(ana)).isEqualTo(1);
		assertThat(emails.received()).hasSize(1);
	}

	@Test
	void pagamentoRecusadoNaoConcedeCreditos() {
		PaymentResponse pedido = novoPedido("PREMIUM");

		PaymentResponse recusado = confirmation.confirm(pedido.id(), false);
		confirmation.confirm(pedido.id(), true);

		assertThat(recusado.status()).isEqualTo(PurchaseStatus.RECUSADO);
		assertThat(purchases.find(ana, pedido.id()).status()).isEqualTo(PurchaseStatus.RECUSADO);
		assertThat(ledger.balance(ana)).isZero();
		assertThat(emails.received()).isEmpty();
	}

	@Test
	void erroNoMeioDaConfirmacaoNaoDeixaEstadoParcial() {
		PaymentResponse pedido = novoPedido("BASICO");
		// Ocupa a chave do crédito deste pedido com outra quantidade: o crédito falha depois de o pedido ser aprovado.
		ledger.credit(ana, 1, CreditOrigin.COMPRA, pedido.id(), "pedido:" + pedido.id());

		assertThatThrownBy(() -> confirmation.confirm(pedido.id(), true))
			.isInstanceOf(IdempotencyKeyConflictException.class);

		assertThat(purchases.find(ana, pedido.id()).status()).isEqualTo(PurchaseStatus.PENDENTE);
		assertThat(ledger.balance(ana)).isEqualTo(1);
		assertThat(emails.received()).isEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class)).isZero();
		assertThat(auditoria("PAGAMENTO_APROVADO", "SUCESSO")).isZero();
	}

	@Test
	void confirmacaoDePedidoInexistenteFalhaEFicaNaAuditoria() {
		assertThatThrownBy(() -> confirmation.confirm(UUID.randomUUID(), true))
			.isInstanceOf(PurchaseNotFoundException.class);

		assertThat(auditoria("PAGAMENTO_APROVADO", "FALHA")).isOne();
	}

	@Test
	void simulacaoSoParaODonoEDesligadaPorPadrao() {
		PaymentResponse pedido = novoPedido("BASICO");

		assertThat(simulation.getIfAvailable()).isNull();
		assertThatThrownBy(() -> confirmation.simulate(UUID.randomUUID(), pedido.id(), true))
			.isInstanceOf(PurchaseNotFoundException.class);
		assertThat(ledger.balance(ana)).isZero();

		confirmation.simulate(ana, pedido.id(), true);
		assertThat(ledger.balance(ana)).isEqualTo(10);
	}

	// CRE-08: auditoria

	@Test
	void operacoesSaoAuditadasSemDadosPessoais() {
		PaymentResponse aprovado = novoPedido("BASICO");
		PaymentResponse recusado = novoPedido("MEDIO");
		confirmation.confirm(aprovado.id(), true);
		confirmation.confirm(recusado.id(), false);

		List<String> linhas = jdbc.queryForList("""
				SELECT operacao || ':' || resultado || ':' || recurso_id || ':' || COALESCE(detalhe, '')
				FROM auditoria WHERE usuario_id = ? AND ocorrido_em >= ? ORDER BY ocorrido_em
				""", String.class, ana, inicio);
		assertThat(linhas).containsExactlyInAnyOrder(
				"PEDIDO_CRIADO:SUCESSO:" + aprovado.id() + ":",
				"PEDIDO_CRIADO:SUCESSO:" + recusado.id() + ":",
				"PAGAMENTO_APROVADO:SUCESSO:" + aprovado.id() + ":",
				"CREDITOS_CONCEDIDOS:SUCESSO:" + aprovado.id() + ":",
				"PAGAMENTO_RECUSADO:SUCESSO:" + recusado.id() + ":");
	}

	private PaymentResponse novoPedido(String pacote) {
		return purchases.create(ana, chave(), CreditsTestData.compra(pacote, PaymentMethod.PIX)).payment();
	}

	private static String chave() {
		return CreditsTestData.chave();
	}

	private int movimentacoes(UUID usuario) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_credito WHERE usuario_id = ?", Integer.class,
				usuario);
	}

	private int somaDasMovimentacoes(UUID usuario) {
		return jdbc.queryForObject("""
				SELECT COALESCE(SUM(CASE WHEN tipo = 'CREDITO' THEN quantidade ELSE -quantidade END), 0)
				FROM movimentacao_credito WHERE usuario_id = ?
				""", Integer.class, usuario);
	}

	private int auditoria(String operacao, String resultado) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE operacao = ? AND resultado = ? AND ocorrido_em >= ?",
				Integer.class, operacao, resultado, inicio);
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
