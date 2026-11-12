package br.ufba.veridt.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contrato de {@link FileStorage}. Toda implementação, inclusive as reais do STO-02, deve estender esta classe e
 * passar em todos os casos.
 */
public abstract class FileStorageContractTests {

	private static final String SHA256_VAZIO = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

	private FileStorage storage;

	protected abstract FileStorage createStorage();

	@BeforeEach
	void criarStorage() {
		storage = createStorage();
	}

	@Test
	void gravaETamanhoESha256ConferemComOConteudo() throws Exception {
		byte[] conteudo = bytes("evidência digital");

		StoredFile gravado = storage.store("registros/1/captura.png", new ByteArrayInputStream(conteudo));

		assertThat(gravado.key()).isEqualTo("registros/1/captura.png");
		assertThat(gravado.size()).isEqualTo(conteudo.length);
		assertThat(gravado.sha256()).isEqualTo(sha256(conteudo));
	}

	@Test
	void leDeVoltaExatamenteOsMesmosBytes() throws Exception {
		byte[] conteudo = new byte[300_000];
		new java.util.Random(42).nextBytes(conteudo);
		storage.store("registros/1/parte-0001.ts", new ByteArrayInputStream(conteudo));

		try (InputStream lido = storage.open("registros/1/parte-0001.ts")) {
			assertThat(lido.readAllBytes()).isEqualTo(conteudo);
		}
	}

	@Test
	void findDevolveOsMetadadosOuVazio() {
		StoredFile gravado = storage.store("registros/1/a.txt", new ByteArrayInputStream(bytes("a")));

		assertThat(storage.find("registros/1/a.txt")).contains(gravado);
		assertThat(storage.find("registros/1/inexistente.txt")).isEmpty();
	}

	@Test
	void naoSobrescreveArquivoExistente() throws Exception {
		storage.store("registros/1/a.txt", new ByteArrayInputStream(bytes("original")));

		assertThatThrownBy(() -> storage.store("registros/1/a.txt", new ByteArrayInputStream(bytes("outro"))))
			.isInstanceOf(FileAlreadyStoredException.class)
			.hasMessageContaining("registros/1/a.txt");
		try (InputStream lido = storage.open("registros/1/a.txt")) {
			assertThat(lido.readAllBytes()).isEqualTo(bytes("original"));
		}
		assertThat(storage.find("registros/1/a.txt").orElseThrow().sha256()).isEqualTo(sha256(bytes("original")));
	}

	@Test
	void gravacoesSimultaneasNaMesmaChaveSoUmaVence() throws Exception {
		CountDownLatch largada = new CountDownLatch(1);
		Callable<Boolean> gravar = () -> {
			largada.await();
			try {
				storage.store("registros/1/disputa.bin", new ByteArrayInputStream(new byte[100_000]));
				return true;
			}
			catch (FileAlreadyStoredException ex) {
				return false;
			}
		};
		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			List<Future<Boolean>> resultados = List.of(executor.submit(gravar), executor.submit(gravar),
					executor.submit(gravar), executor.submit(gravar));
			largada.countDown();
			long sucessos = 0;
			for (Future<Boolean> resultado : resultados) {
				sucessos += resultado.get() ? 1 : 0;
			}
			assertThat(sucessos).isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void abrirChaveInexistenteGeraErroClaro() {
		assertThatThrownBy(() -> storage.open("registros/1/inexistente.txt"))
			.isInstanceOf(StoredFileNotFoundException.class)
			.hasMessageContaining("registros/1/inexistente.txt");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "../segredo", "registros/../segredo", "/absoluto", "Maiuscula.txt", "a//b",
			"registros/1/", ".oculto", "com espaco", "c:\\windows" })
	void chaveInvalidaERecusada(String chave) {
		assertThatThrownBy(() -> storage.store(chave, new ByteArrayInputStream(bytes("x"))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.open(chave)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.find(chave)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void chaveMaiorQueOLimiteERecusada() {
		String longa = "a".repeat(StorageKeys.MAX_LENGTH + 1);

		assertThatThrownBy(() -> storage.store(longa, new ByteArrayInputStream(bytes("x"))))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void falhaNoMeioDoStreamNaoDeixaArquivoParcial() {
		InputStream quebra = new InputStream() {
			private int lidos;

			@Override
			public int read() throws IOException {
				if (lidos++ > 1000) {
					throw new IOException("conexão perdida");
				}
				return 'x';
			}
		};

		assertThatThrownBy(() -> storage.store("registros/1/parcial.bin", quebra))
			.isInstanceOf(UncheckedIOException.class);
		assertThat(storage.find("registros/1/parcial.bin")).isEmpty();
		storage.store("registros/1/parcial.bin", new ByteArrayInputStream(bytes("completo")));
	}

	@Test
	void arquivoVazioEAceito() {
		StoredFile gravado = storage.store("registros/1/vazio.txt", InputStream.nullInputStream());

		assertThat(gravado.size()).isZero();
		assertThat(gravado.sha256()).isEqualTo(SHA256_VAZIO);
	}

	private static byte[] bytes(String texto) {
		return texto.getBytes(StandardCharsets.UTF_8);
	}

	private static String sha256(byte[] conteudo) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
	}

}
