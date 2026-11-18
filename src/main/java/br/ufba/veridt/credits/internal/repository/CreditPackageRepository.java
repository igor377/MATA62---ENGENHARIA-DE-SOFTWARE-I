package br.ufba.veridt.credits.internal.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufba.veridt.credits.internal.domain.CreditPackage;

public interface CreditPackageRepository extends JpaRepository<CreditPackage, UUID> {

	List<CreditPackage> findByActiveTrueOrderByPositionAsc();

	Optional<CreditPackage> findByCodeAndActiveTrue(String code);

}
