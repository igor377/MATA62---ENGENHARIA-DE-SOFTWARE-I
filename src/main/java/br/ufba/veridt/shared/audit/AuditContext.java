package br.ufba.veridt.shared.audit;

import java.util.UUID;

/**
 * Origem do evento auditado: ID de correlação e, em requisições HTTP, IP e user-agent do cliente. Fora de requisições
 * (tarefa agendada ou processo assíncrono sem requisição), IP e user-agent são nulos e {@code process} identifica o
 * processo. IP e user-agent são dados pessoais: nunca vão para logs nem para respostas da API.
 */
public record AuditContext(UUID requestId, String clientIp, String userAgent, String process) {

	public static final int USER_AGENT_MAX_LENGTH = 255;

	public static final String SCHEDULED_TASK = "TAREFA_AGENDADA";

	public static final String ASYNC_PROCESS = "PROCESSO_ASSINCRONO";

	private static final ThreadLocal<AuditContext> CURRENT = new ThreadLocal<>();

	public static AuditContext http(UUID requestId, String clientIp, String userAgent) {
		return new AuditContext(requestId, clientIp, truncate(userAgent), null);
	}

	public static AuditContext background(String process) {
		return new AuditContext(UUID.randomUUID(), null, null, process);
	}

	/** Contexto da thread atual, ou nulo quando ninguém o definiu (por exemplo, testes chamando o serviço direto). */
	public static AuditContext current() {
		return CURRENT.get();
	}

	static void set(AuditContext context) {
		if (context == null) {
			CURRENT.remove();
		}
		else {
			CURRENT.set(context);
		}
	}

	/** Corta por caracteres (code points), como o {@code VARCHAR} do Postgres, sem partir um par substituto. */
	static String truncate(String userAgent) {
		if (userAgent == null || userAgent.codePointCount(0, userAgent.length()) <= USER_AGENT_MAX_LENGTH) {
			return userAgent;
		}
		return userAgent.substring(0, userAgent.offsetByCodePoints(0, USER_AGENT_MAX_LENGTH));
	}

}
