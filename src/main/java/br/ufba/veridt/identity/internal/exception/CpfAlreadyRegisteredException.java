package br.ufba.veridt.identity.internal.exception;

import br.ufba.veridt.shared.error.exception.ConflictException;

public class CpfAlreadyRegisteredException extends ConflictException {

	public CpfAlreadyRegisteredException() {
		super("CPF_JA_CADASTRADO", "CPF já cadastrado.");
	}

}
