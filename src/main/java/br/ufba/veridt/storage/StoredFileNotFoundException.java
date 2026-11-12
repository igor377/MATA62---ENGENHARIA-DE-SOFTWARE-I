package br.ufba.veridt.storage;

import br.ufba.veridt.shared.error.exception.ResourceNotFoundException;

public class StoredFileNotFoundException extends ResourceNotFoundException {

	public StoredFileNotFoundException(String key) {
		super("ARQUIVO_NAO_ENCONTRADO", "Arquivo não encontrado: " + key);
	}

}
