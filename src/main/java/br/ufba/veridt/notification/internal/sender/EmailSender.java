package br.ufba.veridt.notification.internal.sender;

import br.ufba.veridt.notification.internal.template.RenderedEmail;

/** Entrega um e-mail já montado. Isola o provedor de envio do restante do módulo. */
public interface EmailSender {

	void send(String recipient, RenderedEmail email);

}
