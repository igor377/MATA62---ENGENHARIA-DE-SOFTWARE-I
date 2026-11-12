package br.ufba.veridt.identity.internal.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufba.veridt.identity.internal.domain.User;
import br.ufba.veridt.identity.internal.repository.UserRepository;

/**
 * Login por e-mail. O nome do principal é o UUID do usuário, então {@code Authentication.getName()} devolve o UUID e
 * os outros módulos identificam quem está logado sem depender de {@code identity}.
 */
@Service
public class VeridtUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	public VeridtUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		User user = users.findByEmail(User.normalizeEmail(email))
			// O Spring converte em BadCredentialsException: a resposta não revela se o e-mail existe.
			.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
		return org.springframework.security.core.userdetails.User.withUsername(user.getId().toString())
			.password(user.getPasswordHash())
			.roles("USER", user.getType().name())
			.build();
	}

}
