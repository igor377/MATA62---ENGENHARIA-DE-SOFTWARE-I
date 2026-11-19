package br.ufba.veridt.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTests {

	private final RequestIdFilter filter = new RequestIdFilter(new ClientAddressResolver(List.of()));

	@Test
	void xRequestIdValidoEMantido() throws Exception {
		UUID enviado = UUID.randomUUID();

		Resultado resultado = filtrar(enviado.toString(), "Mozilla/5.0");

		assertThat(resultado.cabecalho()).isEqualTo(enviado.toString());
		assertThat(resultado.contexto().requestId()).isEqualTo(enviado);
		assertThat(resultado.mdc()).isEqualTo(enviado.toString());
	}

	@Test
	void xRequestIdInvalidoESubstituido() throws Exception {
		for (String invalido : List.of("abc", "1-1-1-1-1", "' OR 1=1 --", "x".repeat(36))) {
			Resultado resultado = filtrar(invalido, null);

			assertThat(resultado.cabecalho()).isNotEqualTo(invalido);
			assertThat(UUID.fromString(resultado.cabecalho())).isEqualTo(resultado.contexto().requestId());
		}
	}

	@Test
	void semXRequestIdGeraUmNovo() throws Exception {
		Resultado resultado = filtrar(null, null);

		assertThat(UUID.fromString(resultado.cabecalho())).isEqualTo(resultado.contexto().requestId());
	}

	@Test
	void gravaIpEUserAgentTruncadoELimpaOContextoAoFinal() throws Exception {
		Resultado resultado = filtrar(null, "a".repeat(400));

		assertThat(resultado.contexto().clientIp()).isEqualTo("203.0.113.5");
		assertThat(resultado.contexto().userAgent()).hasSize(AuditContext.USER_AGENT_MAX_LENGTH);
		assertThat(resultado.contexto().process()).isNull();
		assertThat(AuditContext.current()).isNull();
		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

	private Resultado filtrar(String requestId, String userAgent) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.5");
		if (requestId != null) {
			request.addHeader(RequestIdFilter.HEADER, requestId);
		}
		if (userAgent != null) {
			request.addHeader("User-Agent", userAgent);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<AuditContext> contexto = new AtomicReference<>();
		AtomicReference<String> mdc = new AtomicReference<>();

		filter.doFilter(request, response, (req, res) -> {
			contexto.set(AuditContext.current());
			mdc.set(MDC.get(RequestIdFilter.MDC_KEY));
		});

		return new Resultado(response.getHeader(RequestIdFilter.HEADER), contexto.get(), mdc.get());
	}

	private record Resultado(String cabecalho, AuditContext contexto, String mdc) {
	}

}
