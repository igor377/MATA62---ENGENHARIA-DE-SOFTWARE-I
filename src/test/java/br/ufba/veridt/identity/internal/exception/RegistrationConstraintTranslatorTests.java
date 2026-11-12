package br.ufba.veridt.identity.internal.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RegistrationConstraintTranslatorTests {

	private final RegistrationConstraintTranslator translator = new RegistrationConstraintTranslator();

	@Test
	void traduzCadaConstraintNoErroCorrespondente() {
		assertThat(translator.translate(violacao("usuario_email_uk"))).isInstanceOf(EmailAlreadyRegisteredException.class);
		assertThat(translator.translate(violacao("usuario_cpf_uk"))).isInstanceOf(CpfAlreadyRegisteredException.class);
		assertThat(translator.translate(violacao("uk_oab_numero_uf_tipo")))
			.isInstanceOf(OabAlreadyRegisteredException.class);
	}

	@Test
	void violacaoSemTraducaoEDevolvidaComoEsta() {
		DataIntegrityViolationException original = violacao("usuario_cpf_formato_ck");

		assertThat(translator.translate(original)).isSameAs(original);
	}

	@Test
	void causaDesconhecidaEDevolvidaComoEsta() {
		DataIntegrityViolationException original = new DataIntegrityViolationException("erro");

		assertThat(translator.translate(original)).isSameAs(original);
	}

	private static DataIntegrityViolationException violacao(String constraint) {
		return new DataIntegrityViolationException("erro",
				new ConstraintViolationException("erro", new SQLException("erro"), constraint));
	}

}
