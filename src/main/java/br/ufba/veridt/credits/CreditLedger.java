package br.ufba.veridt.credits;

import java.util.UUID;

/**
 * Saldo de créditos de cada usuário (CRE-02). O saldo nunca fica negativo, mesmo com débitos simultâneos, e cada
 * operação é idempotente pela chave: repetir a mesma chave devolve a movimentação já feita, sem novo efeito. As
 * operações participam da transação de quem chama.
 */
public interface CreditLedger {

	int balance(UUID userId);

	/**
	 * @throws IdempotencyKeyConflictException se a chave já foi usada em outra operação
	 */
	CreditMovement credit(UUID userId, int amount, CreditOrigin origin, UUID referenceId, String idempotencyKey);

	/**
	 * @throws InsufficientCreditsException se o saldo não cobre a quantidade; nada é alterado
	 * @throws IdempotencyKeyConflictException se a chave já foi usada em outra operação
	 */
	CreditMovement debit(UUID userId, int amount, CreditOrigin origin, UUID referenceId, String idempotencyKey);

}
