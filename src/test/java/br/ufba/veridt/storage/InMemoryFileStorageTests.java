package br.ufba.veridt.storage;

class InMemoryFileStorageTests extends FileStorageContractTests {

	@Override
	protected FileStorage createStorage() {
		return new InMemoryFileStorage();
	}

}
