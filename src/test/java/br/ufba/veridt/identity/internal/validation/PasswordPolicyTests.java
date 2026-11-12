package br.ufba.veridt.identity.internal.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.ufba.veridt.identity.internal.config.IdentityProperties;

class PasswordPolicyTests {

	private final PasswordPolicy policy = new PasswordPolicy(
			new IdentityProperties(Duration.ofMinutes(15), "http://localhost/redefinir", 8));

	@ParameterizedTest
	@ValueSource(strings = { "Senha#123", "a1!bcdef", "ÁrvoreÇ9@" })
	void aceitaSenhaComLetraNumeroESimbolo(String senha) {
		assertThat(policy.isValid(senha)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Sen#1", "SenhaSemNumero!", "Senha12345", "12345678!", "senha 123" })
	void recusaSenhaFraca(String senha) {
		assertThat(policy.isValid(senha)).isFalse();
	}

	@Test
	void recusaSenhaMaiorQue128Caracteres() {
		assertThat(policy.isValid("a1!" + "x".repeat(126))).isFalse();
		assertThat(policy.isValid("a1!" + "x".repeat(125))).isTrue();
	}

	@Test
	void recusaNulo() {
		assertThat(policy.isValid(null)).isFalse();
	}

}
