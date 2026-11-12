package br.ufba.veridt.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Armazenamento de arquivos com integridade. Quem grava escolhe a chave (veja {@link StorageKeys}); uma chave nunca é
 * sobrescrita, então o conteúdo e o SHA-256 de um arquivo gravado não mudam. Falhas de leitura ou escrita são
 * lançadas como {@link java.io.UncheckedIOException}.
 */
public interface FileStorage {

	/**
	 * Grava o conteúdo lendo o stream até o fim, calculando tamanho e SHA-256 durante a leitura. Se o stream falhar no
	 * meio, nada fica gravado.
	 *
	 * @throws FileAlreadyStoredException se a chave já existir
	 * @throws IllegalArgumentException se a chave for inválida
	 */
	StoredFile store(String key, InputStream content);

	/**
	 * Abre o conteúdo para leitura. Quem chama deve fechar o stream.
	 *
	 * @throws StoredFileNotFoundException se a chave não existir
	 */
	InputStream open(String key);

	/** Metadados do arquivo, ou vazio se a chave não existir. */
	Optional<StoredFile> find(String key);

}
