package br.ufba.veridt.identity.internal.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.identity.internal.dto.LoginRequest;
import br.ufba.veridt.identity.internal.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationService authenticationService;

	public AuthController(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	/** Grava o cookie {@code XSRF-TOKEN}: a SPA devolve o valor dele no cabeçalho {@code X-XSRF-TOKEN}. */
	@GetMapping("/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void csrf(CsrfToken token) {
		token.getToken();
	}

	@PostMapping("/login")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void login(@Valid @RequestBody LoginRequest credentials, HttpServletRequest request,
			HttpServletResponse response) {
		authenticationService.login(credentials, request, response);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		authenticationService.logout(authentication, request, response);
	}

}
