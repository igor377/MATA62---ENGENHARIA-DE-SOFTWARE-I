package br.ufba.veridt.credits.internal.dto;

import br.ufba.veridt.credits.internal.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Não tem campo de valor: o total vem sempre do pacote, no servidor. */
public record PurchaseRequest(
		@NotBlank @Pattern(regexp = "[A-Z][A-Z_]{0,19}", message = "Pacote inválido") String packageId,
		@NotNull PaymentMethod method,
		@NotNull @Valid BillingInfoRequest billing) {

	@Override
	public String toString() {
		return "PurchaseRequest[packageId=" + packageId + ", method=" + method + "]";
	}

}
