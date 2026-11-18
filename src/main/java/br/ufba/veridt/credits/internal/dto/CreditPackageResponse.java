package br.ufba.veridt.credits.internal.dto;

import java.math.BigDecimal;

/** {@code id} é o código do pacote (BASICO, MEDIO, PREMIUM); {@code price} é o total, calculado no servidor. */
public record CreditPackageResponse(String id, String name, int credits, BigDecimal pricePerCredit, BigDecimal price,
		String description) {
}
