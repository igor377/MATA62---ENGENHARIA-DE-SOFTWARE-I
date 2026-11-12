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
 */
@Validated
@ConfigurationProperties("veridt.identity")
public record IdentityProperties(@DefaultValue("15m") @NotNull Duration resetTokenValidity,
		@DefaultValue("http://localhost:8080/redefinir-senha") @NotBlank String resetUrl,
		@DefaultValue("8") @Min(8) int passwordMinLength) {
}
