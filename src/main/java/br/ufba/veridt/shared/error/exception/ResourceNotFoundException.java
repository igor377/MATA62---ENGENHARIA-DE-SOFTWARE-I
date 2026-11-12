package br.ufba.veridt.shared.error.exception;

public class ResourceNotFoundException extends BusinessException {

	public ResourceNotFoundException(String code, String message) {
		super(code, message);
	}

}
