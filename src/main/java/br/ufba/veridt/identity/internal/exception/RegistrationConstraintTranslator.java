package br.ufba.veridt.identity.internal.exception;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Traduz a violação de uma constraint do banco no erro de negócio correspondente. Cobre cadastros simultâneos que
 * passam pelas checagens prévias. Violações sem tradução são devolvidas como estão: indicam defeito, não dado repetido.
 */
@Component
public class RegistrationConstraintTranslator {

	public RuntimeException translate(DataIntegrityViolationException ex) {
		String constraint = ex.getCause() instanceof ConstraintViolationException violation
				&& violation.getConstraintName() != null ? violation.getConstraintName().toLowerCase(Locale.ROOT) : "";
		return switch (constraint) {
			case "usuario_email_uk" -> new EmailAlreadyRegisteredException();
			case "usuario_cpf_uk" -> new CpfAlreadyRegisteredException();
			case "uk_oab_numero_uf_tipo" -> new OabAlreadyRegisteredException();
			default -> ex;
		};
	}

}
