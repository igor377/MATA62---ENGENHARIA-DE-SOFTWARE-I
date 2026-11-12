package br.ufba.veridt.storage.internal.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @param endpoint endereço da API S3 (SeaweedFS em desenvolvimento)
 * @param region região exigida pela assinatura S3; o SeaweedFS aceita qualquer valor
 * @param accessKey credencial de acesso
 * @param secretKey segredo da credencial
 * @param bucket bucket dos arquivos, criado na primeira gravação se não existir
 */
@Validated
@ConfigurationProperties("veridt.storage.s3")
public record S3StorageProperties(@NotNull URI endpoint, @NotBlank String region, @NotBlank String accessKey,
		@NotBlank String secretKey,
		@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", message = "nome de bucket S3 inválido") String bucket) {

	/** Esconde as credenciais em logs e mensagens de erro. */
	@Override
	public String toString() {
		return "S3StorageProperties[endpoint=" + endpoint + ", bucket=" + bucket + "]";
	}

}
