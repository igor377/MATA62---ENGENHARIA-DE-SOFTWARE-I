package br.ufba.veridt.credits;

import br.ufba.veridt.shared.error.exception.PaymentRequiredException;

public class InsufficientCreditsException extends PaymentRequiredException {

	public InsufficientCreditsException() {
		super("SALDO_INSUFICIENTE", "Saldo de créditos insuficiente.");
	}

}
