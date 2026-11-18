package br.ufba.veridt.credits.internal.service;

import br.ufba.veridt.credits.internal.dto.PaymentResponse;

/** @param created falso quando a chave de idempotência já existia e o pedido devolvido é o anterior */
public record PurchaseResult(PaymentResponse payment, boolean created) {
}
