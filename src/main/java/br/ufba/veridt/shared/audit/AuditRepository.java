package br.ufba.veridt.shared.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Só insere: a tabela {@code auditoria} recusa alterações e exclusões no próprio banco, e a trigger da V7 calcula
 * {@code seq}, {@code hash_anterior} e {@code hash} de cada linha.
 */
@Repository
public class AuditRepository {

	private static final String INSERT = """
			INSERT INTO auditoria (id, ocorrido_em, usuario_id, operacao, resultado, recurso_id, detalhe, ip_origem,
				agente_usuario, requisicao_id)
			VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS INET), ?, ?)
			""";

	private final JdbcTemplate jdbc;

	public AuditRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(Entry entry) {
		jdbc.update(INSERT, entry.id(), entry.occurredAt(), entry.userId(), entry.operation(), entry.result().name(),
				entry.resourceId(), entry.detail(), entry.clientIp(), entry.userAgent(), entry.requestId());
	}

	public record Entry(UUID id, OffsetDateTime occurredAt, UUID userId, String operation, AuditResult result,
			UUID resourceId, String detail, String clientIp, String userAgent, UUID requestId) {
	}

}
