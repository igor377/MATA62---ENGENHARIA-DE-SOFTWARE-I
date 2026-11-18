package br.ufba.veridt.identity.internal.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.identity.internal.dto.ConfirmEmailRequest;
import br.ufba.veridt.identity.internal.dto.ResendConfirmationRequest;
import br.ufba.veridt.identity.internal.service.EmailConfirmationService;
import jakarta.validation.Valid;

/**
 * A confirmação é um {@code POST} feito pela página do frontend, e não um {@code GET} no link do e-mail: antivírus de
 * e-mail que abrem os links não confirmam a conta sozinhos.
 */
@RestController
@RequestMapping("/api/auth/email-confirmation")
public class EmailConfirmationController {

	private final EmailConfirmationService emailConfirmationService;

	public EmailConfirmationController(EmailConfirmationService emailConfirmationService) {
		this.emailConfirmationService = emailConfirmationService;
	}

	@PostMapping("/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirm(@Valid @RequestBody ConfirmEmailRequest request) {
		emailConfirmationService.confirm(request.token());
	}

	/** Sempre 202, com e-mail cadastrado, já confirmado ou não: a resposta não revela quem tem conta. */
	@PostMapping("/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void resend(@Valid @RequestBody ResendConfirmationRequest request) {
		emailConfirmationService.resend(request.email());
	}

}
