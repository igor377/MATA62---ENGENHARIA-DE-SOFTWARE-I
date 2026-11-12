package br.ufba.veridt.shared.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Só insere: a tabela {@code auditoria} recusa alterações e exclusões no próprio banco. */
@Repository
public class AuditRepository {

	private static final String INSERT = """
			INSERT INTO auditoria (id, ocorrido_em, usuario_id, operacao, resultado, recurso_id, detalhe)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbc;

	public AuditRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(UUID userId, AuditOperation operation, AuditResult result, UUID resourceId, String detail) {
		jdbc.update(INSERT, UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC), userId, operation.name(),
				result.name(), resourceId, detail);
	}

}
