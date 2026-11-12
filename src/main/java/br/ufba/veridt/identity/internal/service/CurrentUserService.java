package br.ufba.veridt.identity.internal.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufba.veridt.identity.internal.dto.UserResponse;
import br.ufba.veridt.identity.internal.mapper.UserMapper;
import br.ufba.veridt.identity.internal.repository.OabRegistrationRepository;
import br.ufba.veridt.identity.internal.repository.UserRepository;
import br.ufba.veridt.shared.error.exception.UnauthorizedException;

/** Dados do usuário logado, usados pela SPA para saber quem está na sessão. */
@Service
public class CurrentUserService {

	private final UserRepository users;
	private final OabRegistrationRepository oabRegistrations;
	private final UserMapper mapper;

	public CurrentUserService(UserRepository users, OabRegistrationRepository oabRegistrations, UserMapper mapper) {
		this.users = users;
		this.oabRegistrations = oabRegistrations;
		this.mapper = mapper;
	}

	/** O principal é o UUID do usuário (ver {@code VeridtUserDetailsService}). */
	@Transactional(readOnly = true)
	public UserResponse get(UUID userId) {
		return users.findById(userId)
			.map(user -> mapper.toResponse(user, oabRegistrations.findByUserId(userId)))
			// Conta removida com a sessão ainda aberta: trata como sessão inválida.
			.orElseThrow(() -> new UnauthorizedException("NAO_AUTENTICADO", "Autenticação necessária."));
	}

}
