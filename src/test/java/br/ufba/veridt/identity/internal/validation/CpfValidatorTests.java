package br.ufba.veridt.identity.internal.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpfValidatorTests {

	@ParameterizedTest
	@ValueSource(strings = { "529.982.247-25", "52998224725", " 111.444.777-35 " })
	void aceitaCpfValidoComOuSemMascara(String cpf) {
		assertThat(CpfValidator.isValid(cpf)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "529.982.247-26", "52998224715", "111.111.111-11", "00000000000", "5299822472",
			"529982247250", "529.982.247/25", "abc.def.ghi-jk", "" })
	void recusaCpfInvalido(String cpf) {
		assertThat(CpfValidator.isValid(cpf)).isFalse();
	}

}
