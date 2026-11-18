package br.ufba.veridt.credits.internal.gateway;

/**
 * Provedor de pagamento (CRE-04). Só inicia a cobrança: a aprovação chega depois, pelo webhook (CRE-05), que chama o
 * {@code PaymentConfirmationService}. Nenhuma implementação aprova pedidos por conta própria.
 */
public interface PaymentGateway {

	PaymentSession start(PaymentStart request);

}
