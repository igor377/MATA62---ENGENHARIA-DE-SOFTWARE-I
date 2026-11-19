package br.ufba.veridt.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.ufba.veridt.TestcontainersConfiguration;
import br.ufba.veridt.shared.audit.AuditChainVerifier.ChainBreak;

/** Cadeia de hashes da V7 com Postgres real. */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(TestcontainersConfiguration.class)
class AuditChainTests {

	private static final int THREADS = 20;

	enum TestOperation implements AuditOperation {
		TESTE_CADEIA
	}

	private final AuditLog auditLog;
	private final AuditChainVerifier verifier;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;

	AuditChainTests(AuditLog auditLog, AuditChainVerifier verifier, JdbcTemplate jdbc,
			PlatformTransactionManager transactionManager) {
		this.auditLog = auditLog;
		this.verifier = verifier;
		this.jdbc = jdbc;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@AfterEach
	void limparContexto() {
		AuditContext.set(null);
	}

	@Test
	void insertsConcorrentesFormamUmaCadeiaValida() throws Exception {
		UUID usuario = UUID.randomUUID();
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<?>> tarefas = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
			for (int i = 0; i < THREADS; i++) {
				boolean sucesso = i % 2 == 0;
				tarefas.add(executor.submit(() -> {
					largada.await();
					if (sucesso) {
						auditLog.recordSuccess(TestOperation.TESTE_CADEIA, usuario);
					}
					else {
						auditLog.recordFailure(TestOperation.TESTE_CADEIA, usuario, "CONCORRENTE");
					}
					return null;
				}));
			}
			largada.countDown();
			for (Future<?> tarefa : tarefas) {
				tarefa.get();
			}
		}

		List<Map<String, Object>> linhas = jdbc.queryForList(
				"SELECT seq, hash_anterior, hash FROM auditoria WHERE usuario_id = ? ORDER BY seq", usuario);
		assertThat(linhas).hasSize(THREADS);
		assertThat(linhas).extracting(linha -> linha.get("hash_anterior")).doesNotHaveDuplicates();
		assertThat(verifier.verify()).isEmpty();
	}

	@Test
	void alteracaoComATriggerDesligadaEDetectadaNoEloCerto() {
		UUID usuario = UUID.randomUUID();
		auditLog.recordSuccess(TestOperation.TESTE_CADEIA, usuario);
		auditLog.recordSuccess(TestOperation.TESTE_CADEIA, UUID.randomUUID());
		long seq = jdbc.queryForObject("SELECT seq FROM auditoria WHERE usuario_id = ?", Long.class, usuario);
		assertThat(verifier.verify()).isEmpty();

		comTriggerDesligada(() -> jdbc.update("UPDATE auditoria SET resultado = 'FALHA' WHERE seq = ?", seq));
		try {
			assertThat(verifier.verify()).contains(new ChainBreak(seq, ChainBreak.Reason.HASH_DIVERGENTE));
		}
		finally {
			// Desfaz a adulteração: as outras classes de teste podem usar o mesmo banco.
			comTriggerDesligada(() -> jdbc.update("UPDATE auditoria SET resultado = 'SUCESSO' WHERE seq = ?", seq));
		}
		assertThat(verifier.verify()).isEmpty();
	}

	@Test
	void recalcularOHashDaLinhaAlteradaQuebraOEloSeguinte() {
		UUID usuario = UUID.randomUUID();
		auditLog.recordSuccess(TestOperation.TESTE_CADEIA, usuario);
		auditLog.recordSuccess(TestOperation.TESTE_CADEIA, UUID.randomUUID());
		Map<String, Object> original = jdbc.queryForMap("SELECT seq, hash FROM auditoria WHERE usuario_id = ?", usuario);
		long seq = (Long) original.get("seq");

		comTriggerDesligada(() -> {
			jdbc.update("UPDATE auditoria SET detalhe = 'ADULTERADO' WHERE seq = ?", seq);
			jdbc.update("UPDATE auditoria a SET hash = auditoria_sha256(auditoria_serializar(a)) WHERE seq = ?", seq);
		});
		try {
			assertThat(verifier.verify()).contains(new ChainBreak(seq + 1, ChainBreak.Reason.ELO_ANTERIOR_DIVERGENTE));
		}
		finally {
			comTriggerDesligada(() -> jdbc.update("UPDATE auditoria SET detalhe = NULL, hash = ? WHERE seq = ?",
					original.get("hash"), seq));
		}
		assertThat(verifier.verify()).isEmpty();
	}

	@Test
	void valoresDaCadeiaEnviadosNoInsertSaoDescartados() {
		UUID usuario = UUID.randomUUID();

		jdbc.update("""
				INSERT INTO auditoria (id, ocorrido_em, usuario_id, operacao, resultado, seq, hash_anterior, hash)
				VALUES (?, now(), ?, 'TESTE_CADEIA', 'SUCESSO', 999999, repeat('0', 64), repeat('0', 64))
				""", UUID.randomUUID(), usuario);

		Map<String, Object> linha = jdbc.queryForMap("SELECT seq, hash FROM auditoria WHERE usuario_id = ?", usuario);
		assertThat((Long) linha.get("seq")).isNotEqualTo(999999L);
		assertThat(linha.get("hash")).isNotEqualTo("0".repeat(64));
		assertThat(verifier.verify()).isEmpty();
	}

	@Test
	void formatoCanonicoDoJavaBateComODoBancoMesmoComCaracteresEspeciais() {
		UUID usuario = UUID.randomUUID();
		AuditContext.set(AuditContext.http(UUID.randomUUID(), "2001:db8::1", "Agente;com:separadores 🙂 e acentuação"));
		auditLog.recordFailure(TestOperation.TESTE_CADEIA, usuario, "DETALHE;COM:SEPARADORES");

		assertThat(jdbc.queryForObject("SELECT host(ip_origem) FROM auditoria WHERE usuario_id = ?", String.class,
				usuario)).isEqualTo("2001:db8::1");
		assertThat(verifier.verify()).isEmpty();
	}

	@Test
	void geneseCobreAsLinhasAnterioresAMigration() {
		Map<String, Object> genese = jdbc.queryForMap("SELECT operacao, detalhe, hash_anterior FROM auditoria WHERE seq = 1");

		assertThat(genese.get("operacao")).isEqualTo("GENESE_CADEIA");
		// Banco de teste novo: nenhuma linha anterior à V7, então o resumo é o SHA-256 do texto vazio.
		assertThat(genese.get("detalhe")).isEqualTo("LEGADO:0");
		assertThat(genese.get("hash_anterior")).isEqualTo(AuditChainVerifier.sha256(""));
	}

	/** Desliga a trigger de imutabilidade só dentro da transação: nenhuma outra sessão a vê desligada. */
	private void comTriggerDesligada(Runnable alteracao) {
		transaction.executeWithoutResult(status -> {
			jdbc.execute("ALTER TABLE auditoria DISABLE TRIGGER auditoria_somente_insercao_trg");
			alteracao.run();
			jdbc.execute("ALTER TABLE auditoria ENABLE TRIGGER auditoria_somente_insercao_trg");
		});
	}

}
