package br.ufba.veridt.credits.internal.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.ufba.veridt.credits.CreditMovement;
import br.ufba.veridt.credits.CreditMovementType;
import br.ufba.veridt.credits.CreditOrigin;

/** SQL do saldo e do extrato. Os métodos só fazem sentido dentro de uma transação, aberta pelo serviço. */
@Repository
public class CreditLedgerRepository {

	private static final String MOVEMENT_COLUMNS = """
			id, usuario_id, tipo, quantidade, saldo_apos, origem, referencia_id, chave_idempotencia, criado_em""";

	private final JdbcTemplate jdbc;

	public CreditLedgerRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Serializa as operações com a mesma chave até o fim da transação: a segunda espera a primeira e, ao continuar,
	 * já enxerga a movimentação gravada.
	 */
	public void lockIdempotencyKey(String idempotencyKey) {
		jdbc.queryForList("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(?, 0))", Integer.class, idempotencyKey);
	}

	public Optional<CreditMovement> findByIdempotencyKey(String idempotencyKey) {
		List<CreditMovement> found = jdbc.query(
				"SELECT " + MOVEMENT_COLUMNS + " FROM movimentacao_credito WHERE chave_idempotencia = ?",
				CreditLedgerRepository::toMovement, idempotencyKey);
		return found.stream().findFirst();
	}

	public int balance(UUID userId) {
		List<Integer> found = jdbc.queryForList("SELECT saldo FROM saldo_credito WHERE usuario_id = ?", Integer.class,
				userId);
		return found.isEmpty() ? 0 : found.getFirst();
	}

	/** Cria a linha de saldo no primeiro crédito. Devolve o saldo novo. */
	public int addToBalance(UUID userId, int amount, Instant now) {
		return jdbc.queryForObject("""
				INSERT INTO saldo_credito (usuario_id, saldo, atualizado_em) VALUES (?, ?, ?)
				ON CONFLICT (usuario_id)
				DO UPDATE SET saldo = saldo_credito.saldo + EXCLUDED.saldo, atualizado_em = EXCLUDED.atualizado_em
				RETURNING saldo
				""", Integer.class, userId, amount, Timestamp.from(now));
	}

	/**
	 * Débito atômico: o UPDATE trava a linha e só subtrai se o saldo cobrir a quantidade, então débitos simultâneos
	 * são aplicados um de cada vez. Vazio quando o saldo não basta (ou o usuário nunca teve créditos).
	 */
	public Optional<Integer> subtractFromBalance(UUID userId, int amount, Instant now) {
		List<Integer> updated = jdbc.queryForList("""
				UPDATE saldo_credito SET saldo = saldo - ?, atualizado_em = ?
				WHERE usuario_id = ? AND saldo >= ?
				RETURNING saldo
				""", Integer.class, amount, Timestamp.from(now), userId, amount);
		return updated.stream().findFirst();
	}

	public void insertMovement(CreditMovement movement) {
		jdbc.update("INSERT INTO movimentacao_credito (" + MOVEMENT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				movement.id(), movement.userId(), movement.type().name(), movement.amount(), movement.balanceAfter(),
				movement.origin().name(), movement.referenceId(), movement.idempotencyKey(),
				Timestamp.from(movement.createdAt()));
	}

	private static CreditMovement toMovement(ResultSet rs, int row) throws SQLException {
		return new CreditMovement(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class),
				CreditMovementType.valueOf(rs.getString("tipo")), rs.getInt("quantidade"), rs.getInt("saldo_apos"),
				CreditOrigin.valueOf(rs.getString("origem")), rs.getObject("referencia_id", UUID.class),
				rs.getString("chave_idempotencia"), rs.getTimestamp("criado_em").toInstant());
	}

}
