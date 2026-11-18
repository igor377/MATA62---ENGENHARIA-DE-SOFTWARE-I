package br.ufba.veridt.identity.internal.exception;

import br.ufba.veridt.shared.error.exception.ForbiddenException;

/** Só é lançada depois de a senha ser conferida: quem não sabe a senha recebe o 401 genérico. */
public class EmailNotConfirmedException extends ForbiddenException {

	public EmailNotConfirmedException() {
		super("EMAIL_NAO_CONFIRMADO", "Confirme seu e-mail antes de entrar. Enviamos um link para o endereço cadastrado.");
	}

}
