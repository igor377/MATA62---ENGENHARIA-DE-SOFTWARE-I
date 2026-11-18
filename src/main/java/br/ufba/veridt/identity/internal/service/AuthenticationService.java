package br.ufba.veridt.identity.internal.service;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import br.ufba.veridt.identity.internal.audit.IdentityAuditOperation;
import br.ufba.veridt.identity.internal.domain.User;
import br.ufba.veridt.identity.internal.dto.LoginRequest;
import br.ufba.veridt.identity.internal.exception.EmailNotConfirmedException;
import br.ufba.veridt.identity.internal.exception.InvalidCredentialsException;
import br.ufba.veridt.identity.internal.repository.UserRepository;
import br.ufba.veridt.shared.audit.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Login e logout (REQ 03 e 04), com sessão guardada no banco pelo Spring Session e registro na auditoria. */
@Service
public class AuthenticationService {

	private static final String CREDENCIAIS_INVALIDAS = "CREDENCIAIS_INVALIDAS";
	private static final String EMAIL_NAO_CONFIRMADO = "EMAIL_NAO_CONFIRMADO";

	private final AuthenticationManager authenticationManager;
	private final SessionAuthenticationStrategy sessionStrategy;
	private final SecurityContextRepository contextRepository;
	private final LogoutHandler logoutHandler;
	private final UserRepository users;
	private final AuditLog auditLog;

	public AuthenticationService(AuthenticationManager authenticationManager,
			SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository,
			LogoutHandler logoutHandler, UserRepository users, AuditLog auditLog) {
		this.authenticationManager = authenticationManager;
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
		this.logoutHandler = logoutHandler;
		this.users = users;
		this.auditLog = auditLog;
	}

	public void login(LoginRequest credentials, HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = authenticate(credentials);
		requireConfirmedEmail(userId(authentication));
		// Troca o ID da sessão e o token CSRF, impedindo a fixação de sessão.
		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
		auditLog.recordSuccess(IdentityAuditOperation.LOGIN, userId(authentication));
	}

	public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		auditLog.recordSuccess(IdentityAuditOperation.LOGOUT, userId(authentication));
		logoutHandler.logout(request, response, authentication);
	}

	private Authentication authenticate(LoginRequest credentials) {
		try {
			return authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(credentials.email(), credentials.password()));
		}
		catch (AuthenticationException ex) {
			// A auditoria guarda de quem é a conta atacada, se existir; o e-mail digitado nunca é gravado. A resposta
			// ao cliente é a mesma nos dois casos.
			UUID target = users.findByEmail(User.normalizeEmail(credentials.email())).map(User::getId).orElse(null);
			auditLog.recordFailure(IdentityAuditOperation.LOGIN, target, CREDENCIAIS_INVALIDAS);
			throw new InvalidCredentialsException();
		}
	}

	/**
	 * Conferido depois da senha, e não pelo {@code UserDetails.isEnabled()}: o Spring testa esse campo antes da senha,
	 * e a resposta revelaria a conta a quem não a conhece. Nenhuma sessão é criada.
	 */
	private void requireConfirmedEmail(UUID userId) {
		boolean confirmed = users.findById(userId).map(User::isEmailConfirmed).orElse(false);
		if (!confirmed) {
			auditLog.recordFailure(IdentityAuditOperation.LOGIN, userId, EMAIL_NAO_CONFIRMADO);
			throw new EmailNotConfirmedException();
		}
	}

	/** O nome do principal é o UUID do usuário (ver {@code VeridtUserDetailsService}). */
	private static UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

}
