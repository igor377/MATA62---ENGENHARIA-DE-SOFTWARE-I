package br.ufba.veridt.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTests {

	private static final String PROXY = "172.28.0.10";

	private final ClientAddressResolver semProxyConfiavel = new ClientAddressResolver(List.of());

	private final ClientAddressResolver comProxyDoFront = new ClientAddressResolver(List.of(PROXY, "10.0.0.0/8"));

	@Test
	void clienteNaoConfiavelNaoForjaOIpPeloXForwardedFor() {
		MockHttpServletRequest request = request("203.0.113.5", "1.2.3.4");

		assertThat(semProxyConfiavel.resolve(request)).isEqualTo("203.0.113.5");
		assertThat(comProxyDoFront.resolve(request)).isEqualTo("203.0.113.5");
	}

	@Test
	void proxyConfiavelInformaOIpReal() {
		assertThat(comProxyDoFront.resolve(request(PROXY, "198.51.100.7"))).isEqualTo("198.51.100.7");
	}

	@Test
	void ipForjadoAEsquerdaDoQueONginxAcrescentaEIgnorado() {
		// O cliente mandou "1.2.3.4"; o nginx acrescentou o IP real ao final.
		assertThat(comProxyDoFront.resolve(request(PROXY, "1.2.3.4, 198.51.100.7"))).isEqualTo("198.51.100.7");
	}

	@Test
	void pulaVariosProxiesConfiaveisDaDireitaParaAEsquerda() {
		assertThat(comProxyDoFront.resolve(request(PROXY, "1.2.3.4, 198.51.100.7, 10.1.2.3")))
			.isEqualTo("198.51.100.7");
	}

	@Test
	void valorInvalidoNoCabecalhoFicaComOUltimoEnderecoValido() {
		assertThat(comProxyDoFront.resolve(request(PROXY, "nao-e-ip"))).isEqualTo(PROXY);
		assertThat(comProxyDoFront.resolve(request(PROXY, "nao-e-ip, 10.1.2.3"))).isEqualTo("10.1.2.3");
	}

	@Test
	void proxyConfiavelSemCabecalhoUsaOProprioEndereco() {
		assertThat(comProxyDoFront.resolve(request(PROXY, null))).isEqualTo(PROXY);
	}

	@Test
	void naoFazConsultaDnsParaNomes() {
		assertThat(ClientAddressResolver.normalize("localhost")).isNull();
		assertThat(ClientAddressResolver.normalize("::1")).isEqualTo("0:0:0:0:0:0:0:1");
	}

	private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		if (forwardedFor != null) {
			request.addHeader(ClientAddressResolver.FORWARDED_FOR, forwardedFor);
		}
		return request;
	}

}
