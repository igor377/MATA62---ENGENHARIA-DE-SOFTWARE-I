package br.ufba.veridt.credits.internal.domain;

import java.util.Locale;

import br.ufba.veridt.shared.validation.Uf;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Dados de faturamento do Apêndice A. São dados pessoais: não têm {@code toString} e nunca vão para logs, respostas
 * da API ou auditoria. Telefone, CPF e CEP são gravados só com os dígitos.
 */
@Embeddable
public class BillingInfo {

	@Column(name = "fat_nome", nullable = false, length = 150)
	private String fullName;

	@Column(name = "fat_email", nullable = false, length = 254)
	private String email;

	@Column(name = "fat_telefone", nullable = false, length = 11)
	private String phone;

	@Column(name = "fat_cpf", nullable = false, length = 11)
	private String cpf;

	@Column(name = "fat_cep", nullable = false, length = 8)
	private String cep;

	@Column(name = "fat_logradouro", nullable = false, length = 150)
	private String street;

	@Column(name = "fat_numero", nullable = false, length = 20)
	private String number;

	@Column(name = "fat_complemento", length = 100)
	private String complement;

	@Column(name = "fat_bairro", nullable = false, length = 100)
	private String neighborhood;

	@Column(name = "fat_cidade", nullable = false, length = 100)
	private String city;

	@Enumerated(EnumType.STRING)
	@Column(name = "fat_uf", nullable = false, length = 2)
	private Uf state;

	protected BillingInfo() {
	}

	public BillingInfo(String fullName, String email, String phone, String cpf, String cep, String street,
			String number, String complement, String neighborhood, String city, Uf state) {
		this.fullName = fullName.strip();
		this.email = email.strip().toLowerCase(Locale.ROOT);
		this.phone = digits(phone);
		this.cpf = digits(cpf);
		this.cep = digits(cep);
		this.street = street.strip();
		this.number = number.strip();
		this.complement = complement == null || complement.isBlank() ? null : complement.strip();
		this.neighborhood = neighborhood.strip();
		this.city = city.strip();
		this.state = state;
	}

	private static String digits(String value) {
		return value.replaceAll("\\D", "");
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

}
