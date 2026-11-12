package br.ufba.veridt.storage.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.ufba.veridt.storage.FileAlreadyStoredException;
import br.ufba.veridt.storage.FileStorage;
import br.ufba.veridt.storage.StorageKeys;
import br.ufba.veridt.storage.StoredFile;
import br.ufba.veridt.storage.StoredFileNotFoundException;
import br.ufba.veridt.storage.internal.config.S3StorageProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@link FileStorage} sobre a API S3. O conteúdo passa por um arquivo temporário enquanto o SHA-256 é calculado; depois
 * é enviado num único {@code PUT} condicional ({@code If-None-Match: *}) com o hash como metadado. O próprio servidor
 * recusa a gravação se a chave já existir, então não há sobrescrita nem em gravações simultâneas.
 */
@Component
public class S3FileStorage implements FileStorage {

	private static final String SHA256_METADATA = "sha256";
	private static final int PRECONDITION_FAILED = 412;
	private static final int NOT_FOUND = 404;

	private final S3Client s3;
	private final String bucket;
	private volatile boolean bucketReady;

	public S3FileStorage(S3Client s3, S3StorageProperties properties) {
		this.s3 = s3;
		this.bucket = properties.bucket();
	}

	@Override
	public StoredFile store(String key, InputStream content) {
		StorageKeys.requireValid(key);
		ensureBucket();
		if (find(key).isPresent()) {
			throw new FileAlreadyStoredException(key);
		}
		Path temp = createTempFile();
		try {
			MessageDigest digest = sha256();
			try (DigestInputStream input = new DigestInputStream(content, digest);
					OutputStream output = Files.newOutputStream(temp)) {
				input.transferTo(output);
			}
			String sha256 = HexFormat.of().formatHex(digest.digest());
			long size = Files.size(temp);
			s3.putObject(request -> request.bucket(bucket)
				.key(key)
				.contentLength(size)
				.ifNoneMatch("*")
				.metadata(Map.of(SHA256_METADATA, sha256)), RequestBody.fromFile(temp));
			return new StoredFile(key, size, sha256);
		}
		catch (IOException ex) {
			// Nada chegou ao servidor: o envio só começa depois que o stream foi lido por completo.
			throw new UncheckedIOException("Falha ao ler o conteúdo do arquivo " + key, ex);
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == PRECONDITION_FAILED) {
				throw new FileAlreadyStoredException(key);
			}
			throw failure("gravar", key, ex);
		}
		catch (SdkException ex) {
			throw failure("gravar", key, ex);
		}
		finally {
			deleteQuietly(temp);
		}
	}

	@Override
	public InputStream open(String key) {
		StorageKeys.requireValid(key);
		try {
			return s3.getObject(request -> request.bucket(bucket).key(key));
		}
		catch (NoSuchKeyException ex) {
			throw new StoredFileNotFoundException(key);
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == NOT_FOUND) {
				throw new StoredFileNotFoundException(key);
			}
			throw failure("ler", key, ex);
		}
		catch (SdkException ex) {
			throw failure("ler", key, ex);
		}
	}

	@Override
	public Optional<StoredFile> find(String key) {
		StorageKeys.requireValid(key);
		try {
			HeadObjectResponse head = s3.headObject(request -> request.bucket(bucket).key(key));
			return Optional.of(new StoredFile(key, head.contentLength(), head.metadata().get(SHA256_METADATA)));
		}
		catch (NoSuchKeyException ex) {
			return Optional.empty();
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == NOT_FOUND) {
				return Optional.empty();
			}
			throw failure("consultar", key, ex);
		}
		catch (SdkException ex) {
			throw failure("consultar", key, ex);
		}
	}

	/** Cria o bucket na primeira gravação, para a aplicação subir mesmo sem o servidor S3 disponível. */
	private void ensureBucket() {
		if (bucketReady) {
			return;
		}
		synchronized (this) {
			if (bucketReady) {
				return;
			}
			try {
				s3.headBucket(request -> request.bucket(bucket));
			}
			catch (S3Exception ex) {
				if (ex.statusCode() != NOT_FOUND) {
					throw failure("acessar o bucket", bucket, ex);
				}
				s3.createBucket(request -> request.bucket(bucket));
			}
			catch (SdkException ex) {
				throw failure("acessar o bucket", bucket, ex);
			}
			bucketReady = true;
		}
	}

	private static Path createTempFile() {
		try {
			return Files.createTempFile("veridt-storage-", ".part");
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Não foi possível criar o arquivo temporário", ex);
		}
	}

	private static void deleteQuietly(Path temp) {
		try {
			Files.deleteIfExists(temp);
		}
		catch (IOException ex) {
			// O arquivo temporário não tem dado sensível além do conteúdo, que já está no armazenamento ou foi descartado.
		}
	}

	private static UncheckedIOException failure(String operation, String key, Exception cause) {
		return new UncheckedIOException(new IOException("Falha ao " + operation + " " + key + " no armazenamento", cause));
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível", ex);
		}
	}

}
