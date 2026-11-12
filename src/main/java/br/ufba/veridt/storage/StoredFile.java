package br.ufba.veridt.storage;

/**
 * Arquivo gravado.
 *
 * @param key chave escolhida por quem gravou
 * @param size tamanho em bytes
 * @param sha256 hash SHA-256 do conteúdo, em hexadecimal minúsculo, calculado durante a gravação
 */
public record StoredFile(String key, long size, String sha256) {
}
