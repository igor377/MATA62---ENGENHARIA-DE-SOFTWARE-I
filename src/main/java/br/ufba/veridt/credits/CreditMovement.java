package br.ufba.veridt.credits;

import java.time.Instant;
import java.util.UUID;

/** Uma linha do extrato de créditos. {@code balanceAfter} é o saldo logo depois desta movimentação. */
public record CreditMovement(UUID id, UUID userId, CreditMovementType type, int amount, int balanceAfter,
		CreditOrigin origin, UUID referenceId, String idempotencyKey, Instant createdAt) {
}
