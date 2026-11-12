package br.ufba.veridt.identity.internal.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Criado pelo Spring, que injeta a política configurada. */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

	private final PasswordPolicy policy;

	public StrongPasswordValidator(PasswordPolicy policy) {
		this.policy = policy;
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || policy.isValid(value)) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(policy.description()).addConstraintViolation();
		return false;
	}

}
