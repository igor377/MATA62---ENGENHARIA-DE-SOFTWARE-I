package br.ufba.veridt.identity.internal.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.ufba.veridt.identity.internal.audit.IdentityAuditOperation;
import br.ufba.veridt.identity.internal.config.IdentityProperties;
import br.ufba.veridt.identity.internal.repository.UserRepository;
import br.ufba.veridt.shared.audit.AuditLog;

/**
 * Apaga as contas que nunca confirmaram o e-mail. Sem isso, quem cadastrasse o e-mail ou o CPF de outra pessoa os
 * bloquearia para sempre. Cada exclusão fica na auditoria, que não tem chave estrangeira para {@code usuario}.
 */
@Component
public class UnconfirmedAccountCleanup {

	private final UserRepository users;
	private final IdentityProperties properties;
	private final AuditLog auditLog;

	public UnconfirmedAccountCleanup(UserRepository users, IdentityProperties properties, AuditLog auditLog) {
		this.users = users;
		this.properties = properties;
		this.auditLog = auditLog;
	}

	@Scheduled(fixedDelayString = "${veridt.identity.unconfirmed-cleanup-interval}")
	@Transactional
	public void deleteExpiredUnconfirmedAccounts() {
		Instant limit = Instant.now().minus(properties.unconfirmedAccountRetention());
		for (UUID userId : users.findUnconfirmedCreatedBefore(limit)) {
			if (users.deleteIfUnconfirmed(userId) > 0) {
				auditLog.recordSuccess(IdentityAuditOperation.EXCLUSAO_CONTA_NAO_CONFIRMADA, userId);
			}
		}
	}

}
