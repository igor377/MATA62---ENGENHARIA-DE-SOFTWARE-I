package br.ufba.veridt.notification;

import java.util.List;
import java.util.Locale;

/** Tipos de e-mail, com assunto fixo e os parâmetros que o modelo exige. */
public enum EmailType {

	RECUPERACAO_SENHA("Recuperação de senha", List.of("nome", "link", "validadeMinutos"), true),
	COMPRA_CONFIRMADA("Compra de créditos confirmada", List.of("nome", "pacote", "creditos", "valor"), false),
	GRAVACAO_CONCLUIDA("Gravação concluída", List.of("titulo", "url", "registroId", "concluidoEm"), false),
	CAPTURA_CONCLUIDA("Captura de tela concluída", List.of("titulo", "url", "registroId", "concluidoEm"), false);

	private final String subject;
	private final List<String> requiredParameters;
	private final boolean sensitive;

	EmailType(String subject, List<String> requiredParameters, boolean sensitive) {
		this.subject = subject;
		this.requiredParameters = requiredParameters;
		this.sensitive = sensitive;
	}

	/** Carrega segredo, como um token, nos parâmetros: o evento é apagado do banco logo após o envio. */
	public boolean sensitive() {
		return sensitive;
	}

	public String subject() {
		return subject;
	}

	public List<String> requiredParameters() {
		return requiredParameters;
	}

	/** Nome do arquivo de modelo em {@code templates/email}, sem extensão. */
	public String templateName() {
		return name().toLowerCase(Locale.ROOT);
	}

}
