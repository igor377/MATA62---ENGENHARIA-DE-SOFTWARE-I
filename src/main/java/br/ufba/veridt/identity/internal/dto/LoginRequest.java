package br.ufba.veridt.identity.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {

	/** O e-mail e a senha nunca podem aparecer em logs. */
	@Override
	public String toString() {
		return "LoginRequest[]";
	}

}
