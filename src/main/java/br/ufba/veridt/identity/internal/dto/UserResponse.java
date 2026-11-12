package br.ufba.veridt.identity.internal.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufba.veridt.identity.internal.domain.UserType;

/** Dados públicos do usuário. Nunca inclui senha, hash ou CPF. */
public record UserResponse(UUID id, String fullName, String email, UserType type,
		List<OabRegistrationResponse> oabRegistrations, Instant createdAt) {
}
