package br.ufba.veridt.identity.internal.mapper;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.ufba.veridt.identity.internal.domain.OabRegistration;
import br.ufba.veridt.identity.internal.domain.User;
import br.ufba.veridt.identity.internal.dto.OabRegistrationRequest;
import br.ufba.veridt.identity.internal.dto.OabRegistrationResponse;
import br.ufba.veridt.identity.internal.dto.RegisterUserRequest;
import br.ufba.veridt.identity.internal.dto.UserResponse;

@Component
public class UserMapper {

	/** Recebe o hash já calculado: a senha em texto puro nunca chega à entidade. */
	public User toEntity(RegisterUserRequest request, String passwordHash) {
		return new User(request.fullName(), request.email(), passwordHash, request.cpf(), request.type());
	}

	public List<OabRegistration> toOabRegistrations(List<OabRegistrationRequest> requests, UUID userId) {
		return requests.stream()
			.map(request -> new OabRegistration(userId, request.number(), request.state(), request.type()))
			.toList();
	}

	public UserResponse toResponse(User user, List<OabRegistration> registrations) {
		List<OabRegistrationResponse> oabRegistrations = registrations.stream()
			.map(registration -> new OabRegistrationResponse(registration.getNumber(), registration.getState(),
					registration.getType()))
			.toList();
		return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getType(), oabRegistrations,
				user.getCreatedAt());
	}

}
