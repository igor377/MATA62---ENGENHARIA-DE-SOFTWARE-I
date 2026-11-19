package br.ufba.veridt.identity.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserMapperTests {

	@Test
	void mascaraOCpfMostrandoSoOsSeisDigitosCentrais() {
		assertThat(UserMapper.maskCpf("12345678900")).isEqualTo("***.456.789-**");
	}

	@Test
	void cpfAusenteOuMalFormadoViraNulo() {
		assertThat(UserMapper.maskCpf(null)).isNull();
		assertThat(UserMapper.maskCpf("")).isNull();
		assertThat(UserMapper.maskCpf("123")).isNull();
	}

}
