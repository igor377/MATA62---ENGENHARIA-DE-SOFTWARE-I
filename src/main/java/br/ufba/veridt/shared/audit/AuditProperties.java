package br.ufba.veridt.shared.audit;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param trustedProxies IPs ou CIDRs dos proxies cujo {@code X-Forwarded-For} é aceito. Vazio: não confia em ninguém e
 * usa sempre o endereço remoto.
 * @param verificationInterval intervalo da verificação da cadeia de hashes
 */
@ConfigurationProperties("veridt.audit")
public record AuditProperties(List<String> trustedProxies, Duration verificationInterval) {

	public AuditProperties {
		trustedProxies = trustedProxies == null ? List.of()
				: trustedProxies.stream().map(String::strip).filter(proxy -> !proxy.isEmpty()).toList();
		verificationInterval = verificationInterval == null ? Duration.ofHours(6) : verificationInterval;
	}

}
