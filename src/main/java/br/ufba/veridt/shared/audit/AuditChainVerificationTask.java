package br.ufba.veridt.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Verifica a cadeia periodicamente ({@code veridt.audit.verification-interval}). Não há rota: ainda não existe perfil
 * de administrador. Uma quebra é gravada na própria auditoria como falha de {@code VERIFICACAO_AUDITORIA}, com
 * {@code ELO_QUEBRADO:<seq>:<motivo>} no detalhe; uma verificação íntegra não grava nada, para não encher a tabela.
 */
@Component
public class AuditChainVerificationTask {

	private static final Logger log = LoggerFactory.getLogger(AuditChainVerificationTask.class);

	enum Operation implements AuditOperation {
		VERIFICACAO_AUDITORIA
	}

	private final AuditChainVerifier verifier;
	private final AuditLog auditLog;

	public AuditChainVerificationTask(AuditChainVerifier verifier, AuditLog auditLog) {
		this.verifier = verifier;
		this.auditLog = auditLog;
	}

	@Scheduled(fixedDelayString = "${veridt.audit.verification-interval}",
			initialDelayString = "${veridt.audit.verification-interval}")
	public void verify() {
		verifier.verify().ifPresent(chainBreak -> {
			log.warn("Cadeia da auditoria quebrada na posição {} ({})", chainBreak.seq(), chainBreak.reason());
			auditLog.recordFailure(Operation.VERIFICACAO_AUDITORIA, null,
					"ELO_QUEBRADO:" + chainBreak.seq() + ":" + chainBreak.reason());
		});
	}

}
