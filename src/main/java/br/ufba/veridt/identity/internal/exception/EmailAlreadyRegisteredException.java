package br.ufba.veridt.identity.internal.exception;

import br.ufba.veridt.shared.error.exception.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

	public EmailAlreadyRegisteredException() {
		super("EMAIL_JA_CADASTRADO", "E-mail já cadastrado.");
	}

}
