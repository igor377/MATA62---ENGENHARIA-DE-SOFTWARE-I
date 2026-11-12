package br.ufba.veridt.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import br.ufba.veridt.notification.internal.sender.EmailSender;
import br.ufba.veridt.notification.internal.template.RenderedEmail;

/** Implementação falsa de {@link EmailSender} para testes: guarda os envios e pode simular falha. */
class RecordingEmailSender implements EmailSender {

	record SentEmail(String recipient, RenderedEmail email, String thread) {
	}

	private final List<SentEmail> sent = new CopyOnWriteArrayList<>();
	private volatile boolean failing;

	@Override
	public void send(String recipient, RenderedEmail email) {
		if (failing) {
			throw new IllegalStateException("Falha simulada no servidor de e-mail");
		}
		sent.add(new SentEmail(recipient, email, Thread.currentThread().getName()));
	}

	List<SentEmail> sent() {
		return List.copyOf(sent);
	}

	void failing(boolean failing) {
		this.failing = failing;
	}

	void reset() {
		sent.clear();
		failing = false;
	}

}
