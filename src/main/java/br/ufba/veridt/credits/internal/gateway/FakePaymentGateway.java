package br.ufba.veridt.credits.internal.gateway;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.ufba.veridt.credits.internal.config.PaymentProperties;
import br.ufba.veridt.credits.internal.domain.PaymentMethod;

/**
 * Provedor falso, para desenvolvimento e testes até o CRE-04: devolve dados de cobrança fictícios, sem chamada
 * externa, e nunca aprova nada. O pedido continua pendente até uma confirmação.
 */
@Component
@ConditionalOnProperty(name = "veridt.payment.provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGateway {

	/** PNG transparente de 1x1 pixel: só ocupa o lugar do QR Code. */
	private static final String PLACEHOLDER_QR_CODE = "data:image/png;base64,"
			+ "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

	private final PaymentProperties properties;

	public FakePaymentGateway(PaymentProperties properties) {
		this.properties = properties;
	}

	@Override
	public PaymentSession start(PaymentStart request) {
		String externalId = "fake-" + request.purchaseId();
		if (request.method() == PaymentMethod.PIX) {
			String copyPaste = "PIX-FICTICIO-VERIDT-" + request.purchaseId() + "-" + request.amount().toPlainString();
			Instant expiresAt = Instant.now().plus(properties.pixValidity());
			return new PaymentSession(externalId, null,
					new PaymentSession.PixCharge(PLACEHOLDER_QR_CODE, copyPaste, expiresAt));
		}
		return new PaymentSession(externalId, properties.fakeReturnUrl() + "/" + request.purchaseId(), null);
	}

}
