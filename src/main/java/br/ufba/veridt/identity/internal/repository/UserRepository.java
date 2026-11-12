package br.ufba.veridt.identity.internal.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufba.veridt.identity.internal.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** Recebe o e-mail já normalizado ({@link User#normalizeEmail}). */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByCpf(String cpf);

}
