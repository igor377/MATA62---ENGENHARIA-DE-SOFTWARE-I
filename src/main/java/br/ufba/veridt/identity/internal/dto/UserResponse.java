package br.ufba.veridt.identity.internal.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufba.veridt.identity.internal.domain.UserType;

/**
 * Dados públicos do usuário. Nunca inclui senha, hash ou o CPF completo: {@code cpfMasked} mostra só os 6 dígitos
 * centrais ({@code ***.456.789-**}).
 */
public record UserResponse(UUID id, String fullName, String email, String cpfMasked, UserType type,
		List<OabRegistrationResponse> oabRegistrations, Instant createdAt) {
}
