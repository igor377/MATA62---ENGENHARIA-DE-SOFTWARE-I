package br.ufba.veridt.credits.internal.web;

import java.util.UUID;

import org.springframework.security.core.Authentication;

/** O nome do principal é o UUID do usuário: credits identifica o dono sem depender de identity. */
final class CurrentUser {

	private CurrentUser() {
	}

	static UUID id(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

}
