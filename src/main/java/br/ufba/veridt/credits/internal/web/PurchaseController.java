package br.ufba.veridt.credits.internal.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.credits.internal.dto.PaymentPageResponse;
import br.ufba.veridt.credits.internal.dto.PaymentResponse;
import br.ufba.veridt.credits.internal.dto.PurchaseRequest;
import br.ufba.veridt.credits.internal.service.PurchaseResult;
import br.ufba.veridt.credits.internal.service.PurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Compra de créditos. O cabeçalho {@code Idempotency-Key} é obrigatório: repetir a requisição com a mesma chave
 * devolve o pedido já criado (200), sem criar outro.
 */
@RestController
@RequestMapping("/api/credits")
@Validated
public class PurchaseController {

	private final PurchaseService purchaseService;

	public PurchaseController(PurchaseService purchaseService) {
		this.purchaseService = purchaseService;
	}

	@PostMapping("/purchases")
	public ResponseEntity<PaymentResponse> purchase(Authentication authentication,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String idempotencyKey,
			@Valid @RequestBody PurchaseRequest request) {
		PurchaseResult result = purchaseService.create(CurrentUser.id(authentication), idempotencyKey, request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
	}

	@GetMapping("/payments")
	public PaymentPageResponse payments(Authentication authentication,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return purchaseService.list(CurrentUser.id(authentication), page, size);
	}

	@GetMapping("/payments/{id}")
	public PaymentResponse payment(Authentication authentication, @PathVariable UUID id) {
		return purchaseService.find(CurrentUser.id(authentication), id);
	}

}
