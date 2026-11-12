package br.ufba.veridt.identity.internal.dto;

import br.ufba.veridt.identity.internal.domain.OabRegistrationType;
import br.ufba.veridt.identity.internal.domain.Uf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OabRegistrationRequest(
		@NotBlank @Pattern(regexp = "(?!0+$)\\d{1,6}", message = "deve ter até 6 dígitos e não pode ser zero") String number,
		@NotNull Uf state,
		@NotNull OabRegistrationType type) {
}
