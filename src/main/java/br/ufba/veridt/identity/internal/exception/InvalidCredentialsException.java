package br.ufba.veridt.identity.internal.exception;

import br.ufba.veridt.shared.error.exception.UnauthorizedException;

/** Mesma resposta para e-mail inexistente e senha errada: não revela se o e-mail está cadastrado. */
public class InvalidCredentialsException extends UnauthorizedException {

	public InvalidCredentialsException() {
		super("CREDENCIAIS_INVALIDAS", "E-mail ou senha inválidos.");
	}

}
