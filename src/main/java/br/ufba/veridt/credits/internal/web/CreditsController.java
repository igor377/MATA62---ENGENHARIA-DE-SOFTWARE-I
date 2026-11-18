package br.ufba.veridt.credits.internal.web;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.credits.CreditLedger;
import br.ufba.veridt.credits.internal.dto.BalanceResponse;
import br.ufba.veridt.credits.internal.dto.CreditPackageResponse;
import br.ufba.veridt.credits.internal.service.PackageService;

@RestController
@RequestMapping("/api/credits")
public class CreditsController {

	private final PackageService packageService;
	private final CreditLedger ledger;

	public CreditsController(PackageService packageService, CreditLedger ledger) {
		this.packageService = packageService;
		this.ledger = ledger;
	}

	@GetMapping("/packages")
	public List<CreditPackageResponse> packages() {
		return packageService.listActive();
	}

	@GetMapping("/balance")
	public BalanceResponse balance(Authentication authentication) {
		return new BalanceResponse(ledger.balance(CurrentUser.id(authentication)));
	}

}
