package br.ufba.veridt.credits;

import br.ufba.veridt.shared.error.exception.ConflictException;

/** A chave de idempotência já foi usada em uma operação diferente (outro usuário, tipo, origem ou quantidade). */
public class IdempotencyKeyConflictException extends ConflictException {

	public IdempotencyKeyConflictException() {
		super("CHAVE_IDEMPOTENCIA_REUTILIZADA", "A chave de idempotência já foi usada em outra operação.");
	}

}
