package br.ufba.veridt.credits.internal.exception;

import br.ufba.veridt.shared.error.exception.ResourceNotFoundException;

/** Pedido inexistente ou de outro usuário: a resposta é a mesma, para não revelar pedidos alheios. */
public class PurchaseNotFoundException extends ResourceNotFoundException {

	public PurchaseNotFoundException() {
		super("PAGAMENTO_NAO_ENCONTRADO", "Pagamento não encontrado.");
	}

}
