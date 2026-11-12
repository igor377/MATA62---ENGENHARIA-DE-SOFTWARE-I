package br.ufba.veridt.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação falsa para testes de outros módulos. Segue o mesmo contrato da implementação real (veja
 * {@link FileStorageContractTests}), mas guarda os arquivos em memória.
 */
public class InMemoryFileStorage implements FileStorage {

	private record Entry(StoredFile metadata, byte[] content) {
	}

	private final Map<String, Entry> files = new ConcurrentHashMap<>();

	@Override
	public StoredFile store(String key, InputStream content) {
		StorageKeys.requireValid(key);
		if (files.containsKey(key)) {
			throw new FileAlreadyStoredException(key);
		}
		MessageDigest digest = sha256();
		byte[] bytes;
		try (DigestInputStream input = new DigestInputStream(content, digest)) {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			input.transferTo(buffer);
			bytes = buffer.toByteArray();
		}
		catch (IOException ex) {
			// Nada foi gravado: o mapa só recebe o arquivo depois de lido por completo.
			throw new UncheckedIOException("Falha ao gravar o arquivo " + key, ex);
		}
		StoredFile stored = new StoredFile(key, bytes.length, HexFormat.of().formatHex(digest.digest()));
		if (files.putIfAbsent(key, new Entry(stored, bytes)) != null) {
			throw new FileAlreadyStoredException(key);
		}
		return stored;
	}

	@Override
	public InputStream open(String key) {
		StorageKeys.requireValid(key);
		Entry entry = files.get(key);
		if (entry == null) {
			throw new StoredFileNotFoundException(key);
		}
		return new ByteArrayInputStream(entry.content());
	}

	@Override
	public Optional<StoredFile> find(String key) {
		StorageKeys.requireValid(key);
		return Optional.ofNullable(files.get(key)).map(Entry::metadata);
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
