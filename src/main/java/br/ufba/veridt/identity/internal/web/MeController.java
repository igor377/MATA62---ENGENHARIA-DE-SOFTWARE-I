package br.ufba.veridt.identity.internal.web;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.identity.internal.dto.UserResponse;
import br.ufba.veridt.identity.internal.service.CurrentUserService;

/** Usuário da sessão atual. Sem sessão, a cadeia de segurança responde 401 antes de chegar aqui. */
@RestController
@RequestMapping("/api/me")
public class MeController {

	private final CurrentUserService currentUser;

	public MeController(CurrentUserService currentUser) {
		this.currentUser = currentUser;
	}

	@GetMapping
	public UserResponse me(Authentication authentication) {
		return currentUser.get(UUID.fromString(authentication.getName()));
	}

}
