package com.lamorim.hubdeia.application.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.run.AgentRunStore;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;

/** The planner decides from what really happened in the conversation, so that is what it is handed. */
class ConversationPlannerAgentTest extends MongoIntegrationTest {

	@MockitoBean
	OpenAiPlannerClient client;

	@Autowired
	ConversationPlannerAgent planner;
	@Autowired
	ConversationService conversations;
	@Autowired
	AgentRunStore runStore;

	String conversationId;
	String requestId;

	@BeforeEach
	void conversationWithHistory() {
		var first = conversations.create("user-1", "Quantos page_views tivemos?");
		conversationId = first.conversationId();
		conversations.appendAssistantMessage(conversationId, "Foram 500.000 page_views.");
		requestId = conversations.appendUserMessage("user-1", conversationId, "Gere um grafico desses dados?").messageId();
		when(client.plan(any())).thenReturn(new PlannerDecision(false, "ok", WorkContextAction.NONE, "", "", "", List.of()));
	}

	@Test
	void theLastThreeRunsAreHandedOverOldestFirstWithWhoDidWhatAndTheResultAsPlainText() {
		Visual table = new Visual("viz1", "Tabela", VisualFormat.HTML, "<section class=\"data-table\"><table></table></section>");
		for (int index = 1; index <= 4; index++) {
			runStore.insert(run(index, new TaskOutput("Resultado " + index + " [[visualization:viz1]]", List.of(table)), TaskStatus.FINISHED));
		}

		PlanningInput input = planned();

		assertThat(input.recentRuns()).extracting(PlanningRunSummary::runId).containsExactly("run-2", "run-3", "run-4");
		PlanningTaskSummary task = input.recentRuns().getLast().tasks().getFirst();
		assertThat(task.subagentId()).isEqualTo("agent-mensuracao");
		assertThat(task.description()).isEqualTo("Tarefa 4");
		assertThat(task.status()).isEqualTo("FINISHED");
		assertThat(task.request()).isEqualTo("Pedido 4");
		assertThat(task.result()).isEqualTo("Resultado 4 [visual: Tabela]");
	}

	@Test
	void longRequestsAndResultsAreCut() {
		runStore.insert(run(1, new TaskOutput("r".repeat(5000), List.of()), TaskStatus.FINISHED));

		PlanningTaskSummary task = planned().recentRuns().getFirst().tasks().getFirst();

		assertThat(task.result().length()).isLessThan(800);
		assertThat(task.result()).endsWith("...");
	}

	@Test
	void aCancelledTaskShowsAsSuchWithNoResult() {
		runStore.insert(run(1, new TaskOutput("", List.of()), TaskStatus.CANCELLED));

		PlanningTaskSummary task = planned().recentRuns().getFirst().tasks().getFirst();

		assertThat(task.status()).isEqualTo("CANCELLED");
		assertThat(task.result()).isEmpty();
	}

	@Test
	void theRequestAndOnlyTheMessagesBeforeItGoWithIt() {
		PlanningInput input = planned();

		assertThat(input.currentRequest()).isEqualTo("Gere um grafico desses dados?");
		assertThat(input.history()).extracting(message -> message.content()).containsExactly("Quantos page_views tivemos?", "Foram 500.000 page_views.");
		assertThat(input.recentRuns()).isEmpty();
	}

	private PlanningInput planned() {
		planner.plan(conversationId, requestId);
		ArgumentCaptor<PlanningInput> captor = ArgumentCaptor.forClass(PlanningInput.class);
		verify(client).plan(captor.capture());
		return captor.getValue();
	}

	private AgentRun run(int index, TaskOutput output, TaskStatus status) {
		Instant at = Instant.now().minus(10 - index, ChronoUnit.MINUTES);
		Task task = new Task("task-" + index, "Tarefa " + index, "agent-mensuracao", status, Map.of("enriched_content", "Pedido " + index),
				output.toMap(), List.of(), 1, 3, at, at);
		return new AgentRun("run-" + index, conversationId, AgentRunStatus.FINISHED, List.of(task), List.of(), List.of(), "message-" + index,
				null, null, "AgentRun finalizado.", at, at);
	}
}
