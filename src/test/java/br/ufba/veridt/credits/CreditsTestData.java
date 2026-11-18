package br.ufba.veridt.credits;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.ufba.veridt.credits.internal.domain.PaymentMethod;
import br.ufba.veridt.credits.internal.dto.BillingInfoRequest;
import br.ufba.veridt.credits.internal.dto.PurchaseRequest;
import br.ufba.veridt.shared.validation.Uf;
import jakarta.servlet.http.Cookie;

final class CreditsTestData {

	/** CPF válido, com dígitos verificadores corretos. */
	static final String CPF = "52998224725";

	private CreditsTestData() {
	}

	static BillingInfoRequest faturamento() {
		return new BillingInfoRequest("Ana Souza", "Ana@Exemplo.com", "71999998888", "529.982.247-25", "40170110",
				"Av. Adhemar de Barros", "500", "Sala 2", "Ondina", "Salvador", Uf.BA);
	}

	static PurchaseRequest compra(String pacote, PaymentMethod metodo) {
		return new PurchaseRequest(pacote, metodo, faturamento());
	}

	static String chave() {
		return UUID.randomUUID().toString();
	}

	/**
	 * Envia o token no cookie e no cabeçalho, como a SPA. O atalho {@code csrf()} do Spring Security troca o
	 * repositório de tokens do filtro compartilhado e contamina os testes seguintes.
	 */
	static RequestPostProcessor csrfDaSpa() {
		return request -> {
			String token = UUID.randomUUID().toString();
			Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
			Cookie[] cookies = Arrays.copyOf(existing, existing.length + 1);
			cookies[existing.length] = new Cookie("XSRF-TOKEN", token);
			request.setCookies(cookies);
			request.addHeader("X-XSRF-TOKEN", token);
			return request;
		};
	}

}
