package br.ufba.veridt.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "inscricao_oab",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_oab_numero_uf_tipo",
				columnNames = { "numero", "uf", "tipo" }))
public class OabRegistration {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "usuario_id", nullable = false)
	private UUID userId;

	@Column(name = "numero", nullable = false, length = 6)
	private String number;

	@Enumerated(EnumType.STRING)
	@Column(name = "uf", nullable = false, length = 2)
	private Uf state;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 20)
	private OabRegistrationType type;

	@Column(name = "criado_em", nullable = false)
	private Instant createdAt;

	protected OabRegistration() {
	}

	public OabRegistration(UUID userId, String number, Uf state, OabRegistrationType type) {
		this.userId = userId;
		this.number = normalizeNumber(number);
		this.state = state;
		this.type = type;
		this.createdAt = Instant.now();
	}

	/** "00123" e "123" são a mesma inscrição: grava e compara sempre sem os zeros à esquerda. */
	public static String normalizeNumber(String number) {
		return number.strip().replaceFirst("^0+", "");
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getNumber() {
		return number;
	}

	public Uf getState() {
		return state;
	}

	public OabRegistrationType getType() {
		return type;
	}

}
