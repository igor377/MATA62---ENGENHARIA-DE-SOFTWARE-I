package br.ufba.veridt.notification.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import br.ufba.veridt.notification.EmailType;

class EmailTemplateRendererTests {

	private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

	@Test
	void recuperacaoDeSenha() {
		RenderedEmail email = renderer.render(EmailType.RECUPERACAO_SENHA,
				Map.of("nome", "Ana Souza", "link", "https://veridt.local/redefinir?token=abc", "validadeMinutos", "30"));

		assertThat(email.subject()).isEqualTo("Recuperação de senha");
		assertThat(email.htmlBody()).contains("Olá, Ana Souza.")
			.contains("href=\"https://veridt.local/redefinir?token=abc\"")
			.contains("vale por 30 minutos");
	}

	@Test
	void compraConfirmada() {
		RenderedEmail email = renderer.render(EmailType.COMPRA_CONFIRMADA,
				Map.of("nome", "Ana Souza", "pacote", "Premium", "creditos", "50", "valor", "R$ 250,00"));

		assertThat(email.subject()).isEqualTo("Compra de créditos confirmada");
		assertThat(email.htmlBody()).contains("Olá, Ana Souza.")
			.contains(">Premium<")
			.contains(">50<")
			.contains(">R$ 250,00<");
	}

	@Test
	void gravacaoConcluida() {
		RenderedEmail email = renderer.render(EmailType.GRAVACAO_CONCLUIDA, registro());

		assertThat(email.subject()).isEqualTo("Gravação concluída");
		assertThat(email.htmlBody()).contains("gravação de navegação foi concluída").satisfies(this::contemRegistro);
	}

	@Test
	void capturaConcluida() {
		RenderedEmail email = renderer.render(EmailType.CAPTURA_CONCLUIDA, registro());

		assertThat(email.subject()).isEqualTo("Captura de tela concluída");
		assertThat(email.htmlBody()).contains("captura de tela foi concluída").satisfies(this::contemRegistro);
	}

	@Test
	void todosOsMarcadoresSaoSubstituidos() {
		for (EmailType type : EmailType.values()) {
			Map<String, String> parameters = new java.util.HashMap<>();
			type.requiredParameters().forEach(name -> parameters.put(name, "valor"));

			assertThat(renderer.render(type, parameters).htmlBody()).as(type.name()).doesNotContainPattern("\\{\\w+}");
		}
	}

	@Test
	void parametrosSaoEscapados() {
		RenderedEmail email = renderer.render(EmailType.CAPTURA_CONCLUIDA,
				Map.of("titulo", "<script>alert('x')</script>", "url", "https://a.com/?a=1&b=2", "registroId", "1",
						"concluidoEm", "agora"));

		assertThat(email.htmlBody()).doesNotContain("<script>")
			.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
			.contains("https://a.com/?a=1&amp;b=2");
	}

	@Test
	void valorComMarcadorNaoESubstituidoDeNovo() {
		RenderedEmail email = renderer.render(EmailType.RECUPERACAO_SENHA,
				Map.of("nome", "{link}", "link", "https://veridt.local/redefinir", "validadeMinutos", "30"));

		assertThat(email.htmlBody()).contains("Olá, {link}.");
	}

	@Test
	void parametroAusenteGeraErro() {
		assertThatThrownBy(() -> renderer.render(EmailType.COMPRA_CONFIRMADA, Map.of("nome", "Ana")))
			.isInstanceOf(EmailTemplateException.class)
			.hasMessageContaining("pacote")
			.hasMessageContaining("creditos")
			.hasMessageContaining("valor");
	}

	@Test
	void parametroEmBrancoGeraErro() {
		assertThatThrownBy(() -> renderer.render(EmailType.RECUPERACAO_SENHA,
				Map.of("nome", "Ana", "link", " ", "validadeMinutos", "30")))
			.isInstanceOf(EmailTemplateException.class)
			.hasMessageContaining("link");
	}

	private static Map<String, String> registro() {
		return Map.of("titulo", "Post difamatório", "url", "https://exemplo.com/post", "registroId",
				"3f1c2a9e-0d4b-4c3e-9a61-7f2b8e5d1c40", "concluidoEm", "07/11/2026 14:30");
	}

	private void contemRegistro(String body) {
		assertThat(body).contains(">Post difamatório<")
			.contains(">https://exemplo.com/post<")
			.contains(">3f1c2a9e-0d4b-4c3e-9a61-7f2b8e5d1c40<")
			.contains(">07/11/2026 14:30<");
	}

}
