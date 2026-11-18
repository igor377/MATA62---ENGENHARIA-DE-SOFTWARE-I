package br.ufba.veridt.credits.internal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Pedido de compra de um pacote (CRE-03). Guarda uma cópia do pacote, que não muda se o pacote mudar depois, e o valor
 * total calculado no servidor. Sai de {@code PENDENTE} uma única vez.
 */
@Entity
@Table(name = "pedido_credito")
public class Purchase {

	@Id
	private UUID id;

	@Column(name = "usuario_id", nullable = false)
	private UUID userId;

	@Column(name = "chave_idempotencia", nullable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "pacote_id", nullable = false)
	private UUID packageId;

	@Column(name = "pacote_codigo", nullable = false, length = 20)
	private String packageCode;

	@Column(name = "pacote_nome", nullable = false, length = 60)
	private String packageName;

	@Column(name = "creditos", nullable = false)
	private int credits;

	@Column(name = "valor_por_credito", nullable = false, precision = 10, scale = 2)
	private BigDecimal pricePerCredit;

	@Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalPrice;

	@Enumerated(EnumType.STRING)
	@Column(name = "metodo", nullable = false, length = 20)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PurchaseStatus status;

	@Column(name = "pagamento_externo_id", length = 100, unique = true)
	private String externalPaymentId;

	@Column(name = "checkout_url", length = 2048)
	private String checkoutUrl;

	@Column(name = "pix_copia_cola")
	private String pixCopyPaste;

	@Column(name = "pix_qr_code")
	private String pixQrCodeImage;

	@Column(name = "pix_expira_em")
	private Instant pixExpiresAt;

	@Embedded
	private BillingInfo billing;

	@Column(name = "criado_em", nullable = false)
	private Instant createdAt;

	@Column(name = "decidido_em")
	private Instant decidedAt;

	protected Purchase() {
	}

	public Purchase(UUID userId, String idempotencyKey, CreditPackage creditPackage, PaymentMethod method,
			BillingInfo billing, Instant now) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.idempotencyKey = idempotencyKey;
		this.packageId = creditPackage.getId();
		this.packageCode = creditPackage.getCode();
		this.packageName = creditPackage.getName();
		this.credits = creditPackage.getCredits();
		this.pricePerCredit = creditPackage.getPricePerCredit();
		this.totalPrice = creditPackage.totalPrice();
		this.method = method;
		this.status = PurchaseStatus.PENDENTE;
		this.billing = billing;
		this.createdAt = now;
	}

	public void attachPayment(String externalPaymentId, String checkoutUrl, String pixCopyPaste,
			String pixQrCodeImage, Instant pixExpiresAt) {
		this.externalPaymentId = externalPaymentId;
		this.checkoutUrl = checkoutUrl;
		this.pixCopyPaste = pixCopyPaste;
		this.pixQrCodeImage = pixQrCodeImage;
		this.pixExpiresAt = pixExpiresAt;
	}

	public boolean isPending() {
		return status == PurchaseStatus.PENDENTE;
	}

	public void approve(Instant now) {
		decide(PurchaseStatus.APROVADO, now);
	}

	public void reject(Instant now) {
		decide(PurchaseStatus.RECUSADO, now);
	}

	private void decide(PurchaseStatus decision, Instant now) {
		if (!isPending()) {
			throw new IllegalStateException("O pedido já foi decidido");
		}
		this.status = decision;
		this.decidedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getPackageCode() {
		return packageCode;
	}

	public String getPackageName() {
		return packageName;
	}

	public int getCredits() {
		return credits;
	}

	public BigDecimal getPricePerCredit() {
		return pricePerCredit;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public PaymentMethod getMethod() {
		return method;
	}

	public PurchaseStatus getStatus() {
		return status;
	}

	public String getExternalPaymentId() {
		return externalPaymentId;
	}

	public String getCheckoutUrl() {
		return checkoutUrl;
	}

	public String getPixCopyPaste() {
		return pixCopyPaste;
	}

	public String getPixQrCodeImage() {
		return pixQrCodeImage;
	}

	public Instant getPixExpiresAt() {
		return pixExpiresAt;
	}

	public BillingInfo getBilling() {
		return billing;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

}
