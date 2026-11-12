package br.ufba.veridt.notification.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * @param from remetente dos e-mails, definido por {@code MAIL_FROM}
 * @param threads envios simultâneos; cada um ocupa uma conexão com o banco enquanto espera o SMTP
 */
@Validated
@ConfigurationProperties("veridt.mail")
public record NotificationProperties(@NotBlank @Email String from, @DefaultValue("2") @Min(1) int threads) {
}
