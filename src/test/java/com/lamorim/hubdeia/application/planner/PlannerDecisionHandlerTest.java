package com.lamorim.hubdeia.application.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.HumanInputService;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ConversationWorkContextStatus;
import com.lamorim.hubdeia.domain.model.HumanInputRequestStatus;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

class PlannerDecisionHandlerTest extends MongoIntegrationTest {

	@Autowired
	PlannerDecisionHandler handler;
	@Autowired
	ConversationService conversations;
	@Autowired
	WorkContextService contexts;
	@Autowired
	HumanInputService humanInput;
	@Autowired
	ArtifactService artifacts;
	@Autowired
	AgentRunService runs;
	@Autowired
	WorkContextRepository workContextDefinitions;
	@Autowired
	SubagentRepository subagents;

	String conversationId;
	String messageId;

	@BeforeEach
	void conversationAndCatalog() {
		subagents.save(new Subagent("agent-mensuracao", "Mensuracao", "", "", List.of(), List.of(), List.of("INSIGHT"), List.of(), true));
		workContextDefinitions.save(new WorkContext("ctx-dados", "DADOS", "Dados", "", "", List.of("agent-mensuracao"), true));
		workContextDefinitions.save(new WorkContext("ctx-outro", "OUTRO", "Outro", "", "", List.of("agent-outro"), true));
		var accepted = conversations.create("user-1", "oi");
		conversationId = accepted.conversationId();
		messageId = accepted.messageId();
	}

	@Test
	void directAnswerIsWrittenToTheChatWithoutStartingARun() {
		Optional<String> runId = handler.apply(conversationId, messageId, decision(false, "Ola!", WorkContextAction.NONE, "", "", List.of()));

		assertThat(runId).isEmpty();
		assertThat(conversations.messages("user-1", conversationId)).extracting(Message::role, Message::content).containsExactly(
				org.assertj.core.groups.Tuple.tuple("user", "oi"), org.assertj.core.groups.Tuple.tuple("assistant", "Ola!"));
		assertThat(runs.summaries(conversationId, null)).isEmpty();
	}

	@Test
	void tasksActivateTheContextAndStartARun() {
		Optional<String> runId = handler.apply(conversationId, messageId, decision(true, "Vou analisar.", WorkContextAction.ACTIVATE, "ctx-dados", "",
				List.of(new PlannedTask("Analisar setembro", "cartao em setembro", "agent-mensuracao"))));

		AgentRun run = runs.get(runId.orElseThrow());
		assertThat(run.status()).isEqualTo(AgentRunStatus.RUNNING);
		assertThat(run.tasks()).singleElement().satisfies(task -> {
			assertThat(task.subagentSelected()).isEqualTo("agent-mensuracao");
			assertThat(task.input()).containsEntry("enriched_content", "cartao em setembro");
		});
		assertThat(contexts.active(conversationId)).get().satisfies(active -> {
			assertThat(active.workContextId()).isEqualTo("ctx-dados");
			assertThat(active.lastAgentRunId()).isEqualTo(run.id());
		});
		assertThat(runs.summaries(conversationId, messageId)).singleElement().satisfies(summary -> assertThat(summary.workContextId()).isEqualTo("ctx-dados"));
	}

	@Test
	void subagentOutsideTheSelectedContextIsRejected() {
		assertThatThrownBy(() -> handler.apply(conversationId, messageId, decision(true, "", WorkContextAction.ACTIVATE, "ctx-dados", "",
				List.of(new PlannedTask("x", "y", "agent-outro"))))).isInstanceOf(IllegalStateException.class);
		assertThat(runs.summaries(conversationId, null)).isEmpty();
	}

	@Test
	void tasksWithoutAnyContextAreRejected() {
		assertThatThrownBy(() -> handler.apply(conversationId, messageId, decision(true, "", WorkContextAction.NONE, "", "",
				List.of(new PlannedTask("x", "y", "agent-mensuracao"))))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void contextActionIsIgnoredWhenThereAreNoTasks() {
		Optional<String> runId = handler.apply(conversationId, messageId, decision(false, "Claro.", WorkContextAction.KEEP, "", "", List.of()));

		assertThat(runId).isEmpty();
		assertThat(contexts.active(conversationId)).isEmpty();
		assertThat(conversations.recent(conversationId, 1)).singleElement().satisfies(message -> assertThat(message.content()).isEqualTo("Claro."));
	}

	@Test
	void rejectedPlanLeavesNoTrace() {
		String requestId = humanInput.create(conversationId, "run-1", "task-1", "Qual segmento?").id();
		Artifact insight = publishInsight("Insight setembro");

		assertThatThrownBy(() -> handler.apply(conversationId, messageId, new PlannerDecision(true, "", WorkContextAction.ACTIVATE, "ctx-dados", requestId,
				insight.id(), List.of(new PlannedTask("x", "y", "agent-outro"))))).isInstanceOf(IllegalStateException.class);

		assertThat(humanInput.pendingForConversation(conversationId)).hasSize(1);
		assertThat(contexts.definitionIds(conversationId)).isEmpty();
		assertThat(runs.summaries(conversationId, null)).isEmpty();
	}

	@Test
	void switchingContextPausesTheActiveOneAndReturningReusesItsInstance() {
		var dados = contexts.activate(conversationId, "ctx-dados");
		var outro = contexts.activate(conversationId, "ctx-outro");

		assertThat(contexts.active(conversationId)).get().extracting(c -> c.id()).isEqualTo(outro.id());
		assertThat(contexts.paused(conversationId)).extracting(c -> c.id()).containsExactly(dados.id());

		var back = contexts.activate(conversationId, "ctx-dados");

		assertThat(back.id()).isEqualTo(dados.id());
		assertThat(contexts.paused(conversationId)).extracting(c -> c.id()).containsExactly(outro.id());
		assertThat(contexts.definitionIds(conversationId)).hasSize(2);
		assertThat(contexts.paused(conversationId)).allSatisfy(c -> assertThat(c.status()).isEqualTo(ConversationWorkContextStatus.PAUSED));
	}

	@Test
	void answeringAPendingRequestMarksItAnsweredOnlyWithinTheConversation() {
		String requestId = humanInput.create(conversationId, "run-1", "task-1", "Qual segmento?").id();
		humanInput.answer("other-conversation", requestId, messageId);
		assertThat(humanInput.pendingForConversation(conversationId)).hasSize(1);

		handler.apply(conversationId, messageId, decision(false, "Entendi.", WorkContextAction.NONE, "", requestId, List.of()));

		assertThat(humanInput.pendingForConversation(conversationId)).isEmpty();
		assertThat(mongo.findById(requestId, com.lamorim.hubdeia.domain.model.HumanInputRequest.class)).satisfies(request -> {
			assertThat(request.status()).isEqualTo(HumanInputRequestStatus.ANSWERED);
			assertThat(request.answerMessageId()).isEqualTo(messageId);
		});
	}

	@Test
	void reactivatingAnArtifactAnswersWithAConfirmationWhenThePlannerSaidNothing() {
		Artifact old = publishInsight("Insight setembro");
		publishInsight("Insight jovens");

		handler.apply(conversationId, messageId, decisionReactivating("", old.id()));

		assertThat(artifacts.list(conversationId)).filteredOn(view -> view.id().equals(old.id())).singleElement()
				.satisfies(view -> assertThat(view.status().name()).isEqualTo("ACTIVE"));
		assertThat(conversations.recent(conversationId, 1)).singleElement().satisfies(message -> {
			assertThat(message.role()).isEqualTo("assistant");
			assertThat(message.content()).contains("Insight setembro").contains("versao 1");
		});
	}

	@Test
	void unknownArtifactIdNeverLetsThePlannerClaimARestore() {
		handler.apply(conversationId, messageId, decisionReactivating("Voltei para o briefing anterior.", "nao-existe"));

		assertThat(conversations.recent(conversationId, 1)).singleElement()
				.satisfies(message -> assertThat(message.content()).isEqualTo("Nao encontrei essa versao para retomar."));
	}

	private Artifact publishInsight(String title) {
		AgentRun run = new AgentRun("run-x", conversationId, AgentRunStatus.RUNNING, List.of(), List.of(), List.of(), messageId, null, null, "", Instant.now(), null);
		Task task = Task.create("task-" + System.nanoTime(), title, "agent-mensuracao", "", 3, Instant.now());
		return artifacts.publish(run, task, new TaskExecutionResult(task.id(), "agent-mensuracao", Map.of("content", title), List.of(), List.of(), List.of(), List.of()))
				.orElseThrow();
	}

	private PlannerDecision decision(boolean createTasks, String message, WorkContextAction action, String contextId, String requestId, List<PlannedTask> tasks) {
		return new PlannerDecision(createTasks, message, action, contextId, requestId, "", tasks);
	}

	private PlannerDecision decisionReactivating(String message, String artifactId) {
		return new PlannerDecision(false, message, WorkContextAction.NONE, "", "", artifactId, List.of());
	}
}
