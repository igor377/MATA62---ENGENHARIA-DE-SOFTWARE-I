package br.ufba.veridt.identity.internal.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidator implements ConstraintValidator<Cpf, String> {

	private static final Pattern FORMAT = Pattern.compile("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || isValid(value);
	}

	public static boolean isValid(String value) {
		String cpf = value.strip();
		if (!FORMAT.matcher(cpf).matches()) {
			return false;
		}
		String digits = cpf.replaceAll("\\D", "");
		// Sequências repetidas (111.111.111-11) passam no cálculo, mas não são CPFs válidos.
		if (digits.chars().distinct().count() == 1) {
			return false;
		}
		return checkDigit(digits, 9) == digits.charAt(9) - '0' && checkDigit(digits, 10) == digits.charAt(10) - '0';
	}

	private static int checkDigit(String digits, int length) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += (digits.charAt(i) - '0') * (length + 1 - i);
		}
		int rest = (sum * 10) % 11;
		return rest == 10 ? 0 : rest;
	}

}
