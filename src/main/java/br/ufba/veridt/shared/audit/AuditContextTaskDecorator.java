package br.ufba.veridt.shared.audit;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Leva o contexto de auditoria para outras threads. Trabalho disparado por uma requisição ({@code @Async}) herda o ID
 * e a origem dela. Sem contexto (tarefa agendada), cada execução recebe um ID novo, gerado dentro do {@code run()}
 * porque tarefas periódicas são decoradas uma única vez.
 */
public class AuditContextTaskDecorator implements TaskDecorator {

	private final String process;

	public AuditContextTaskDecorator(String process) {
		this.process = process;
	}

	@Override
	public Runnable decorate(Runnable runnable) {
		AuditContext captured = AuditContext.current();
		return () -> {
			AuditContext previous = AuditContext.current();
			String previousMdc = MDC.get(RequestIdFilter.MDC_KEY);
			AuditContext context = captured != null ? captured : AuditContext.background(process);
			AuditContext.set(context);
			MDC.put(RequestIdFilter.MDC_KEY, context.requestId().toString());
			try {
				runnable.run();
			}
			finally {
				AuditContext.set(previous);
				if (previousMdc == null) {
					MDC.remove(RequestIdFilter.MDC_KEY);
				}
				else {
					MDC.put(RequestIdFilter.MDC_KEY, previousMdc);
				}
			}
		};
	}

}
