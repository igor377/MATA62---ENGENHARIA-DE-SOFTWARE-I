package br.ufba.veridt.shared.error.exception;

/** Violação de regra de negócio. Os módulos estendem esta classe com exceções específicas. */
public class BusinessException extends RuntimeException {

	private final String code;

	public BusinessException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

}
