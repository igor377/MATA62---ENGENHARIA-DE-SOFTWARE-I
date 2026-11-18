package br.ufba.veridt.credits.internal.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufba.veridt.credits.internal.dto.CreditPackageResponse;
import br.ufba.veridt.credits.internal.mapper.CreditsMapper;
import br.ufba.veridt.credits.internal.repository.CreditPackageRepository;

/** Pacotes à venda (CRE-01). */
@Service
public class PackageService {

	private final CreditPackageRepository packages;
	private final CreditsMapper mapper;

	public PackageService(CreditPackageRepository packages, CreditsMapper mapper) {
		this.packages = packages;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public List<CreditPackageResponse> listActive() {
		return packages.findByActiveTrueOrderByPositionAsc().stream().map(mapper::toResponse).toList();
	}

}
