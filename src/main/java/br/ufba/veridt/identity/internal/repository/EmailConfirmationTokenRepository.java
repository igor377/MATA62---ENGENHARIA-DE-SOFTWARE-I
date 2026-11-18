package br.ufba.veridt.identity.internal.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.ufba.veridt.identity.internal.domain.EmailConfirmationToken;
import jakarta.persistence.LockModeType;

public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, UUID> {

	/** Bloqueia a linha para que dois cliques simultâneos não usem o mesmo token. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);

	/** Usado no intervalo mínimo entre reenvios. */
	boolean existsByUserIdAndCreatedAtAfter(UUID userId, Instant since);

	@Modifying
	@Query("UPDATE EmailConfirmationToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
	void invalidateActiveTokens(@Param("userId") UUID userId, @Param("now") Instant now);

}
