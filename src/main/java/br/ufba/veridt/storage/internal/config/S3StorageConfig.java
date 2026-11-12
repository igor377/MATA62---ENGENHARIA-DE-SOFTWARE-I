package br.ufba.veridt.storage.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfig {

	/** Não abre conexão ao subir: a aplicação inicia mesmo sem o servidor S3 disponível. */
	@Bean(destroyMethod = "close")
	S3Client s3Client(S3StorageProperties properties) {
		return createClient(properties);
	}

	public static S3Client createClient(S3StorageProperties properties) {
		return S3Client.builder()
			.endpointOverride(properties.endpoint())
			.region(Region.of(properties.region()))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
			// Servidores S3 compatíveis usam o bucket no caminho, não no nome do host.
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			// O SHA-256 próprio já garante a integridade; somar checksums opcionais do SDK quebra servidores compatíveis.
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.build();
	}

}
