package br.ufba.veridt.storage;

import java.util.regex.Pattern;

/**
 * Regras das chaves de arquivo, iguais para toda implementação. Segmentos separados por {@code /}, cada um começando
 * por letra minúscula ou dígito e seguido de letras minúsculas, dígitos, {@code _}, {@code .} ou {@code -}. Como nenhum
 * segmento pode começar por ponto, {@code ..} e caminhos absolutos são impossíveis, o que impede path traversal na
 * implementação em disco.
 */
public final class StorageKeys {

	public static final int MAX_LENGTH = 255;

	private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_.-]*(/[a-z0-9][a-z0-9_.-]*)*");

	private StorageKeys() {
	}

	public static String requireValid(String key) {
		if (key == null || key.length() > MAX_LENGTH || !VALID.matcher(key).matches()) {
			throw new IllegalArgumentException("Chave de arquivo inválida: " + key);
		}
		return key;
	}

}
