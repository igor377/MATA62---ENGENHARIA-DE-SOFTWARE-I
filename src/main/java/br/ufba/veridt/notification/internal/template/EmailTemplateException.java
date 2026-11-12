package br.ufba.veridt.notification.internal.template;

/** Modelo ausente ou evento sem os parâmetros que o modelo exige. */
public class EmailTemplateException extends RuntimeException {

	public EmailTemplateException(String message) {
		super(message);
	}

	public EmailTemplateException(String message, Throwable cause) {
		super(message, cause);
	}

}
