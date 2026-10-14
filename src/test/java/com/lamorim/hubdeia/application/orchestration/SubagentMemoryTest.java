package com.lamorim.hubdeia.application.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.TaskExecutionService;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.orchestration.SubagentPromptBuilder.SubagentPrompt;
import com.lamorim.hubdeia.application.run.AgentRunStore;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

/** The individual short-term memory a subagent receives: its own recent tasks, as plain text. */
class SubagentMemoryTest extends MongoIntegrationTest {

	private static final String CONVERSATION = "conversation-1";
	private static final String RUN = "run-1";

	@Autowired
	TaskExecutionService executions;
	@Autowired
	SubagentPromptBuilder prompts;
	@Autowired
	AgentRunStore runStore;
	@Autowired
	ArtifactService artifacts;
	@Autowired
	SubagentRepository subagents;

	private Subagent measurement;
	private Subagent campaigns;

	@BeforeEach
	void catalog() {
		measurement = subagents.save(new Subagent("agent-mensuracao", "Mensuracao", "", "Voce mede campanhas.", List.of(), List.of(), List.of("INSIGHT"), List.of(), true));
		campaigns = subagents.save(new Subagent("agent-campanhas", "Campanhas", "", "Voce cria briefings.", List.of(), List.of(), List.of("BRIEFING"), List.of("INSIGHT"), true));
		runStore.insert(new AgentRun(RUN, CONVERSATION, AgentRunStatus.RUNNING, List.of(), List.of(), List.of(), "message-1", null, null, "", Instant.now(), null));
	}

	@Test
	void memoryHoldsTheLatestFiveTasksOfTheSubagentOldestFirst() {
		for (int index = 1; index <= 7; index++) {
			record(CONVERSATION, measurement, "t" + index, "resultado " + index);
		}
		record(CONVERSATION, campaigns, "other-agent", "nao deve aparecer");
		record("other-conversation", measurement, "other-conversation-task", "nao deve aparecer");

		List<SubagentExecution> memory = executions.recentMemory(CONVERSATION, measurement.id());

		assertThat(memory).extracting(SubagentExecution::taskId).containsExactly("t3", "t4", "t5", "t6", "t7");
	}

	@Test
	void severalAttemptsOfOneTaskCountOnceAndTheLatestAttemptWins() {
		for (int index = 1; index <= 5; index++) {
			record(CONVERSATION, measurement, "t" + index, "resultado " + index);
		}
		for (int attempt = 1; attempt <= 8; attempt++) {
			record(CONVERSATION, measurement, "t6", "tentativa " + attempt);
		}

		List<SubagentExecution> memory = executions.recentMemory(CONVERSATION, measurement.id());

		assertThat(memory).extracting(SubagentExecution::taskId).containsExactly("t2", "t3", "t4", "t5", "t6");
		assertThat(TaskOutput.from(memory.getLast().output()).content()).isEqualTo("tentativa 8");
	}

	@Test
	void executionsWrittenBackToBackKeepTheirOrder() {
		for (int index = 1; index <= 20; index++) {
			record(CONVERSATION, measurement, "t" + index, "resultado " + index);
		}

		assertThat(executions.recentMemory(CONVERSATION, measurement.id())).extracting(SubagentExecution::taskId)
				.containsExactly("t16", "t17", "t18", "t19", "t20");
	}

	@Test
	void theMemoryInThePromptIsPlainTextWithoutVisualMarkup() {
		Visual table = new Visual("viz1", "Tabela de campanhas", VisualFormat.HTML, "<section class=\"data-table\"><table><tr><td>1</td></tr></table></section>");
		record(CONVERSATION, measurement, "t1", new TaskOutput("Black lidera. [[visualization:viz1]] Fim.", List.of(table)));
		record(CONVERSATION, campaigns, "t-other", new TaskOutput("memoria de outro agente", List.of()));

		SubagentPrompt prompt = prompts.build(CONVERSATION, RUN, measurement, newTask("t2", "agent-mensuracao", "Agora so o publico jovem"));

		assertThat(prompt.messages()).hasSize(3);
		String memory = prompt.messages().get(1).getString("content");
		assertThat(memory).startsWith("Memoria individual do subagente:").contains("task_id=t1").contains("Black lidera. [visual: Tabela de campanhas] Fim.");
		assertThat(memory).doesNotContain("<section").doesNotContain("[[visualization").doesNotContain("memoria de outro agente");
		assertThat(prompt.messages().getLast().getString("content")).contains("Agora so o publico jovem");
	}

	@Test
	void longOutputsAreCutSoMemoryStaysShort() {
		record(CONVERSATION, measurement, "t1", "x".repeat(5000));

		String memory = prompts.build(CONVERSATION, RUN, measurement, newTask("t2", "agent-mensuracao", "algo")).messages().get(1).getString("content");

		assertThat(memory.length()).isLessThan(2000);
		assertThat(memory).endsWith("...");
	}

	@Test
	void withoutHistoryThereIsNoMemorySection() {
		SubagentPrompt prompt = prompts.build(CONVERSATION, RUN, measurement, newTask("t1", "agent-mensuracao", "primeira pergunta"));

		assertThat(prompt.messages()).extracting(message -> message.getString("role")).containsExactly("system", "user");
		assertThat(prompt.consumedArtifactIds()).isEmpty();
	}

	@Test
	void consumedArtifactsReachThePromptAsPlainTextAndAreReportedForLineage() {
		Visual chart = new Visual("viz9", "Receita", VisualFormat.VEGA_LITE, "{\"mark\":\"bar\"}");
		AgentRun run = runStore.require(RUN);
		Task producer = newTask("insight-task", "agent-mensuracao", "Insight setembro");
		var insight = artifacts.publish(run, producer, new TaskExecutionResult(producer.id(), measurement.id(),
				new TaskOutput("Black lidera. [[visualization:viz9]]", List.of(chart)).toMap(), List.of(), List.of(), List.of(), List.of())).orElseThrow();

		SubagentPrompt prompt = prompts.build(CONVERSATION, RUN, campaigns, newTask("brief", "agent-campanhas", "Crie o briefing"));

		String instructions = prompt.messages().getFirst().getString("content");
		assertThat(instructions).contains("[INSIGHT v1] descricao insight-task").contains("Black lidera. [visual: Receita]");
		assertThat(instructions).doesNotContain("vega").doesNotContain("{\"mark\"");
		assertThat(prompt.consumedArtifactIds()).containsExactly(insight.id());
	}

	private void record(String conversationId, Subagent subagent, String taskId, String text) {
		record(conversationId, subagent, taskId, new TaskOutput(text, List.of()));
	}

	private void record(String conversationId, Subagent subagent, String taskId, TaskOutput output) {
		Task task = newTask(taskId, subagent.id(), "descricao " + taskId);
		executions.record(conversationId, RUN, task, new TaskExecutionResult(taskId, subagent.id(), output.toMap(), List.of(), List.of(), List.of(), List.of()));
	}

	private static Task newTask(String id, String subagentId, String enrichedContent) {
		return Task.create(id, "descricao " + id, subagentId, enrichedContent, 3, Instant.now());
	}
}
