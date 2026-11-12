package br.ufba.veridt.notification.internal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.internal.sender.EmailSender;
import br.ufba.veridt.notification.internal.template.EmailTemplateRenderer;
import br.ufba.veridt.notification.internal.template.RenderedEmail;
import jakarta.validation.Valid;

/** Valida o pedido, monta o e-mail e o entrega. O log registra só o tipo, nunca destinatário ou parâmetros. */
@Service
@Validated
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private final EmailTemplateRenderer renderer;
	private final EmailSender sender;

	public EmailService(EmailTemplateRenderer renderer, EmailSender sender) {
		this.renderer = renderer;
		this.sender = sender;
	}

	public void send(@Valid EmailRequested request) {
		RenderedEmail email = renderer.render(request.type(), request.parameters());
		sender.send(request.recipient(), email);
		log.info("E-mail {} enviado", request.type());
	}

}
