package br.ufba.veridt.credits.internal.audit;

import br.ufba.veridt.shared.audit.AuditOperation;

/** Operações de créditos registradas na auditoria (CRE-08). O recurso é sempre o ID do pedido. */
public enum CreditsAuditOperation implements AuditOperation {

	PEDIDO_CRIADO,
	PAGAMENTO_APROVADO,
	PAGAMENTO_RECUSADO,
	CREDITOS_CONCEDIDOS

}
