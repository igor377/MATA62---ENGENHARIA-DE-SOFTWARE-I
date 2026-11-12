package br.ufba.veridt.notification.internal.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.internal.config.NotificationConfig;
import br.ufba.veridt.notification.internal.service.EmailService;

/**
 * Porta de entrada do módulo. Roda de forma assíncrona após o commit de quem publicou; se lançar exceção, a publicação
 * fica pendente no registro de eventos e é reenviada no próximo reinício.
 * <p>
 * Equivale a {@code @ApplicationModuleListener}, mas usa o executor exclusivo da notificação em vez do executor padrão.
 */
@Component
class EmailRequestedListener {

	private final EmailService emailService;

	EmailRequestedListener(EmailService emailService) {
		this.emailService = emailService;
	}

	@Async(NotificationConfig.EXECUTOR)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener
	void on(EmailRequested event) {
		emailService.send(event);
	}

}
