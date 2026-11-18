package br.ufba.veridt.credits.internal.dto;

import br.ufba.veridt.shared.validation.Cpf;
import br.ufba.veridt.shared.validation.Uf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados de faturamento do Apêndice A. Telefone e CEP só com dígitos; o CPF aceita máscara. */
public record BillingInfoRequest(
		@NotBlank @Size(max = 150) String fullName,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = "\\d{10,11}", message = "Telefone com DDD, só dígitos") String phone,
		@NotBlank @Cpf String cpf,
		@NotBlank @Pattern(regexp = "\\d{8}", message = "CEP com 8 dígitos") String cep,
		@NotBlank @Size(max = 150) String street,
		@NotBlank @Size(max = 20) String number,
		@Size(max = 100) String complement,
		@NotBlank @Size(max = 100) String neighborhood,
		@NotBlank @Size(max = 100) String city,
		@NotNull Uf state) {

	/** Dados pessoais nunca podem aparecer em logs. */
	@Override
	public String toString() {
		return "BillingInfoRequest[]";
	}

}
