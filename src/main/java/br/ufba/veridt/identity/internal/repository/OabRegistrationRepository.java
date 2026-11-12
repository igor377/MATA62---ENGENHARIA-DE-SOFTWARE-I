package br.ufba.veridt.identity.internal.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufba.veridt.identity.internal.domain.OabRegistration;
import br.ufba.veridt.identity.internal.domain.OabRegistrationType;
import br.ufba.veridt.identity.internal.domain.Uf;

public interface OabRegistrationRepository extends JpaRepository<OabRegistration, UUID> {

	/** Recebe o número já normalizado ({@link OabRegistration#normalizeNumber}). */
	boolean existsByNumberAndStateAndType(String number, Uf state, OabRegistrationType type);

	List<OabRegistration> findByUserId(UUID userId);

}
