package br.ufba.veridt.credits.internal.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import br.ufba.veridt.credits.internal.audit.CreditsAuditOperation;
import br.ufba.veridt.credits.internal.domain.CreditPackage;
import br.ufba.veridt.credits.internal.domain.Purchase;
import br.ufba.veridt.credits.internal.dto.PaymentPageResponse;
import br.ufba.veridt.credits.internal.dto.PaymentResponse;
import br.ufba.veridt.credits.internal.dto.PurchaseRequest;
import br.ufba.veridt.credits.internal.exception.PackageUnavailableException;
import br.ufba.veridt.credits.internal.exception.PurchaseNotFoundException;
import br.ufba.veridt.credits.internal.gateway.PaymentGateway;
import br.ufba.veridt.credits.internal.gateway.PaymentSession;
import br.ufba.veridt.credits.internal.gateway.PaymentStart;
import br.ufba.veridt.credits.internal.mapper.CreditsMapper;
import br.ufba.veridt.credits.internal.repository.CreditPackageRepository;
import br.ufba.veridt.credits.internal.repository.PurchaseRepository;
import br.ufba.veridt.shared.audit.AuditLog;
import jakarta.validation.Valid;

/**
 * Pedidos de compra (CRE-03). O valor vem do pacote, no servidor. A mesma chave de idempotência do mesmo usuário
 * devolve o pedido já criado, inclusive quando duas requisições chegam juntas.
 */
@Service
@Validated
public class PurchaseService {

	private final CreditPackageRepository packages;
	private final PurchaseRepository purchases;
	private final PaymentGateway gateway;
	private final CreditsMapper mapper;
	private final AuditLog auditLog;
	private final TransactionTemplate transaction;

	public PurchaseService(CreditPackageRepository packages, PurchaseRepository purchases, PaymentGateway gateway,
			CreditsMapper mapper, AuditLog auditLog, PlatformTransactionManager transactionManager) {
		this.packages = packages;
		this.purchases = purchases;
		this.gateway = gateway;
		this.mapper = mapper;
		this.auditLog = auditLog;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public PurchaseResult create(UUID userId, String idempotencyKey, @Valid PurchaseRequest request) {
		Optional<PurchaseResult> repeated = existing(userId, idempotencyKey);
		if (repeated.isPresent()) {
			return repeated.get();
		}
		try {
			return new PurchaseResult(transaction.execute(status -> newPurchase(userId, idempotencyKey, request)),
					true);
		}
		catch (DataIntegrityViolationException ex) {
			// Outra requisição com a mesma chave criou o pedido entre a consulta e a gravação.
			return existing(userId, idempotencyKey).orElseThrow(() -> ex);
		}
	}

	@Transactional(readOnly = true)
	public PaymentResponse find(UUID userId, UUID purchaseId) {
		return purchases.findByIdAndUserId(purchaseId, userId)
			.map(mapper::toResponse)
			.orElseThrow(PurchaseNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public PaymentPageResponse list(UUID userId, int page, int size) {
		return mapper.toPage(purchases.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)));
	}

	private Optional<PurchaseResult> existing(UUID userId, String idempotencyKey) {
		return purchases.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
			.map(purchase -> new PurchaseResult(mapper.toResponse(purchase), false));
	}

	/** TODO CRE-04: com o provedor real, chamar o gateway depois do commit e com timeout. */
	private PaymentResponse newPurchase(UUID userId, String idempotencyKey, PurchaseRequest request) {
		CreditPackage creditPackage = packages.findByCodeAndActiveTrue(request.packageId())
			.orElseThrow(PackageUnavailableException::new);
		Purchase purchase = new Purchase(userId, idempotencyKey, creditPackage, request.method(),
				mapper.toBilling(request.billing()), Instant.now().truncatedTo(ChronoUnit.MICROS));
		purchases.saveAndFlush(purchase);

		PaymentSession session = gateway.start(new PaymentStart(purchase.getId(), purchase.getMethod(),
				purchase.getTotalPrice(), "Veridt - pacote " + purchase.getPackageName()));
		PaymentSession.PixCharge pix = session.pix();
		purchase.attachPayment(session.externalId(), session.checkoutUrl(), pix == null ? null : pix.copyPaste(),
				pix == null ? null : pix.qrCodeImage(), pix == null ? null : pix.expiresAt());

		auditLog.recordSuccess(CreditsAuditOperation.PEDIDO_CRIADO, userId, purchase.getId());
		return mapper.toResponse(purchase);
	}

}
