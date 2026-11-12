package br.ufba.veridt.notification.internal.sender;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import br.ufba.veridt.notification.internal.config.NotificationProperties;
import br.ufba.veridt.notification.internal.template.RenderedEmail;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class SmtpEmailSenderTests {

	@Test
	void falhaDoSmtpNaoExpoeODestinatario() {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		doThrow(new MailSendException("550 Invalid Addresses: <ana@exemplo.com>")).when(mailSender)
			.send(any(MimeMessage.class));
		SmtpEmailSender sender = new SmtpEmailSender(mailSender, new NotificationProperties("no-reply@veridt.local", 1));

		assertThatThrownBy(() -> sender.send("ana@exemplo.com", new RenderedEmail("Assunto", "<p>corpo</p>")))
			.isInstanceOf(EmailDeliveryException.class)
			.hasMessageContaining("MailSendException")
			.hasMessageNotContaining("ana@exemplo.com")
			.hasNoCause();
	}

}
