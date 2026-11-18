package br.ufba.veridt.credits.internal.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import br.ufba.veridt.credits.internal.domain.BillingInfo;
import br.ufba.veridt.credits.internal.domain.CreditPackage;
import br.ufba.veridt.credits.internal.domain.Purchase;
import br.ufba.veridt.credits.internal.dto.BillingInfoRequest;
import br.ufba.veridt.credits.internal.dto.CreditPackageResponse;
import br.ufba.veridt.credits.internal.dto.PaymentPageResponse;
import br.ufba.veridt.credits.internal.dto.PaymentResponse;
import br.ufba.veridt.credits.internal.dto.PixResponse;

@Component
public class CreditsMapper {

	public CreditPackageResponse toResponse(CreditPackage creditPackage) {
		return new CreditPackageResponse(creditPackage.getCode(), creditPackage.getName(), creditPackage.getCredits(),
				creditPackage.getPricePerCredit(), creditPackage.totalPrice(), creditPackage.getBenefits());
	}

	public BillingInfo toBilling(BillingInfoRequest request) {
		return new BillingInfo(request.fullName(), request.email(), request.phone(), request.cpf(), request.cep(),
				request.street(), request.number(), request.complement(), request.neighborhood(), request.city(),
				request.state());
	}

	/** Os dados de faturamento ficam de fora da resposta. */
	public PaymentResponse toResponse(Purchase purchase) {
		PixResponse pix = purchase.getPixCopyPaste() == null ? null
				: new PixResponse(purchase.getPixQrCodeImage(), purchase.getPixCopyPaste(), purchase.getPixExpiresAt());
		return new PaymentResponse(purchase.getId(), purchase.getPackageCode(), purchase.getPackageName(),
				purchase.getMethod(), purchase.getStatus(), purchase.getTotalPrice(), purchase.getCredits(),
				purchase.getCreatedAt(), purchase.getDecidedAt(), pix, purchase.getCheckoutUrl());
	}

	public PaymentPageResponse toPage(Page<Purchase> page) {
		return new PaymentPageResponse(page.getContent().stream().map(this::toResponse).toList(), page.getNumber(),
				page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

}
