package br.ufba.veridt.shared.audit;

/**
 * Operação auditável. Cada módulo define um enum que implementa esta interface, para as operações serem tipadas sem
 * {@code shared} conhecer os módulos. O nome gravado é o da constante, em maiúsculas.
 */
public interface AuditOperation {

	String name();

}
