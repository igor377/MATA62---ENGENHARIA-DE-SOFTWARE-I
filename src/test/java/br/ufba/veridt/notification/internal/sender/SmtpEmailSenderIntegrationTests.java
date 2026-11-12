package br.ufba.veridt.notification.internal.sender;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;

import br.ufba.veridt.notification.internal.config.NotificationProperties;
import br.ufba.veridt.notification.internal.template.RenderedEmail;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Envia de verdade para um Mailpit em contêiner e confere a mensagem pela API dele. */
class SmtpEmailSenderIntegrationTests {

	private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.33.0")
		.withExposedPorts(1025, 8025);

	private static SmtpEmailSender sender;

	@BeforeAll
	static void iniciarMailpit() {
		MAILPIT.start();
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost(MAILPIT.getHost());
		mailSender.setPort(MAILPIT.getMappedPort(1025));
		sender = new SmtpEmailSender(mailSender, new NotificationProperties("no-reply@veridt.local", 1));
	}

	@AfterAll
	static void pararMailpit() {
		MAILPIT.stop();
	}

	@Test
	void entregaEmailHtmlComRemetenteDestinatarioEAssunto() throws Exception {
		sender.send("ana@exemplo.com",
				new RenderedEmail("Captura de tela concluída", "<p>Registro <strong>concluído</strong></p>"));

		JsonNode message = latestMessage();
		assertThat(message.path("From").path("Address").asString()).isEqualTo("no-reply@veridt.local");
		assertThat(message.path("To").get(0).path("Address").asString()).isEqualTo("ana@exemplo.com");
		assertThat(message.path("Subject").asString()).isEqualTo("Captura de tela concluída");
		assertThat(message.path("HTML").asString()).contains("<strong>concluído</strong>");
	}

	private static JsonNode latestMessage() throws Exception {
		URI uri = URI.create("http://%s:%d/api/v1/message/latest".formatted(MAILPIT.getHost(),
				MAILPIT.getMappedPort(8025)));
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).isEqualTo(200);
			return JsonMapper.builder().build().readTree(response.body());
		}
	}

}
