package com.lamorim.hubdeia.application.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.ArtifactView;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.HumanInputService;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.communication.CommunicatorAgent;
import com.lamorim.hubdeia.application.planner.PlannedTask;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.application.validation.TaskValidationResult;
import com.lamorim.hubdeia.application.validation.TaskValidationStatus;
import com.lamorim.hubdeia.application.validation.TaskValidatorAgent;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

/** The orchestrator runs against real Mongo; only the three LLM-backed collaborators are replaced. */
class AgentRunOrchestratorTest extends MongoIntegrationTest {

	@MockitoBean
	SubagentTaskExecutor executor;
	@MockitoBean
	TaskValidatorAgent validator;
	@MockitoBean
	CommunicatorAgent communicator;

	@Autowired
	AgentRunOrchestrator orchestrator;
	@Autowired
	AgentRunService runs;
	@Autowired
	ConversationService conversations;
	@Autowired
	WorkContextService contexts;
	@Autowired
	HumanInputService humanInput;
	@Autowired
	ArtifactService artifacts;
	@Autowired
	SubagentRepository subagents;
	@Autowired
	WorkContextRepository workContextDefinitions;

	String conversationId;
	String messageId;
	ConversationWorkContext context;

	@BeforeEach
	void setUp() {
		subagents.save(new Subagent("agent-mensuracao", "Mensuracao", "", "", List.of(), List.of(), List.of("INSIGHT"), List.of(), true));
		subagents.save(new Subagent("agent-campanhas", "Campanhas", "", "", List.of(), List.of(), List.of("BRIEFING"), List.of("INSIGHT"), true));
		workContextDefinitions.save(new WorkContext("ctx-dados", "DADOS", "Dados", "", "", List.of("agent-mensuracao"), true));
		var accepted = conversations.create("user-1", "Como foi a campanha?");
		conversationId = accepted.conversationId();
		messageId = accepted.messageId();
		context = contexts.activate(conversationId, "ctx-dados");
		when(communicator.introduceFinishedRun(any(), any())).thenReturn("Resumo final");
		when(communicator.introducePartialRun(any(), any())).thenReturn("Resumo parcial");
		when(communicator.introduceCancelledRun(any())).thenReturn("Encerrei a execucao");
		when(executor.execute(any(), any(), any(), any())).thenAnswer(call -> okResult(call.getArgument(2)));
	}

	@Test
	void approvedTaskFinishesTheRunPublishesAnArtifactAndAnswersInTheChat() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.CONTINUE, ""));
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		AgentRun run = runs.get(runId);
		assertThat(run.status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(run.tasks()).singleElement().satisfies(task -> {
			assertThat(task.status()).isEqualTo(TaskStatus.FINISHED);
			assertThat(task.attempts()).isEqualTo(1);
			assertThat(task.validations()).hasSize(1);
		});
		assertThat(run.finalAnswer()).extracting(block -> block.type()).containsExactly("communicator", "subagent");
		assertThat(chat()).containsSubsequence("Como foi a campanha?", "Resumo final", "resultado de Analisar cartao");
		assertThat(artifacts.list(conversationId)).singleElement().satisfies(view -> {
			assertThat(view.type()).isEqualTo("INSIGHT");
			assertThat(view.status()).isEqualTo(ArtifactStatus.ACTIVE);
			assertThat(view.content()).isEqualTo("resultado de Analisar cartao");
		});
		assertThat(runs.status(conversationId, runId).status()).isEqualTo("FINISHED");
		assertThat(contexts.active(conversationId)).get().satisfies(active -> assertThat(active.lastAgentRunId()).isEqualTo(runId));
	}

	@Test
	void retryReceivesTheValidatorFeedbackAndStopsAtTheAttemptLimit() {
		when(validator.validate(any(), any(), any()))
				.thenReturn(verdict(TaskValidationStatus.RETRY, "faltou o periodo"))
				.thenReturn(verdict(TaskValidationStatus.CONTINUE, ""));
		List<Task> seen = new CopyOnWriteArrayList<>();
		doAnswer(call -> {
			seen.add(call.getArgument(2));
			return okResult(call.getArgument(2));
		}).when(executor).execute(any(), any(), any(), any());
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		assertThat(seen).hasSize(2);
		assertThat(seen.get(0).input()).doesNotContainKey("validator_feedback");
		assertThat(seen.get(1).input()).containsEntry("validator_feedback", "faltou o periodo");
		Task task = runs.get(runId).tasks().getFirst();
		assertThat(task.attempts()).isEqualTo(2);
		assertThat(task.validations()).extracting(validation -> validation.status()).containsExactly("RETRY", "CONTINUE");
		assertThat(runs.get(runId).status()).isEqualTo(AgentRunStatus.FINISHED);
	}

	@Test
	void taskThatKeepsFailingValidationIsCancelledAndTheRunStillFinishes() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.RETRY, "ruim"));
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		verify(executor, times(3)).execute(any(), any(), any(), any());
		AgentRun run = runs.get(runId);
		assertThat(run.tasks().getFirst().status()).isEqualTo(TaskStatus.CANCELLED);
		assertThat(run.status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(run.finalAnswer()).extracting(block -> block.type()).containsExactly("communicator");
		assertThat(artifacts.list(conversationId)).isEmpty();
	}

	@Test
	void cancelFromOneTaskEndsTheRunForGoodEvenWhenAnotherTaskFinishesLater() {
		when(validator.validate(any(), any(), any())).thenAnswer(call ->
				"lenta".equals(((Task) call.getArgument(2)).description()) ? verdict(TaskValidationStatus.CONTINUE, "") : verdict(TaskValidationStatus.CANCEL, "impossivel"));
		doAnswer(call -> {
			Task task = call.getArgument(2);
			if ("lenta".equals(task.description())) {
				Thread.sleep(600);
			}
			return okResult(task);
		}).when(executor).execute(any(), any(), any(), any());
		String runId = startRun("lenta", "rapida");

		orchestrator.execute(conversationId, runId);

		AgentRun run = runs.get(runId);
		assertThat(run.status()).isEqualTo(AgentRunStatus.CANCELLED);
		assertThat(run.finishedAt()).isNotNull();
		assertThat(chat().stream().filter("Encerrei a execucao"::equals).count()).isEqualTo(1);
		assertThat(chat()).doesNotContain("Resumo final");
		assertThat(runs.status(conversationId, runId).status()).isEqualTo("CANCELLED");
		assertThat(artifacts.list(conversationId)).as("a task approved after the run ended publishes nothing").isEmpty();
		assertThat(humanInput.pendingForConversation(conversationId)).isEmpty();
	}

	@Test
	void humanInputRequestEndsTheRunAsPartialWithThePendingQuestion() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.HITL, "Qual segmento?"));
		String runId = startRun("Analisar clientes");

		orchestrator.execute(conversationId, runId);

		assertThat(runs.get(runId).status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(humanInput.pendingForConversation(conversationId)).singleElement().satisfies(request -> {
			assertThat(request.question()).isEqualTo("Qual segmento?");
			assertThat(request.agentRunId()).isEqualTo(runId);
		});
		assertThat(chat()).anyMatch(content -> content.contains("Qual segmento?"));
		assertThat(chat()).contains("Resumo parcial");
	}

	@Test
	void unexpectedErrorFailsTheRunAndTellsTheUser() {
		doThrow(new IllegalStateException("provider fora do ar")).when(executor).execute(any(), any(), any(), any());
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		AgentRun run = runs.get(runId);
		assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
		assertThat(run.agentRunLastMessage()).contains("provider fora do ar");
		assertThat(chat()).last().asString().contains("Nao foi possivel concluir");
		assertThat(runs.status(conversationId, runId).status()).isEqualTo("FAILED");
	}

	@Test
	void aTaskThatConsumesAnArtifactWaitsForTheTaskThatProducesItInTheSameRun() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.CONTINUE, ""));
		List<String> started = new CopyOnWriteArrayList<>();
		List<Integer> insightsVisibleWhenBriefingStarted = new CopyOnWriteArrayList<>();
		doAnswer(call -> {
			Task task = call.getArgument(2);
			started.add(task.description());
			if ("briefing".equals(task.description())) {
				insightsVisibleWhenBriefingStarted.add(artifacts.list(conversationId).size());
			} else {
				Thread.sleep(400);
			}
			return okResult(task);
		}).when(executor).execute(any(), any(), any(), any());
		String runId = runs.start(conversationId, messageId, context, null, "Vou analisar.", List.of(
				new PlannedTask("briefing", "criar briefing", "agent-campanhas"),
				new PlannedTask("insight", "analisar", "agent-mensuracao"))).id();

		orchestrator.execute(conversationId, runId);

		assertThat(started).containsExactly("insight", "briefing");
		assertThat(insightsVisibleWhenBriefingStarted).as("the briefing started after the insight was published").containsExactly(1);
		assertThat(runs.get(runId).status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(artifacts.list(conversationId)).extracting(ArtifactView::type).containsExactlyInAnyOrder("INSIGHT", "BRIEFING");
	}

	@Test
	void aTaskWhosePrerequisiteWasNotApprovedIsSkippedInsteadOfUsingAnOldResult() {
		when(validator.validate(any(), any(), any())).thenAnswer(call ->
				"insight".equals(((Task) call.getArgument(2)).description()) ? verdict(TaskValidationStatus.RETRY, "ruim") : verdict(TaskValidationStatus.CONTINUE, ""));
		List<String> started = new CopyOnWriteArrayList<>();
		doAnswer(call -> {
			Task task = call.getArgument(2);
			started.add(task.description());
			return okResult(task);
		}).when(executor).execute(any(), any(), any(), any());
		String runId = runs.start(conversationId, messageId, context, null, "Vou analisar.", List.of(
				new PlannedTask("insight", "analisar", "agent-mensuracao"),
				new PlannedTask("briefing", "criar briefing", "agent-campanhas"))).id();

		orchestrator.execute(conversationId, runId);

		assertThat(started).containsOnly("insight").hasSize(3);
		AgentRun run = runs.get(runId);
		assertThat(run.tasks()).extracting(Task::description, Task::status).containsExactlyInAnyOrder(
				org.assertj.core.groups.Tuple.tuple("insight", TaskStatus.CANCELLED), org.assertj.core.groups.Tuple.tuple("briefing", TaskStatus.CANCELLED));
		assertThat(run.status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(run.logs()).extracting(log -> log.executionStatus()).contains("TASK_SKIPPED");
		assertThat(artifacts.list(conversationId)).isEmpty();
	}

	@Test
	void visualsStayApartFromTheTextInStorageAndComeTogetherOnlyForPeople() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.CONTINUE, ""));
		Visual table = new Visual("viz1", "Desempenho", VisualFormat.HTML, "<section class=\"data-table\"><table></table></section>");
		doAnswer(call -> {
			Task task = call.getArgument(2);
			return new TaskExecutionResult(task.id(), "agent-mensuracao",
					new TaskOutput("Resumo.\n\n[[visualization:viz1]]\n\nFim.", List.of(table)).toMap(), List.of(), List.of(), List.of(), List.of());
		}).when(executor).execute(any(), any(), any(), any());
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		ArtifactView artifact = artifacts.list(conversationId).getFirst();
		assertThat(artifact.content()).contains("[visual: Desempenho]").doesNotContain("<section");
		assertThat(artifact.renderedContent()).contains("<section class=\"data-table\">");
		assertThat(chat()).anyMatch(message -> message.contains("<section class=\"data-table\">"));
		assertThat(mongo.findById(artifact.id(), com.lamorim.hubdeia.domain.model.Artifact.class).content()).doesNotContain("<section");
	}

	private String startRun(String... descriptions) {
		List<PlannedTask> planned = new ArrayList<>();
		for (String description : descriptions) {
			planned.add(new PlannedTask(description, "conteudo de " + description, "agent-mensuracao"));
		}
		return runs.start(conversationId, messageId, context, null, "Vou analisar.", planned).id();
	}

	private TaskExecutionResult okResult(Task task) {
		return new TaskExecutionResult(task.id(), "agent-mensuracao", Map.of("content", "resultado de " + task.description()), List.of(), List.of(), List.of(), List.of());
	}

	private TaskValidationResult verdict(TaskValidationStatus status, String text) {
		return new TaskValidationResult(status, text, text, Instant.now());
	}

	private List<String> chat() {
		return conversations.messages("user-1", conversationId).stream().map(Message::content).toList();
	}
}
