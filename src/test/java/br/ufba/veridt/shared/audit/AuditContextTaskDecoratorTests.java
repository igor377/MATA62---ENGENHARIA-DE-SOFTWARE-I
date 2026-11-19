package br.ufba.veridt.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditContextTaskDecoratorTests {

	@AfterEach
	void limpar() {
		AuditContext.set(null);
	}

	@Test
	void tarefaAgendadaRecebeUmIdNovoACadaExecucao() {
		List<AuditContext> execucoes = new ArrayList<>();
		Runnable tarefa = new AuditContextTaskDecorator(AuditContext.SCHEDULED_TASK)
			.decorate(() -> execucoes.add(AuditContext.current()));

		tarefa.run();
		tarefa.run();

		assertThat(execucoes).hasSize(2).allSatisfy(contexto -> {
			assertThat(contexto.process()).isEqualTo(AuditContext.SCHEDULED_TASK);
			assertThat(contexto.clientIp()).isNull();
			assertThat(contexto.userAgent()).isNull();
		});
		assertThat(execucoes.get(0).requestId()).isNotEqualTo(execucoes.get(1).requestId());
		assertThat(AuditContext.current()).isNull();
	}

	@Test
	void trabalhoAssincronoHerdaOContextoDaRequisicao() {
		AuditContext requisicao = AuditContext.http(UUID.randomUUID(), "203.0.113.5", "Mozilla/5.0");
		AuditContext.set(requisicao);
		List<AuditContext> execucoes = new ArrayList<>();
		Runnable tarefa = new AuditContextTaskDecorator(AuditContext.ASYNC_PROCESS)
			.decorate(() -> execucoes.add(AuditContext.current()));
		AuditContext.set(null);

		tarefa.run();

		assertThat(execucoes).containsExactly(requisicao);
	}

}
