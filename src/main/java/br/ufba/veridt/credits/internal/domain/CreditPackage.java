package br.ufba.veridt.credits.internal.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pacote de créditos à venda (CRE-01). Criado e alterado só por migration. */
@Entity
@Table(name = "pacote_credito")
public class CreditPackage {

	@Id
	private UUID id;

	@Column(name = "codigo", nullable = false, length = 20, unique = true)
	private String code;

	@Column(name = "nome", nullable = false, length = 60)
	private String name;

	@Column(name = "creditos", nullable = false)
	private int credits;

	@Column(name = "valor_por_credito", nullable = false, precision = 10, scale = 2)
	private BigDecimal pricePerCredit;

	@Column(name = "beneficios", nullable = false)
	private String benefits;

	@Column(name = "ativo", nullable = false)
	private boolean active;

	@Column(name = "ordem", nullable = false)
	private int position;

	protected CreditPackage() {
	}

	/** Calculado sempre no servidor: créditos vezes o valor por crédito. */
	public BigDecimal totalPrice() {
		return pricePerCredit.multiply(BigDecimal.valueOf(credits));
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public int getCredits() {
		return credits;
	}

	public BigDecimal getPricePerCredit() {
		return pricePerCredit;
	}

	public String getBenefits() {
		return benefits;
	}

	public boolean isActive() {
		return active;
	}

}
