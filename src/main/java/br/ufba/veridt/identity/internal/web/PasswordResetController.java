package br.ufba.veridt.identity.internal.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.identity.internal.dto.RequestPasswordResetRequest;
import br.ufba.veridt.identity.internal.dto.ResetPasswordRequest;
import br.ufba.veridt.identity.internal.service.PasswordResetService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

	private final PasswordResetService passwordResetService;

	public PasswordResetController(PasswordResetService passwordResetService) {
		this.passwordResetService = passwordResetService;
	}

	/** Sempre 202, com e-mail cadastrado ou não: a resposta não revela quem tem conta. */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void request(@Valid @RequestBody RequestPasswordResetRequest request) {
		passwordResetService.requestReset(request.email());
	}

	@PostMapping("/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirm(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request);
	}

}
