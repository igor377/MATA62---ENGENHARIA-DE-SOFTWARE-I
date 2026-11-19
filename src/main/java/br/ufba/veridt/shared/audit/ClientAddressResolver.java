package br.ufba.veridt.shared.audit;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP real do cliente. O {@code X-Forwarded-For} só é lido quando a conexão vem de um proxy confiável, e da direita
 * para a esquerda: o nginx do frontend acrescenta o IP real ao final do valor recebido, então tudo à esquerda pode ter
 * sido forjado pelo cliente. O primeiro endereço que não é de proxy confiável é o cliente.
 */
public class ClientAddressResolver {

	static final String FORWARDED_FOR = "X-Forwarded-For";

	private final List<IpAddressMatcher> trustedProxies;

	public ClientAddressResolver(List<String> trustedProxies) {
		this.trustedProxies = trustedProxies.stream().map(IpAddressMatcher::new).toList();
	}

	public String resolve(HttpServletRequest request) {
		String client = normalize(request.getRemoteAddr());
		if (client == null || !isTrusted(client)) {
			return client;
		}
		for (String hop : forwardedHopsFromRight(request)) {
			String address = normalize(hop);
			if (address == null) {
				// Valor inválido: não dá para confiar no que vem à esquerda, então fica o último endereço válido.
				return client;
			}
			client = address;
			if (!isTrusted(address)) {
				return address;
			}
		}
		return client;
	}

	private boolean isTrusted(String address) {
		return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
	}

	private static List<String> forwardedHopsFromRight(HttpServletRequest request) {
		List<String> hops = new ArrayList<>();
		for (String header : Collections.list(request.getHeaders(FORWARDED_FOR))) {
			for (String hop : header.split(",")) {
				hops.add(hop.strip());
			}
		}
		Collections.reverse(hops);
		return hops;
	}

	/** Aceita só IPs literais, sem consulta DNS, e devolve a forma normalizada. */
	static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return InetAddress.ofLiteral(value.strip()).getHostAddress();
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
