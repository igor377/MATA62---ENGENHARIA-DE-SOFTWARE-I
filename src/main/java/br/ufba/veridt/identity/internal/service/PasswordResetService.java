package br.ufba.veridt.identity.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import br.ufba.veridt.identity.internal.audit.IdentityAuditOperation;
import br.ufba.veridt.identity.internal.config.IdentityProperties;
import br.ufba.veridt.identity.internal.domain.PasswordResetToken;
import br.ufba.veridt.identity.internal.domain.User;
import br.ufba.veridt.identity.internal.dto.ResetPasswordRequest;
import br.ufba.veridt.identity.internal.event.PasswordChanged;
import br.ufba.veridt.identity.internal.exception.InvalidResetTokenException;
import br.ufba.veridt.identity.internal.repository.PasswordResetTokenRepository;
import br.ufba.veridt.identity.internal.repository.UserRepository;
import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.EmailType;
import br.ufba.veridt.shared.audit.AuditLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Recuperação de senha (REQ 02). O token vai só no e-mail; o banco guarda o SHA-256, com validade e uso único. */
@Service
@Validated
public class PasswordResetService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private final UserRepository users;
	private final PasswordResetTokenRepository tokens;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher events;
	private final IdentityProperties properties;
	private final AuditLog auditLog;

	public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
			PasswordEncoder passwordEncoder, ApplicationEventPublisher events, IdentityProperties properties,
			AuditLog auditLog) {
		this.users = users;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
		this.properties = properties;
		this.auditLog = auditLog;
	}

	/**
	 * Roda em segundo plano para o chamador retornar na hora: com e-mail cadastrado há gravações no banco, e o tempo de
	 * resposta revelaria quais e-mails existem.
	 */
	@Async("applicationTaskExecutor")
	@Transactional
	public void requestReset(@NotBlank String email) {
		users.findByEmail(User.normalizeEmail(email)).ifPresent(this::issueToken);
	}

	@Transactional
	public void resetPassword(@Valid ResetPasswordRequest request) {
		Instant now = Instant.now();
		PasswordResetToken token = tokens.findByTokenHash(hash(request.token()))
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(this::invalidToken);
		User user = users.findById(token.getUserId()).orElseThrow(this::invalidToken);

		user.changePassword(passwordEncoder.encode(request.newPassword()));
		token.markUsed(now);
		events.publishEvent(new PasswordChanged(user.getId()));
		auditLog.recordSuccess(IdentityAuditOperation.REDEFINICAO_SENHA, user.getId());
	}

	/** Registra a tentativa sem usuário: o token não identifica com segurança a quem pertence. */
	private InvalidResetTokenException invalidToken() {
		auditLog.recordFailure(IdentityAuditOperation.REDEFINICAO_SENHA, null, "TOKEN_INVALIDO");
		return new InvalidResetTokenException();
	}

	private void issueToken(User user) {
		Instant now = Instant.now();
		tokens.invalidateActiveTokens(user.getId(), now);
		String token = newToken();
		tokens.save(new PasswordResetToken(user.getId(), hash(token), now, now.plus(properties.resetTokenValidity())));
		events.publishEvent(new EmailRequested(user.getEmail(), EmailType.RECUPERACAO_SENHA,
				Map.of("nome", user.getFullName(), "link", properties.resetUrl() + "?token=" + token,
						"validadeMinutos", String.valueOf(properties.resetTokenValidity().toMinutes()))));
	}

	private static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível", ex);
		}
	}

}
