package br.ufba.veridt.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EmailRequestedTests {

	@Test
	void toStringNaoExpoeDestinatarioNemParametros() {
		EmailRequested event = new EmailRequested("ana@exemplo.com", EmailType.RECUPERACAO_SENHA,
				Map.of("nome", "Ana", "link", "https://veridt.local/redefinir?token=segredo", "validadeMinutos", "30"));

		assertThat(event.toString()).isEqualTo("EmailRequested[type=RECUPERACAO_SENHA]")
			.doesNotContain("ana@exemplo.com")
			.doesNotContain("segredo");
	}

	@Test
	void aceitaParametroNuloSemLancarExcecao() {
		Map<String, String> parameters = new HashMap<>();
		parameters.put("nome", null);

		EmailRequested event = new EmailRequested("ana@exemplo.com", EmailType.COMPRA_CONFIRMADA, parameters);

		assertThat(event.parameters()).containsEntry("nome", null);
	}

	@Test
	void parametrosSaoUmaCopiaImutavel() {
		Map<String, String> parameters = new HashMap<>(Map.of("nome", "Ana"));
		EmailRequested event = new EmailRequested("ana@exemplo.com", EmailType.COMPRA_CONFIRMADA, parameters);

		parameters.put("nome", "Outro");

		assertThat(event.parameters()).containsEntry("nome", "Ana");
		assertThatThrownBy(() -> event.parameters().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
	}

}
