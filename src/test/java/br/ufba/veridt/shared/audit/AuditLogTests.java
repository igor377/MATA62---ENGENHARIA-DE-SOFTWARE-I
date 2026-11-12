package br.ufba.veridt.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.ufba.veridt.TestcontainersConfiguration;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(TestcontainersConfiguration.class)
class AuditLogTests {

	enum TestOperation implements AuditOperation {
		TESTE_AUDITORIA
	}

	private final AuditLog auditLog;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;

	private OffsetDateTime inicio;

	AuditLogTests(AuditLog auditLog, JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
		this.auditLog = auditLog;
		this.jdbc = jdbc;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@BeforeEach
	void marcarInicio() {
		inicio = OffsetDateTime.now(ZoneOffset.UTC);
	}

	@Test
	void gravaQuemQuandoOperacaoEResultado() {
		UUID usuario = UUID.randomUUID();
		UUID recurso = UUID.randomUUID();

		auditLog.recordSuccess(TestOperation.TESTE_AUDITORIA, usuario, recurso);

		var linha = jdbc.queryForMap("SELECT * FROM auditoria WHERE usuario_id = ?", usuario);
		assertThat(linha).containsEntry("operacao", "TESTE_AUDITORIA")
			.containsEntry("resultado", "SUCESSO")
			.containsEntry("recurso_id", recurso);
		assertThat(linha.get("ocorrido_em")).isNotNull();
	}

	@Test
	void bancoRecusaAlterarApagarOuEsvaziarAAuditoria() {
		UUID usuario = UUID.randomUUID();
		auditLog.recordSuccess(TestOperation.TESTE_AUDITORIA, usuario);

		assertThatThrownBy(() -> jdbc.update("UPDATE auditoria SET resultado = 'FALHA' WHERE usuario_id = ?", usuario))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria WHERE usuario_id = ?", usuario))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.execute("TRUNCATE auditoria"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(registros(usuario)).isEqualTo(1);
	}

	@Test
	void falhaContinuaRegistradaQuandoAOperacaoEDesfeita() {
		UUID usuario = UUID.randomUUID();

		transaction.executeWithoutResult(status -> {
			auditLog.recordFailure(TestOperation.TESTE_AUDITORIA, usuario, "MOTIVO");
			status.setRollbackOnly();
		});

		assertThat(jdbc.queryForObject("SELECT detalhe FROM auditoria WHERE usuario_id = ? AND resultado = 'FALHA'",
				String.class, usuario)).isEqualTo("MOTIVO");
	}

	@Test
	void sucessoEDesfeitoJuntoComAOperacao() {
		UUID usuario = UUID.randomUUID();

		transaction.executeWithoutResult(status -> {
			auditLog.recordSuccess(TestOperation.TESTE_AUDITORIA, usuario);
			status.setRollbackOnly();
		});

		assertThat(registros(usuario)).isZero();
	}

	private int registros(UUID usuario) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE usuario_id = ? AND ocorrido_em >= ?",
				Integer.class, usuario, inicio);
	}

}
