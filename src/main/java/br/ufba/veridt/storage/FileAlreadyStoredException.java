package br.ufba.veridt.storage;

import br.ufba.veridt.shared.error.exception.ConflictException;

/** Um arquivo gravado nunca é sobrescrito, para o conteúdo e o hash registrados continuarem válidos. */
public class FileAlreadyStoredException extends ConflictException {

	public FileAlreadyStoredException(String key) {
		super("ARQUIVO_JA_EXISTE", "Já existe um arquivo com a chave: " + key);
	}

}
