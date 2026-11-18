package br.ufba.veridt.credits.internal.gateway;

import java.time.Instant;

/**
 * Cobrança iniciada no provedor. {@code checkoutUrl} vem no Mercado Pago; {@code pix}, no Pix.
 *
 * @param externalId identificador do pagamento no provedor, usado pelo webhook para achar o pedido
 */
public record PaymentSession(String externalId, String checkoutUrl, PixCharge pix) {

	/** @param qrCodeImage imagem do QR Code como data URL */
	public record PixCharge(String qrCodeImage, String copyPaste, Instant expiresAt) {
	}

}
