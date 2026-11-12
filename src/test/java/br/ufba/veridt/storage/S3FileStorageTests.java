package br.ufba.veridt.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import br.ufba.veridt.storage.internal.S3FileStorage;
import br.ufba.veridt.storage.internal.config.S3StorageConfig;
import br.ufba.veridt.storage.internal.config.S3StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;

/** O contrato inteiro contra um SeaweedFS real, a mesma imagem do Compose. Cada teste usa um bucket novo. */
class S3FileStorageTests extends FileStorageContractTests {

	private static final String ACCESS_KEY = "teste";
	private static final String SECRET_KEY = "segredo-de-teste";

	private static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>("chrislusf/seaweedfs:4.53")
		.withCommand("server", "-s3", "-dir=/data")
		.withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
		.withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
		.withExposedPorts(8333)
		// Mesma sonda do Compose: o S3 responde antes de aceitar gravações.
		.waitingFor(Wait.forSuccessfulCommand("wget -qO- \"http://$(hostname -i):9333/dir/assign\" | grep -q fid"));

	private static S3Client client;

	@BeforeAll
	static void iniciar() {
		SEAWEEDFS.start();
		client = S3StorageConfig.createClient(propriedades("nao-usado"));
	}

	@AfterAll
	static void parar() {
		client.close();
		SEAWEEDFS.stop();
	}

	@Override
	protected FileStorage createStorage() {
		return new S3FileStorage(client, propriedades("teste-" + UUID.randomUUID()));
	}

	@Test
	void hashFicaGravadoComOArquivoEOutraInstanciaOEncontra() throws Exception {
		S3StorageProperties propriedades = propriedades("teste-" + UUID.randomUUID());
		StoredFile gravado = new S3FileStorage(client, propriedades).store("registros/1/a.txt",
				new ByteArrayInputStream("conteúdo".getBytes(StandardCharsets.UTF_8)));

		try (S3Client outroCliente = S3StorageConfig.createClient(propriedades)) {
			S3FileStorage outraInstancia = new S3FileStorage(outroCliente, propriedades);
			assertThat(outraInstancia.find("registros/1/a.txt")).contains(gravado);
			try (InputStream lido = outraInstancia.open("registros/1/a.txt")) {
				assertThat(new String(lido.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("conteúdo");
			}
		}
	}

	@Test
	void arquivoGrandeEGravadoInteiro() {
		byte[] conteudo = new byte[12 * 1024 * 1024];
		new java.util.Random(7).nextBytes(conteudo);
		FileStorage storage = createStorage();

		StoredFile gravado = storage.store("registros/1/parte-0001.ts", new ByteArrayInputStream(conteudo));

		assertThat(gravado.size()).isEqualTo(conteudo.length);
		assertThat(storage.find("registros/1/parte-0001.ts")).contains(gravado);
	}

	private static S3StorageProperties propriedades(String bucket) {
		URI endpoint = URI.create("http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333));
		return new S3StorageProperties(endpoint, "us-east-1", ACCESS_KEY, SECRET_KEY, bucket);
	}

}
