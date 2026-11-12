package br.ufba.veridt.notification;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Pedido de envio de e-mail. Outros módulos publicam este evento dentro da própria transação; o envio ocorre de forma
 * assíncrona, só após o commit, e uma falha no envio não desfaz a operação de quem publicou.
 * <p>
 * A entrega é "pelo menos uma vez": se o envio funcionar e a marcação de concluído falhar, o e-mail é reenviado no
 * reinício. Os eventos pendentes ficam serializados no banco, então renomear uma constante de {@link EmailType} quebra
 * os que ainda não foram enviados.
 *
 * @param recipient endereço de destino
 * @param type tipo do e-mail, que define assunto, modelo e parâmetros obrigatórios
 * @param parameters valores usados no modelo, conforme {@link EmailType#requiredParameters()}
 */
public record EmailRequested(@NotBlank @Email String recipient, @NotNull EmailType type,
		@NotNull Map<String, String> parameters) {

	public EmailRequested {
		// Aceita valores nulos: um parâmetro ausente é rejeitado no envio, sem derrubar a transação de quem publicou.
		parameters = parameters == null ? null : Collections.unmodifiableMap(new HashMap<>(parameters));
	}

	/** Mostra só o tipo: destinatário e parâmetros, como links com token, não podem ir para logs. */
	@Override
	public String toString() {
		return "EmailRequested[type=" + type + "]";
	}

}
