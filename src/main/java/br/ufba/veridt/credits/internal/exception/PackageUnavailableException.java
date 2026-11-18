package br.ufba.veridt.credits.internal.exception;

import br.ufba.veridt.shared.error.exception.BusinessException;

/** Código de pacote inexistente ou desativado. */
public class PackageUnavailableException extends BusinessException {

	public PackageUnavailableException() {
		super("PACOTE_INDISPONIVEL", "Pacote de créditos indisponível.");
	}

}
