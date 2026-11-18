package br.ufba.veridt.credits.internal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param provider provedor de pagamento; só existe {@code fake} até o CRE-04
 * @param simulationEnabled liga a rota que aprova ou recusa o próprio pedido sem provedor. Só para desenvolvimento:
 * qualquer usuário conseguiria se dar créditos
 * @param fakeReturnUrl página do frontend para onde o checkout falso leva, seguida do ID do pedido
 * @param pixValidity validade da cobrança Pix
 */
@Validated
@ConfigurationProperties("veridt.payment")
public record PaymentProperties(@DefaultValue("fake") @NotBlank String provider,
		@DefaultValue("false") boolean simulationEnabled,
		@DefaultValue("http://localhost:3000/creditos/pagamento") @NotBlank String fakeReturnUrl,
		@DefaultValue("30m") @NotNull Duration pixValidity) {
}
