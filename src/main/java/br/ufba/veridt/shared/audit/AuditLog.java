package br.ufba.veridt.shared.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Log de auditoria compartilhado (P6): quem, quando, operação, resultado, origem (IP, user-agent) e ID de correlação,
 * lidos do {@link AuditContext} da thread. Só grava, sem regra de domínio. O {@code detalhe} nunca pode conter CPF,
 * e-mail, senha, token ou qualquer outro dado pessoal.
 */
@Component
public class AuditLog {

	private final AuditRepository repository;

	public AuditLog(AuditRepository repository) {
		this.repository = repository;
	}

	/** Participa da transação de quem chama: a operação e o registro são gravados juntos ou nenhum dos dois. */
	@Transactional
	public void recordSuccess(AuditOperation operation, UUID userId) {
		recordSuccess(operation, userId, null);
	}

	/**
	 * O insert fica para o fim da transação ({@code beforeCommit}). A trigger da cadeia trava a cabeça até o commit, então
	 * inserir agora seguraria a trava durante toda a operação e travaria a thread se, depois, a mesma operação chamasse
	 * {@link #recordFailure}, que abre outra transação e esperaria por essa trava. O horário gravado é o desta chamada.
	 */
	@Transactional
	public void recordSuccess(AuditOperation operation, UUID userId, UUID resourceId) {
		AuditRepository.Entry entry = entry(operation, AuditResult.SUCESSO, userId, resourceId, null);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void beforeCommit(boolean readOnly) {
					repository.insert(entry);
				}
			});
		}
		else {
			repository.insert(entry);
		}
	}

	/** Transação própria: a falha continua registrada mesmo quando a operação de quem chama é desfeita. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(AuditOperation operation, UUID userId, String detail) {
		repository.insert(entry(operation, AuditResult.FALHA, userId, null, detail));
	}

	private static AuditRepository.Entry entry(AuditOperation operation, AuditResult result, UUID userId,
			UUID resourceId, String detail) {
		AuditContext context = AuditContext.current();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
		if (context == null) {
			return new AuditRepository.Entry(UUID.randomUUID(), now, userId, operation.name(), result, resourceId,
					detail, null, null, null);
		}
		return new AuditRepository.Entry(UUID.randomUUID(), now, userId, operation.name(), result, resourceId,
				withProcess(context.process(), detail), context.clientIp(), context.userAgent(), context.requestId());
	}

	/** Fora de requisição HTTP, o detalhe identifica o processo: {@code TAREFA_AGENDADA} ou {@code TAREFA_AGENDADA:CODIGO}. */
	private static String withProcess(String process, String detail) {
		if (process == null) {
			return detail;
		}
		return detail == null ? process : process + ":" + detail;
	}

}
