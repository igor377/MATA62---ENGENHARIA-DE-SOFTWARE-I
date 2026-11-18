package br.ufba.veridt.credits.internal.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufba.veridt.credits.CreditLedger;
import br.ufba.veridt.credits.CreditOrigin;
import br.ufba.veridt.credits.internal.audit.CreditsAuditOperation;
import br.ufba.veridt.credits.internal.domain.Purchase;
import br.ufba.veridt.credits.internal.dto.PaymentResponse;
import br.ufba.veridt.credits.internal.exception.PurchaseNotFoundException;
import br.ufba.veridt.credits.internal.mapper.CreditsMapper;
import br.ufba.veridt.credits.internal.repository.PurchaseRepository;
import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.EmailType;
import br.ufba.veridt.shared.audit.AuditLog;

/**
 * Aplica o resultado de um pagamento. O webhook (CRE-05) e a reconciliação (CRE-06) chamarão {@link #confirm}; não há
 * rota pública que o exponha. Tudo em uma transação: o pedido é decidido, os créditos concedidos e o e-mail pedido
 * juntos, ou nada acontece. Confirmações repetidas não têm efeito.
 */
@Service
public class PaymentConfirmationService {

	private final PurchaseRepository purchases;
	private final CreditLedger ledger;
	private final ApplicationEventPublisher events;
	private final CreditsMapper mapper;
	private final AuditLog auditLog;

	public PaymentConfirmationService(PurchaseRepository purchases, CreditLedger ledger,
			ApplicationEventPublisher events, CreditsMapper mapper, AuditLog auditLog) {
		this.purchases = purchases;
		this.ledger = ledger;
		this.events = events;
		this.mapper = mapper;
		this.auditLog = auditLog;
	}

	/**
	 * @return o pedido depois da confirmação
	 * @throws PurchaseNotFoundException se o pedido não existe
	 */
	@Transactional
	public PaymentResponse confirm(UUID purchaseId, boolean approved) {
		Purchase purchase = purchases.findForUpdate(purchaseId).orElseThrow(() -> {
			auditLog.recordFailure(operation(approved), null, "PEDIDO_INEXISTENTE");
			return new PurchaseNotFoundException();
		});
		if (purchase.isPending()) {
			apply(purchase, approved);
		}
		return mapper.toResponse(purchase);
	}

	/** Simulação de desenvolvimento: só o dono decide o próprio pedido. */
	@Transactional
	public PaymentResponse simulate(UUID userId, UUID purchaseId, boolean approved) {
		purchases.findByIdAndUserId(purchaseId, userId).orElseThrow(PurchaseNotFoundException::new);
		return confirm(purchaseId, approved);
	}

	private void apply(Purchase purchase, boolean approved) {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		if (!approved) {
			purchase.reject(now);
			auditLog.recordSuccess(CreditsAuditOperation.PAGAMENTO_RECUSADO, purchase.getUserId(), purchase.getId());
			return;
		}
		purchase.approve(now);
		// A chave por pedido é uma segunda barreira: mesmo que o status falhasse, o crédito não se repetiria.
		ledger.credit(purchase.getUserId(), purchase.getCredits(), CreditOrigin.COMPRA, purchase.getId(),
				"pedido:" + purchase.getId());
		events.publishEvent(new EmailRequested(purchase.getBilling().getEmail(), EmailType.COMPRA_CONFIRMADA,
				Map.of("nome", purchase.getBilling().getFullName(), "pacote", purchase.getPackageName(), "creditos",
						String.valueOf(purchase.getCredits()), "valor", reais(purchase.getTotalPrice()))));
		auditLog.recordSuccess(CreditsAuditOperation.PAGAMENTO_APROVADO, purchase.getUserId(), purchase.getId());
		auditLog.recordSuccess(CreditsAuditOperation.CREDITOS_CONCEDIDOS, purchase.getUserId(), purchase.getId());
	}

	private static CreditsAuditOperation operation(boolean approved) {
		return approved ? CreditsAuditOperation.PAGAMENTO_APROVADO : CreditsAuditOperation.PAGAMENTO_RECUSADO;
	}

	/** "R$ 1.225,00". */
	static String reais(BigDecimal value) {
		DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
		return "R$ " + format.format(value);
	}

}
