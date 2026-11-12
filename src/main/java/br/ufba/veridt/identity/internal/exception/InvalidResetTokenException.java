package br.ufba.veridt.identity.internal.exception;

import br.ufba.veridt.shared.error.exception.BusinessException;

/** Token inexistente, expirado ou já usado: a mensagem é a mesma para não revelar qual dos casos ocorreu. */
public class InvalidResetTokenException extends BusinessException {

	public InvalidResetTokenException() {
		super("TOKEN_INVALIDO", "Link de redefinição de senha inválido ou expirado.");
	}

}
