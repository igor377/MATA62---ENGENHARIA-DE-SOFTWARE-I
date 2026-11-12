package br.ufba.veridt.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Token de recuperação de senha. Só o hash SHA-256 é gravado; o valor original existe apenas no e-mail. */
@Entity
@Table(name = "token_recuperacao_senha")
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "usuario_id", nullable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "expira_em", nullable = false)
	private Instant expiresAt;

	@Column(name = "usado_em")
	private Instant usedAt;

	@Column(name = "criado_em", nullable = false)
	private Instant createdAt;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

}
