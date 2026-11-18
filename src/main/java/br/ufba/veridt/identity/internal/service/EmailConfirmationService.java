package br.ufba.veridt.identity.internal.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import br.ufba.veridt.identity.internal.audit.IdentityAuditOperation;
import br.ufba.veridt.identity.internal.config.IdentityProperties;
import br.ufba.veridt.identity.internal.domain.EmailConfirmationToken;
import br.ufba.veridt.identity.internal.domain.User;
import br.ufba.veridt.identity.internal.exception.InvalidConfirmationTokenException;
import br.ufba.veridt.identity.internal.repository.EmailConfirmationTokenRepository;
import br.ufba.veridt.identity.internal.repository.UserRepository;
import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.EmailType;
import br.ufba.veridt.shared.audit.AuditLog;
import jakarta.validation.constraints.NotBlank;

/**
 * Confirmação de e-mail (ID-08): o login só é liberado depois que o usuário prova ser dono do endereço. O token vai
 * só no e-mail; o banco guarda o SHA-256, com validade e uso único.
 */
@Service
@Validated
public class EmailConfirmationService {

	private final UserRepository users;
	private final EmailConfirmationTokenRepository tokens;
	private final ApplicationEventPublisher events;
	private final IdentityProperties properties;
	private final AuditLog auditLog;

	public EmailConfirmationService(UserRepository users, EmailConfirmationTokenRepository tokens,
			ApplicationEventPublisher events, IdentityProperties properties, AuditLog auditLog) {
		this.users = users;
		this.tokens = tokens;
		this.events = events;
		this.properties = properties;
		this.auditLog = auditLog;
	}

	/**
	 * Emite um token e pede o envio do e-mail. Participa da transação de quem chama: se o cadastro for desfeito, nem o
	 * token nem o e-mail sobrevivem.
	 */
	@Transactional
	public void issue(User user) {
		Instant now = Instant.now();
		tokens.invalidateActiveTokens(user.getId(), now);
		String token = SecureTokens.newToken();
		tokens.save(new EmailConfirmationToken(user.getId(), SecureTokens.hash(token), now,
				now.plus(properties.confirmationTokenValidity())));
		events.publishEvent(new EmailRequested(user.getEmail(), EmailType.CONFIRMACAO_EMAIL,
				Map.of("nome", user.getFullName(), "link", properties.confirmationUrl() + "?token=" + token,
						"validadeHoras", String.valueOf(properties.confirmationTokenValidity().toHours()))));
	}

	@Transactional
	public void confirm(@NotBlank String token) {
		Instant now = Instant.now();
		EmailConfirmationToken confirmation = tokens.findByTokenHash(SecureTokens.hash(token))
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(this::invalidToken);
		User user = users.findById(confirmation.getUserId()).orElseThrow(this::invalidToken);

		user.confirmEmail(now);
		confirmation.markUsed(now);
		auditLog.recordSuccess(IdentityAuditOperation.CONFIRMACAO_EMAIL, user.getId());
	}

	/**
	 * Roda em segundo plano para o chamador retornar na hora: o tempo de resposta não revela se o e-mail existe nem se
	 * já foi confirmado. Respeita um intervalo mínimo por conta, para o reenvio não servir para lotar a caixa de alguém.
	 */
	@Async("applicationTaskExecutor")
	@Transactional
	public void resend(@NotBlank String email) {
		Instant recent = Instant.now().minus(properties.confirmationResendInterval());
		users.findByEmail(User.normalizeEmail(email))
			.filter(user -> !user.isEmailConfirmed())
			.filter(user -> !tokens.existsByUserIdAndCreatedAtAfter(user.getId(), recent))
			.ifPresent(this::issue);
	}

	/** Registra a tentativa sem usuário: o token não identifica com segurança a quem pertence. */
	private InvalidConfirmationTokenException invalidToken() {
		auditLog.recordFailure(IdentityAuditOperation.CONFIRMACAO_EMAIL, null, "TOKEN_INVALIDO");
		return new InvalidConfirmationTokenException();
	}

}
