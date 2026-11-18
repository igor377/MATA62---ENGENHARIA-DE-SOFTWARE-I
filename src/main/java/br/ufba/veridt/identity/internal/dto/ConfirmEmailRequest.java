package br.ufba.veridt.identity.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailRequest(@NotBlank @Size(max = 100) String token) {

	/** O token nunca pode aparecer em logs. */
	@Override
	public String toString() {
		return "ConfirmEmailRequest[]";
	}

}
