package br.ufba.veridt.identity.internal.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Nega tudo por padrão e libera só o health check e as rotas públicas de autenticação. Login e logout ficam no
 * {@code AuthController}, então os filtros padrão do Spring estão desligados. O CSRF usa o modelo para SPA: cookie
 * {@code XSRF-TOKEN} lido pelo frontend e devolvido no cabeçalho {@code X-XSRF-TOKEN}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
public class SecurityConfig {

	// Argon2id com a configuração mínima recomendada pelo OWASP: 19 MiB e 2 iterações.
	private static final int ARGON2_SALT_BYTES = 16;
	private static final int ARGON2_HASH_BYTES = 32;
	private static final int ARGON2_PARALLELISM = 1;
	private static final int ARGON2_MEMORY_KIB = 19456;
	private static final int ARGON2_ITERATIONS = 2;

	@Bean
	PasswordEncoder passwordEncoder() {
		return new Argon2PasswordEncoder(ARGON2_SALT_BYTES, ARGON2_HASH_BYTES, ARGON2_PARALLELISM, ARGON2_MEMORY_KIB,
				ARGON2_ITERATIONS);
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	@Bean
	LogoutHandler logoutHandler(CsrfTokenRepository csrfTokenRepository) {
		return new CompositeLogoutHandler(new SecurityContextLogoutHandler(), new CsrfLogoutHandler(csrfTokenRepository));
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errors,
			SecurityContextRepository securityContextRepository) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
			.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
			.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/password-reset",
					"/api/auth/password-reset/confirm").permitAll()
			.anyRequest().authenticated())
			.csrf(csrf -> csrf.spa())
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			// Sem redirecionamento após o login, guardar a requisição negada só criaria uma sessão por visitante.
			.requestCache(cache -> cache.disable())
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint((request, response, exception) -> errors.write(response,
						HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Autenticação necessária."))
				.accessDeniedHandler((request, response, exception) -> errors.write(response, HttpStatus.FORBIDDEN,
						"ACESSO_NEGADO", "Acesso negado.")));
		return http.build();
	}

}
