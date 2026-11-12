package br.ufba.veridt.identity.internal.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "nome_completo", nullable = false, length = 150)
	private String fullName;

	@Column(nullable = false, length = 254, unique = true)
	private String email;

	@Column(name = "senha_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 11, unique = true)
	private String cpf;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 20)
	private UserType type;

	@Column(name = "criado_em", nullable = false)
	private Instant createdAt;

	protected User() {
	}

	public User(String fullName, String email, String passwordHash, String cpf, UserType type) {
		this.fullName = fullName.strip();
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.cpf = normalizeCpf(cpf);
		this.type = type;
		this.createdAt = Instant.now();
	}

	/** E-mail é comparado sem diferenciar maiúsculas: grava e busca sempre em minúsculas. */
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	/** CPF é aceito com ou sem máscara e gravado só com os dígitos. */
	public static String normalizeCpf(String cpf) {
		return cpf.replaceAll("\\D", "");
	}

	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
	}

	public UUID getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getCpf() {
		return cpf;
	}

	public UserType getType() {
		return type;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
