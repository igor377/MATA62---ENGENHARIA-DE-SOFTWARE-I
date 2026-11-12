package br.ufba.veridt.identity.internal.audit;

import br.ufba.veridt.shared.audit.AuditOperation;

/** Operações de identidade registradas na auditoria (ID-07). */
public enum IdentityAuditOperation implements AuditOperation {

	CADASTRO,
	LOGIN,
	LOGOUT,
	REDEFINICAO_SENHA

}
