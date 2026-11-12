package br.ufba.veridt.identity.internal.dto;

import br.ufba.veridt.identity.internal.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResetPasswordRequest(@NotBlank String token, @NotNull @StrongPassword String newPassword) {

	/** O token e a senha nunca podem aparecer em logs. */
	@Override
	public String toString() {
		return "ResetPasswordRequest[]";
	}

}
