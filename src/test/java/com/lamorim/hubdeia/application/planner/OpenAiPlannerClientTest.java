package com.lamorim.hubdeia.application.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.lamorim.hubdeia.application.ArtifactView;
import com.lamorim.hubdeia.application.ConversationMemorySanitizer;
import com.lamorim.hubdeia.application.llm.OpenAiChatClient;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;
import com.lamorim.hubdeia.domain.model.Message;

/** What the planner model is told: the prompt must reference exactly the context the application really sends. */
class OpenAiPlannerClientTest {

	private final OpenAiChatClient chat = mock(OpenAiChatClient.class);
	private final OpenAiPlannerClient client = new OpenAiPlannerClient(chat, "model-x", new ConversationMemorySanitizer());

	@BeforeEach
	void modelAnswersWithADirectReply() {
		when(chat.completeJson(any())).thenReturn(Document.parse("""
				{"should_create_tasks": false, "response_message": "ok", "work_context_action": "NONE", "work_context_id": "",
				 "related_human_input_request_id": "", "reactivate_artifact_id": "", "tasks": []}"""));
	}

	@Test
	void theSystemPromptTellsThePlannerToReadTheContextBeforeDecidingAndNeverToProduceVisualsItself() {
		String prompt = request().getList("messages", Document.class).getFirst().getString("content");

		assertThat(prompt).contains("LEIA O CONTEXTO ANTES DE DECIDIR").contains("CONTINUACAO de trabalho de especialista");
		assertThat(prompt).contains("graficos, tabelas, calculos e numeros novos nunca saem de voce");
		assertThat(prompt).contains("DEVOLVA o trabalho ao mesmo especialista");
		assertThat(prompt).contains("O especialista NAO ve a conversa");
	}

	@Test
	void everyContextFieldThePromptMentionsIsReallyInThePlanningInput() {
		String prompt = request().getList("messages", Document.class).getFirst().getString("content");
		Document planningInput = planningInputOf(request());

		List.of("recent_runs", "artifacts", "active_context", "paused_contexts", "pending_human_input_requests", "current_request", "available_work_contexts")
				.forEach(field -> {
					assertThat(prompt).as("prompt mentions " + field).contains(field);
					assertThat(planningInput).as("input carries " + field).containsKey(field);
				});
	}

	@Test
	void recentRunsReachTheModelWithWhoWasAskedWhatAndWhatCameBack() {
		PlanningTaskSummary task = new PlanningTaskSummary("agent-mensuracao", "Total de page_views", "FINISHED", "quantos page_views?", "500.000 page_views");
		PlanningInput input = new PlanningInput("Gere um grafico desses dados?", List.of(), null, List.of(),
				List.of(new PlanningRunSummary("run-1", "FINISHED", List.of(task))), List.of(), List.of(), List.of());

		client.plan(input);

		Document sent = planningInputOf(captured());
		List<Document> runs = sent.getList("recent_runs", Document.class);
		assertThat(runs).hasSize(1);
		assertThat(runs.getFirst().getString("agent_run_id")).isEqualTo("run-1");
		Document sentTask = runs.getFirst().getList("tasks", Document.class).getFirst();
		assertThat(sentTask.getString("subagent_id")).isEqualTo("agent-mensuracao");
		assertThat(sentTask.getString("request")).isEqualTo("quantos page_views?");
		assertThat(sentTask.getString("result")).isEqualTo("500.000 page_views");
	}

	@Test
	void artifactsTellTheModelWhichSpecialistProducedThem() {
		ArtifactView artifact = new ArtifactView("a1", "INSIGHT", 1, ArtifactStatus.ACTIVE, false, "Page views", "500.000", "500.000", List.of(),
				null, "agent-mensuracao", "run-1", Instant.now());
		PlanningInput input = new PlanningInput("x", List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(artifact));

		client.plan(input);

		assertThat(planningInputOf(captured()).getList("artifacts", Document.class).getFirst().getString("subagent_id")).isEqualTo("agent-mensuracao");
	}

	@Test
	void theChatHistoryIsSentBeforeTheRequestWithoutRenderedVisuals() {
		Message earlier = new Message("m1", "c1", "assistant", "Resultado.\n\n<!--visual:start-->\n\n<table></table>\n\n<!--visual:end-->", Instant.now());
		PlanningInput input = new PlanningInput("e agora?", List.of(earlier), null, List.of(), List.of(), List.of(), List.of(), List.of());

		client.plan(input);

		List<Document> messages = captured().getList("messages", Document.class);
		assertThat(messages).extracting(message -> message.getString("role")).containsExactly("system", "assistant", "user");
		assertThat(messages.get(1).getString("content")).contains("[visual omitido da memoria]").doesNotContain("<table>");
	}

	private Document request() {
		client.plan(new PlanningInput("pedido", List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of()));
		return captured();
	}

	private Document captured() {
		ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
		verify(chat, org.mockito.Mockito.atLeastOnce()).completeJson(captor.capture());
		return captor.getValue();
	}

	private static Document planningInputOf(Document request) {
		List<Document> messages = request.getList("messages", Document.class);
		return Document.parse(messages.getLast().getString("content"));
	}
}
