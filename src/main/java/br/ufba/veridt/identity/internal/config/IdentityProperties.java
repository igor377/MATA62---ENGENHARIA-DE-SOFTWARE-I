package br.ufba.veridt.identity.internal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param resetTokenValidity validade do link de recuperação de senha
 * @param resetUrl página que recebe o token; depende do frontend (P1)
 * @param passwordMinLength tamanho mínimo da senha
 * @param confirmationUrl página do frontend que recebe o token de confirmação de e-mail e o envia por {@code POST}
 * @param confirmationTokenValidity validade do link de confirmação de e-mail
 * @param confirmationResendInterval intervalo mínimo entre dois envios do link de confirmação para a mesma conta
 * @param unconfirmedAccountRetention tempo até uma conta não confirmada ser apagada, liberando e-mail e CPF
 */
@Validated
@ConfigurationProperties("veridt.identity")
public record IdentityProperties(@DefaultValue("15m") @NotNull Duration resetTokenValidity,
		@DefaultValue("http://localhost:8080/redefinir-senha") @NotBlank String resetUrl,
		@DefaultValue("8") @Min(8) int passwordMinLength,
		@DefaultValue("http://localhost:3000/confirmar-email") @NotBlank String confirmationUrl,
		@DefaultValue("24h") @NotNull Duration confirmationTokenValidity,
		@DefaultValue("60s") @NotNull Duration confirmationResendInterval,
		@DefaultValue("7d") @NotNull Duration unconfirmedAccountRetention) {
}
