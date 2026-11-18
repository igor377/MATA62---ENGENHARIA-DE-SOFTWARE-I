package br.ufba.veridt.credits.internal.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.credits.internal.dto.PaymentResponse;
import br.ufba.veridt.credits.internal.dto.PaymentSimulationRequest;
import br.ufba.veridt.credits.internal.service.PaymentConfirmationService;
import jakarta.validation.Valid;

/**
 * Só para desenvolvimento, enquanto não há provedor: aprova ou recusa o próprio pedido pelo mesmo serviço que o
 * webhook usará. Só existe com {@code veridt.payment.simulation-enabled=true}; nunca ligue em produção, porque
 * qualquer usuário conseguiria se dar créditos.
 */
@RestController
@RequestMapping("/api/credits/payments")
@ConditionalOnProperty(name = "veridt.payment.simulation-enabled", havingValue = "true")
public class PaymentSimulationController {

	private final PaymentConfirmationService confirmationService;

	public PaymentSimulationController(PaymentConfirmationService confirmationService) {
		this.confirmationService = confirmationService;
	}

	@PostMapping("/{id}/simulation")
	public PaymentResponse simulate(Authentication authentication, @PathVariable UUID id,
			@Valid @RequestBody PaymentSimulationRequest request) {
		return confirmationService.simulate(CurrentUser.id(authentication), id, request.approved());
	}

}
