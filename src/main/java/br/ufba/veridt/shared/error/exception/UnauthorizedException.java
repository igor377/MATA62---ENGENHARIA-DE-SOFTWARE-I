package br.ufba.veridt.shared.error.exception;

/** Autenticação recusada (401). */
public class UnauthorizedException extends BusinessException {

	public UnauthorizedException(String code, String message) {
		super(code, message);
	}

}
