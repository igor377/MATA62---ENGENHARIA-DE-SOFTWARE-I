package br.ufba.veridt.notification.internal.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import br.ufba.veridt.notification.EmailType;

/**
 * Monta o e-mail a partir de {@code templates/email/<tipo>.html}, trocando cada {@code {parametro}} pelo valor
 * escapado. Os modelos são carregados na inicialização, para que um arquivo ausente impeça a aplicação de subir.
 */
@Component
public class EmailTemplateRenderer {

	private static final String TEMPLATE_LOCATION = "templates/email/%s.html";
	private static final Charset CHARSET = StandardCharsets.UTF_8;
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

	private final Map<EmailType, String> templates = new EnumMap<>(EmailType.class);

	public EmailTemplateRenderer() {
		for (EmailType type : EmailType.values()) {
			templates.put(type, load(type));
		}
	}

	public RenderedEmail render(EmailType type, Map<String, String> parameters) {
		List<String> missing = type.requiredParameters().stream()
			.filter(name -> parameters.get(name) == null || parameters.get(name).isBlank())
			.toList();
		if (!missing.isEmpty()) {
			throw new EmailTemplateException("E-mail " + type + " sem os parâmetros " + missing);
		}
		// Uma única passada: um valor que contenha outro marcador, como "{link}", não é substituído de novo.
		String body = PLACEHOLDER.matcher(templates.get(type)).replaceAll(match -> {
			String name = match.group(1);
			if (!type.requiredParameters().contains(name)) {
				return Matcher.quoteReplacement(match.group());
			}
			// Com o charset, escapa só os caracteres especiais do HTML e preserva os acentos.
			return Matcher.quoteReplacement(HtmlUtils.htmlEscape(parameters.get(name), CHARSET.name()));
		});
		return new RenderedEmail(type.subject(), body);
	}

	private static String load(EmailType type) {
		ClassPathResource resource = new ClassPathResource(TEMPLATE_LOCATION.formatted(type.templateName()));
		try (InputStream input = resource.getInputStream()) {
			return new String(input.readAllBytes(), CHARSET);
		}
		catch (IOException ex) {
			throw new EmailTemplateException("Modelo de e-mail não encontrado: " + resource.getPath(), ex);
		}
	}

}
