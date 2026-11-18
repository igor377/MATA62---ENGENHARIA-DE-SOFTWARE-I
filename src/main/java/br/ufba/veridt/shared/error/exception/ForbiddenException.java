package br.ufba.veridt.shared.error.exception;

/** Usuário identificado, mas sem permissão para a operação (403). */
public class ForbiddenException extends BusinessException {

	public ForbiddenException(String code, String message) {
		super(code, message);
	}

}
