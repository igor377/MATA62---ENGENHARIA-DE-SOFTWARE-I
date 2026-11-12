package br.ufba.veridt.notification.internal.sender;

/** Falha ao montar ou enviar o e-mail. A mensagem nunca contém destinatário nem conteúdo. */
public class EmailDeliveryException extends RuntimeException {

	public EmailDeliveryException(String message) {
		super(message);
	}

}
