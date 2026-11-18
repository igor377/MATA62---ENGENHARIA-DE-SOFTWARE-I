package br.ufba.veridt.shared.error.exception;

/** A operação exige pagamento ou saldo que o usuário não tem (402). */
public class PaymentRequiredException extends BusinessException {

	public PaymentRequiredException(String code, String message) {
		super(code, message);
	}

}
