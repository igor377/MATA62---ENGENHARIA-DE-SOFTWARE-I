package br.ufba.veridt.identity.internal.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import br.ufba.veridt.identity.internal.audit.IdentityAuditOperation;
import br.ufba.veridt.identity.internal.domain.OabRegistration;
import br.ufba.veridt.identity.internal.domain.User;
import br.ufba.veridt.identity.internal.dto.OabRegistrationRequest;
import br.ufba.veridt.identity.internal.dto.RegisterUserRequest;
import br.ufba.veridt.identity.internal.dto.UserResponse;
import br.ufba.veridt.identity.internal.exception.CpfAlreadyRegisteredException;
import br.ufba.veridt.identity.internal.exception.EmailAlreadyRegisteredException;
import br.ufba.veridt.identity.internal.exception.OabAlreadyRegisteredException;
import br.ufba.veridt.identity.internal.exception.RegistrationConstraintTranslator;
import br.ufba.veridt.identity.internal.mapper.UserMapper;
import br.ufba.veridt.identity.internal.repository.OabRegistrationRepository;
import br.ufba.veridt.identity.internal.repository.UserRepository;
import br.ufba.veridt.shared.audit.AuditLog;
import jakarta.validation.Valid;

/** Cadastro de usuários (REQ 01). A senha é gravada só como hash Argon2id. */
@Service
@Validated
public class RegistrationService {

	private final UserRepository users;
	private final OabRegistrationRepository oabRegistrations;
	private final UserMapper mapper;
	private final PasswordEncoder passwordEncoder;
	private final RegistrationConstraintTranslator constraintTranslator;
	private final AuditLog auditLog;

	public RegistrationService(UserRepository users, OabRegistrationRepository oabRegistrations, UserMapper mapper,
			PasswordEncoder passwordEncoder, RegistrationConstraintTranslator constraintTranslator, AuditLog auditLog) {
		this.users = users;
		this.oabRegistrations = oabRegistrations;
		this.mapper = mapper;
		this.passwordEncoder = passwordEncoder;
		this.constraintTranslator = constraintTranslator;
		this.auditLog = auditLog;
	}

	@Transactional
	public UserResponse register(@Valid RegisterUserRequest request) {
		List<OabRegistrationRequest> requestedOab = request.oabRegistrations() == null ? List.of()
				: request.oabRegistrations();
		assertAvailable(request, requestedOab);
		User user = mapper.toEntity(request, passwordEncoder.encode(request.password()));
		try {
			users.saveAndFlush(user);
			List<OabRegistration> saved = oabRegistrations
				.saveAllAndFlush(mapper.toOabRegistrations(requestedOab, user.getId()));
			auditLog.recordSuccess(IdentityAuditOperation.CADASTRO, user.getId());
			return mapper.toResponse(user, saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw constraintTranslator.translate(ex);
		}
	}

	private void assertAvailable(RegisterUserRequest request, List<OabRegistrationRequest> requestedOab) {
		if (users.existsByEmail(User.normalizeEmail(request.email()))) {
			throw new EmailAlreadyRegisteredException();
		}
		if (users.existsByCpf(User.normalizeCpf(request.cpf()))) {
			throw new CpfAlreadyRegisteredException();
		}
		boolean oabTaken = requestedOab.stream()
			.anyMatch(oab -> oabRegistrations.existsByNumberAndStateAndType(OabRegistration.normalizeNumber(oab.number()),
					oab.state(), oab.type()));
		if (oabTaken) {
			throw new OabAlreadyRegisteredException();
		}
	}

}
