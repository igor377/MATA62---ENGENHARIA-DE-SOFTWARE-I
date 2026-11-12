package br.ufba.veridt.identity.internal.dto;

import java.util.List;

import br.ufba.veridt.identity.internal.domain.UserType;
import br.ufba.veridt.identity.internal.validation.Cpf;
import br.ufba.veridt.identity.internal.validation.StrongPassword;
import br.ufba.veridt.identity.internal.validation.ValidOab;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados de cadastro (REQ 01, Apêndice A). Inscrições na OAB só são aceitas, e exigidas, para advogado. */
@ValidOab
public record RegisterUserRequest(
		@NotBlank @Size(max = 150) String fullName,
		@NotBlank @Email @Size(max = 254) String email,
		@NotNull @StrongPassword String password,
		@NotBlank @Cpf String cpf,
		@NotNull UserType type,
		@Size(max = 30) List<@NotNull @Valid OabRegistrationRequest> oabRegistrations) {

	/** Esconde a senha e o CPF em logs e mensagens de erro. */
	@Override
	public String toString() {
		return "RegisterUserRequest[type=" + type + "]";
	}

}
