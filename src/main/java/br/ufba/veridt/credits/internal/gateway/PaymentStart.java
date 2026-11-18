package br.ufba.veridt.credits.internal.gateway;

import java.math.BigDecimal;
import java.util.UUID;

import br.ufba.veridt.credits.internal.domain.PaymentMethod;

/** O que o provedor precisa para cobrar. Sem dados pessoais: o provedor real os receberá quando o CRE-04 exigir. */
public record PaymentStart(UUID purchaseId, PaymentMethod method, BigDecimal amount, String description) {
}
