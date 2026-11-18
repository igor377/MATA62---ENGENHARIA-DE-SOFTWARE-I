package br.ufba.veridt.identity.internal.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.ufba.veridt.identity.internal.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** Recebe o e-mail já normalizado ({@link User#normalizeEmail}). */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByCpf(String cpf);

	@Query("SELECT u.id FROM User u WHERE u.emailConfirmedAt IS NULL AND u.createdAt < :before")
	List<UUID> findUnconfirmedCreatedBefore(@Param("before") Instant before);

	/**
	 * Apaga só se a conta continuar sem confirmação, caso o usuário confirme entre a consulta e a exclusão. As
	 * inscrições na OAB e os tokens saem em cascata no banco.
	 */
	@Modifying
	@Query("DELETE FROM User u WHERE u.id = :id AND u.emailConfirmedAt IS NULL")
	int deleteIfUnconfirmed(@Param("id") UUID id);

}
