package br.ufba.veridt.shared.audit;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Dá um ID de correlação a cada requisição: vai para o MDC dos logs, para o cabeçalho {@code X-Request-ID} da
 * resposta e para a auditoria, junto com IP e user-agent. Um {@code X-Request-ID} recebido só é aceito se for UUID.
 * Roda antes do Spring Security, para que respostas 401 e 403 também tragam o ID.
 */
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-ID";

	public static final String MDC_KEY = "requestId";

	private final ClientAddressResolver addressResolver;

	public RequestIdFilter(ClientAddressResolver addressResolver) {
		this.addressResolver = addressResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		UUID requestId = parse(request.getHeader(HEADER));
		AuditContext.set(AuditContext.http(requestId, addressResolver.resolve(request), request.getHeader("User-Agent")));
		MDC.put(MDC_KEY, requestId.toString());
		response.setHeader(HEADER, requestId.toString());
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
			AuditContext.set(null);
		}
	}

	/** Só um UUID na forma canônica é aceito; qualquer outro valor é descartado e trocado por um novo. */
	static UUID parse(String header) {
		if (header != null && header.length() == 36) {
			try {
				UUID parsed = UUID.fromString(header);
				if (parsed.toString().equalsIgnoreCase(header)) {
					return parsed;
				}
			}
			catch (IllegalArgumentException ex) {
				// Valor inválido: cai na geração abaixo.
			}
		}
		return UUID.randomUUID();
	}

}
