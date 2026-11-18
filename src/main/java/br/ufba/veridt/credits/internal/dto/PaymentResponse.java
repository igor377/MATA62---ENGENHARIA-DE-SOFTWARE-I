package br.ufba.veridt.credits.internal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.ufba.veridt.credits.internal.domain.PaymentMethod;
import br.ufba.veridt.credits.internal.domain.PurchaseStatus;

/**
 * Pedido de compra visto pelo dono. Não traz os dados de faturamento. {@code pix} só no Pix e {@code checkoutUrl} só
 * no Mercado Pago.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(UUID id, String packageId, String packageName, PaymentMethod method,
		PurchaseStatus status, BigDecimal amount, int credits, Instant createdAt, Instant decidedAt, PixResponse pix,
		String checkoutUrl) {
}
