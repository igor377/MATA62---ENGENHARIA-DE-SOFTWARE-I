package br.ufba.veridt.shared.audit;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Log de auditoria compartilhado (P6): quem, quando, operação e resultado. Só grava, sem regra de domínio. O
 * {@code detalhe} nunca pode conter CPF, e-mail, senha, token ou qualquer outro dado pessoal.
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

	@Transactional
	public void recordSuccess(AuditOperation operation, UUID userId, UUID resourceId) {
		repository.insert(userId, operation, AuditResult.SUCESSO, resourceId, null);
	}

	/** Transação própria: a falha continua registrada mesmo quando a operação de quem chama é desfeita. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(AuditOperation operation, UUID userId, String detail) {
		repository.insert(userId, operation, AuditResult.FALHA, null, detail);
	}

}
