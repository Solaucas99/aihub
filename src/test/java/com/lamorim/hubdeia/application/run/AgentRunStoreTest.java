package com.lamorim.hubdeia.application.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunLog;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.TaskValidation;

class AgentRunStoreTest extends MongoIntegrationTest {

	@Autowired
	AgentRunStore store;

	@Test
	void parallelTaskUpdatesDoNotOverwriteEachOther() {
		int taskCount = 12;
		AgentRun run = store.insert(newRun(taskCount));

		CompletableFuture.allOf(run.tasks().stream().map(task -> CompletableFuture.runAsync(() -> {
			store.markTaskRunning(run.id(), task.id());
			store.recordTaskOutput(run.id(), task.id(), Map.of("content", "out-" + task.id()), 1);
			store.appendTaskValidation(run.id(), task.id(), new TaskValidation("CONTINUE", "ok", "", 1, Instant.now()));
			store.appendLog(run.id(), AgentRunLog.info(AgentRunStatus.RUNNING, "TASK_FINISHED", "done " + task.id()));
			store.finishTask(run.id(), task.id(), TaskStatus.FINISHED);
		})).toArray(CompletableFuture[]::new)).join();

		AgentRun stored = store.require(run.id());
		assertThat(stored.tasks()).hasSize(taskCount).allSatisfy(task -> {
			assertThat(task.status()).isEqualTo(TaskStatus.FINISHED);
			assertThat(task.output()).containsEntry("content", "out-" + task.id());
			assertThat(task.validations()).hasSize(1);
			assertThat(task.attempts()).isEqualTo(1);
			assertThat(task.finishedAt()).isNotNull();
		});
		assertThat(stored.logs()).hasSize(taskCount + 1);
	}

	@Test
	void terminalStatusIsFinal() {
		AgentRun run = store.insert(newRun(1));
		AgentRunLog cancelled = AgentRunLog.info(AgentRunStatus.CANCELLED, "CANCELLED", "cancelado");

		assertThat(store.complete(run.id(), AgentRunStatus.CANCELLED, List.of(new FinalAnswerBlock("communicator", "x", "cancelado", null, null)), cancelled)).isTrue();
		assertThat(store.complete(run.id(), AgentRunStatus.FINISHED, List.of(), AgentRunLog.info(AgentRunStatus.FINISHED, "FINISHED", "fim"))).isFalse();
		assertThat(store.appendLog(run.id(), AgentRunLog.info(AgentRunStatus.RUNNING, "LATE", "atrasado"))).isFalse();
		store.markTaskRunning(run.id(), run.tasks().getFirst().id());

		AgentRun stored = store.require(run.id());
		assertThat(stored.status()).isEqualTo(AgentRunStatus.CANCELLED);
		assertThat(stored.agentRunLastMessage()).isEqualTo("cancelado");
		assertThat(stored.finalAnswer()).hasSize(1);
		assertThat(stored.finishedAt()).isNotNull();
		assertThat(store.isRunning(run.id())).isFalse();
	}

	@Test
	void retryResetKeepsOriginalInputAndAddsFeedback() {
		AgentRun run = store.insert(newRun(1));
		String taskId = run.tasks().getFirst().id();
		store.recordTaskOutput(run.id(), taskId, Map.of("content", "ruim"), 1);

		store.resetTaskForRetry(run.id(), taskId, "faltou o periodo");

		Task task = store.requireTask(run.id(), taskId);
		assertThat(task.status()).isEqualTo(TaskStatus.NEW);
		assertThat(task.output()).isEmpty();
		assertThat(task.input()).containsEntry("enriched_content", "conteudo").containsEntry("validator_feedback", "faltou o periodo");
		assertThat(task.attempts()).isEqualTo(1);
	}

	private AgentRun newRun(int tasks) {
		Instant now = Instant.now();
		List<Task> list = IntStream.range(0, tasks)
				.mapToObj(index -> Task.create("task-" + index + "-" + System.nanoTime(), "descricao " + index, "agent-x", "conteudo", 3, now)).toList();
		return new AgentRun("run-" + System.nanoTime(), "conversation-1", AgentRunStatus.RUNNING, list,
				List.of(AgentRunLog.info(AgentRunStatus.RUNNING, "TASKS_RUNNING", "Tasks planejadas.")), List.of(), "message-1", "context-1", null, "plano", now, null);
	}
}
