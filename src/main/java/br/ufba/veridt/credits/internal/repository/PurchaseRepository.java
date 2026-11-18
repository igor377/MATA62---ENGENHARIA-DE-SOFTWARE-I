package br.ufba.veridt.credits.internal.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.ufba.veridt.credits.internal.domain.Purchase;
import jakarta.persistence.LockModeType;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

	Optional<Purchase> findByIdAndUserId(UUID id, UUID userId);

	Optional<Purchase> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	Page<Purchase> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	/** Trava o pedido: confirmações simultâneas do mesmo pagamento são aplicadas uma de cada vez. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Purchase p WHERE p.id = :id")
	Optional<Purchase> findForUpdate(@Param("id") UUID id);

}
