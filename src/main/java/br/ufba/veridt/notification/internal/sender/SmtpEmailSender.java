package br.ufba.veridt.notification.internal.sender;

import java.nio.charset.StandardCharsets;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import br.ufba.veridt.notification.internal.config.NotificationProperties;
import br.ufba.veridt.notification.internal.template.RenderedEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final NotificationProperties properties;

	public SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public void send(String recipient, RenderedEmail email) {
		MimeMessage message = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(properties.from());
			helper.setTo(recipient);
			helper.setSubject(email.subject());
			helper.setText(email.htmlBody(), true);
		}
		catch (MessagingException ex) {
			throw failure("montar", ex);
		}
		try {
			mailSender.send(message);
		}
		catch (MailException ex) {
			throw failure("enviar", ex);
		}
	}

	/**
	 * As mensagens do SMTP costumam trazer o endereço recusado, e falhas no listener são registradas em log. Por isso a
	 * exceção original é descartada e só o tipo dela é mantido.
	 */
	private static EmailDeliveryException failure(String step, Exception ex) {
		return new EmailDeliveryException(
				"Não foi possível " + step + " o e-mail (" + ex.getClass().getSimpleName() + ")");
	}

}
