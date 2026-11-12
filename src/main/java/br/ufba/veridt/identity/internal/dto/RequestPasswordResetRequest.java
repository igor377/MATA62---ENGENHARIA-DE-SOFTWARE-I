package br.ufba.veridt.identity.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestPasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {

	/** O e-mail do usuário não pode aparecer em logs. */
	@Override
	public String toString() {
		return "RequestPasswordResetRequest[]";
	}

}
