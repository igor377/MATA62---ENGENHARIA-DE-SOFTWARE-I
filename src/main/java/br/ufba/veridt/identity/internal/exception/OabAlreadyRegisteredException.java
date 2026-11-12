package br.ufba.veridt.identity.internal.exception;

import br.ufba.veridt.shared.error.exception.ConflictException;

public class OabAlreadyRegisteredException extends ConflictException {

	public OabAlreadyRegisteredException() {
		super("OAB_JA_CADASTRADA", "Inscrição na OAB já cadastrada.");
	}

}
