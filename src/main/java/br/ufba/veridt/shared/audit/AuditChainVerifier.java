package br.ufba.veridt.shared.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Percorre a cadeia de hashes da auditoria e devolve o primeiro elo quebrado. Reimplementa em Java o formato canônico
 * da V7 ({@code auditoria_serializar}), sem usar as funções do banco: assim também confere que o formato documentado é
 * o que o banco calcula. Detecta alteração pontual; quem pode recalcular a cadeia inteira não é detectado sem uma
 * âncora externa (pendência no BACKLOG).
 */
@Component
public class AuditChainVerifier {

	private static final int PAGE_SIZE = 1000;

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'");

	private static final String COLUMNS = """
			seq, id, ocorrido_em, usuario_id, operacao, resultado, recurso_id, detalhe, host(ip_origem) AS ip,
			agente_usuario, requisicao_id, hash_anterior, hash
			""";

	private final JdbcTemplate jdbc;
	private final TransactionTemplate snapshot;

	public AuditChainVerifier(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.snapshot = new TransactionTemplate(transactionManager);
		// Uma única foto do banco: inserts durante a verificação não aparecem pela metade.
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.snapshot.setReadOnly(true);
	}

	/** Vazio quando a cadeia está íntegra. */
	public Optional<ChainBreak> verify() {
		return snapshot.execute(status -> Optional.ofNullable(findFirstBreak()));
	}

	private ChainBreak findFirstBreak() {
		String previous = legacyDigest();
		long expectedSeq = 1;
		long lastSeq = 0;
		while (true) {
			List<Row> page = jdbc.query("SELECT " + COLUMNS + " FROM auditoria WHERE seq > ? ORDER BY seq LIMIT ?",
					AuditChainVerifier::row, lastSeq, PAGE_SIZE);
			for (Row row : page) {
				if (row.seq() != expectedSeq) {
					return new ChainBreak(expectedSeq, ChainBreak.Reason.SEQ_AUSENTE);
				}
				if (!previous.equals(row.previousHash())) {
					return new ChainBreak(row.seq(), row.seq() == 1 ? ChainBreak.Reason.LEGADO_DIVERGENTE
							: ChainBreak.Reason.ELO_ANTERIOR_DIVERGENTE);
				}
				if (!sha256(serialize(row)).equals(row.hash())) {
					return new ChainBreak(row.seq(), ChainBreak.Reason.HASH_DIVERGENTE);
				}
				previous = row.hash();
				lastSeq = row.seq();
				expectedSeq++;
			}
			if (page.size() < PAGE_SIZE) {
				break;
			}
		}
		Map<String, Object> head = jdbc.queryForMap("SELECT ultima_seq, ultimo_hash FROM auditoria_cadeia WHERE id = 1");
		if (((Number) head.get("ultima_seq")).longValue() != lastSeq || !previous.equals(head.get("ultimo_hash"))) {
			return new ChainBreak(lastSeq, ChainBreak.Reason.CABECA_DIVERGENTE);
		}
		return null;
	}

	/** Resumo das linhas anteriores à V7, na mesma ordem usada pela migration. */
	private String legacyDigest() {
		MessageDigest digest = newDigest();
		jdbc.query("SELECT " + COLUMNS + " FROM auditoria WHERE seq IS NULL ORDER BY ocorrido_em, id",
				(ResultSet rs) -> {
					digest.update(serialize(row(rs, 0)).getBytes(StandardCharsets.UTF_8));
				});
		return HexFormat.of().formatHex(digest.digest());
	}

	static String serialize(Row row) {
		StringBuilder text = new StringBuilder();
		field(text, row.seq() == null ? null : row.seq().toString());
		field(text, string(row.id()));
		field(text, row.occurredAt() == null ? null
				: TIMESTAMP.format(row.occurredAt().withOffsetSameInstant(ZoneOffset.UTC)));
		field(text, string(row.userId()));
		field(text, row.operation());
		field(text, row.result());
		field(text, string(row.resourceId()));
		field(text, row.detail());
		field(text, row.clientIp());
		field(text, row.userAgent());
		field(text, string(row.requestId()));
		field(text, row.previousHash());
		return text.toString();
	}

	/** {@code <tamanho em caracteres>:<valor>;} ou {@code -;} para nulo, como {@code auditoria_campo}. */
	private static void field(StringBuilder text, String value) {
		if (value == null) {
			text.append("-;");
		}
		else {
			text.append(value.codePointCount(0, value.length())).append(':').append(value).append(';');
		}
	}

	private static String string(UUID value) {
		return value == null ? null : value.toString();
	}

	static String sha256(String text) {
		return HexFormat.of().formatHex(newDigest().digest(text.getBytes(StandardCharsets.UTF_8)));
	}

	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		long seq = rs.getLong("seq");
		return new Row(rs.wasNull() ? null : seq, rs.getObject("id", UUID.class),
				rs.getObject("ocorrido_em", OffsetDateTime.class), rs.getObject("usuario_id", UUID.class),
				rs.getString("operacao"), rs.getString("resultado"), rs.getObject("recurso_id", UUID.class),
				rs.getString("detalhe"), rs.getString("ip"), rs.getString("agente_usuario"),
				rs.getObject("requisicao_id", UUID.class), rs.getString("hash_anterior"), rs.getString("hash"));
	}

	record Row(Long seq, UUID id, OffsetDateTime occurredAt, UUID userId, String operation, String result,
			UUID resourceId, String detail, String clientIp, String userAgent, UUID requestId, String previousHash,
			String hash) {
	}

	/** Primeiro elo quebrado: a posição ({@code seq}) e o motivo. */
	public record ChainBreak(long seq, Reason reason) {

		public enum Reason {

			/** Falta uma posição: uma linha foi apagada ou inserida sem a trigger. */
			SEQ_AUSENTE,
			/** O hash anterior gravado não é o hash da linha anterior. */
			ELO_ANTERIOR_DIVERGENTE,
			/** O conteúdo da linha não bate com o hash dela. */
			HASH_DIVERGENTE,
			/** Uma linha anterior à V7 foi alterada, apagada ou inserida. */
			LEGADO_DIVERGENTE,
			/** A cabeça da cadeia não aponta para a última linha. */
			CABECA_DIVERGENTE

		}

	}

}
