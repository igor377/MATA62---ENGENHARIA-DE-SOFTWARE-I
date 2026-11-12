package br.ufba.veridt.identity.internal.validation;

import org.springframework.stereotype.Component;

import br.ufba.veridt.identity.internal.config.IdentityProperties;

/** Senha com tamanho mínimo configurável, até 128 caracteres, contendo letra, número e símbolo. */
@Component
public class PasswordPolicy {

	static final int MAX_LENGTH = 128;

	private final int minLength;

	public PasswordPolicy(IdentityProperties properties) {
		this.minLength = properties.passwordMinLength();
	}

	public boolean isValid(String password) {
		if (password == null || password.length() < minLength || password.length() > MAX_LENGTH) {
			return false;
		}
		boolean letter = password.chars().anyMatch(Character::isLetter);
		boolean digit = password.chars().anyMatch(Character::isDigit);
		boolean symbol = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
		return letter && digit && symbol;
	}

	public String description() {
		return "A senha deve ter entre %d e %d caracteres, com letra, número e símbolo".formatted(minLength,
				MAX_LENGTH);
	}

}
