package br.ufba.veridt.credits.internal.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufba.veridt.credits.CreditLedger;
import br.ufba.veridt.credits.CreditMovement;
import br.ufba.veridt.credits.CreditMovementType;
import br.ufba.veridt.credits.CreditOrigin;
import br.ufba.veridt.credits.IdempotencyKeyConflictException;
import br.ufba.veridt.credits.InsufficientCreditsException;
import br.ufba.veridt.credits.internal.repository.CreditLedgerRepository;

/**
 * Saldo com extrato somente de inserção. Cada operação: trava a chave de idempotência, devolve a movimentação já
 * feita se a chave existir, atualiza o saldo de forma atômica e grava a movimentação. Tudo na transação de quem chama.
 */
@Service
public class JdbcCreditLedger implements CreditLedger {

	private static final int MAX_KEY_LENGTH = 150;

	private final CreditLedgerRepository repository;

	public JdbcCreditLedger(CreditLedgerRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public int balance(UUID userId) {
		return repository.balance(Objects.requireNonNull(userId));
	}

	@Override
	@Transactional
	public CreditMovement credit(UUID userId, int amount, CreditOrigin origin, UUID referenceId, String idempotencyKey) {
		return apply(CreditMovementType.CREDITO, userId, amount, origin, referenceId, idempotencyKey);
	}

	@Override
	@Transactional
	public CreditMovement debit(UUID userId, int amount, CreditOrigin origin, UUID referenceId, String idempotencyKey) {
		return apply(CreditMovementType.DEBITO, userId, amount, origin, referenceId, idempotencyKey);
	}

	private CreditMovement apply(CreditMovementType type, UUID userId, int amount, CreditOrigin origin,
			UUID referenceId, String idempotencyKey) {
		validate(userId, amount, origin, idempotencyKey);
		repository.lockIdempotencyKey(idempotencyKey);
		var existing = repository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return sameOperationOrConflict(existing.get(), type, userId, amount, origin);
		}

		// Na precisão do Postgres: a movimentação devolvida é igual à que uma consulta posterior lê.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		int balanceAfter = type == CreditMovementType.CREDITO ? repository.addToBalance(userId, amount, now)
				: repository.subtractFromBalance(userId, amount, now).orElseThrow(InsufficientCreditsException::new);
		CreditMovement movement = new CreditMovement(UUID.randomUUID(), userId, type, amount, balanceAfter, origin,
				referenceId, idempotencyKey, now);
		repository.insertMovement(movement);
		return movement;
	}

	private static CreditMovement sameOperationOrConflict(CreditMovement existing, CreditMovementType type,
			UUID userId, int amount, CreditOrigin origin) {
		boolean same = existing.type() == type && existing.userId().equals(userId) && existing.amount() == amount
				&& existing.origin() == origin;
		if (!same) {
			throw new IdempotencyKeyConflictException();
		}
		return existing;
	}

	private static void validate(UUID userId, int amount, CreditOrigin origin, String idempotencyKey) {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(origin, "origin");
		if (amount <= 0) {
			throw new IllegalArgumentException("A quantidade de créditos deve ser positiva");
		}
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
			throw new IllegalArgumentException("Chave de idempotência inválida");
		}
	}

}
