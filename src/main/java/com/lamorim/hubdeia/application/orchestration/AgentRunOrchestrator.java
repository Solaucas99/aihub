package com.lamorim.hubdeia.application.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.HumanInputService;
import com.lamorim.hubdeia.application.communication.CommunicatorAgent;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.application.validation.TaskValidationResult;
import com.lamorim.hubdeia.application.validation.TaskValidatorAgent;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;
import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

/** Runs the tasks of an agent run in parallel, validates each output and consolidates the final answer. */
@Component
public class AgentRunOrchestrator {

	private static final Logger logger = LoggerFactory.getLogger(AgentRunOrchestrator.class);

	private final AgentRunService runs;
	private final SubagentTaskExecutor executor;
	private final TaskValidatorAgent validator;
	private final CommunicatorAgent communicator;
	private final HumanInputService humanInput;
	private final ArtifactService artifacts;
	private final SubagentRepository subagents;
	private final TaskDependencyResolver dependencies;
	private final int parallelism;

	public AgentRunOrchestrator(
			AgentRunService runs,
			SubagentTaskExecutor executor,
			TaskValidatorAgent validator,
			CommunicatorAgent communicator,
			HumanInputService humanInput,
			ArtifactService artifacts,
			SubagentRepository subagents,
			TaskDependencyResolver dependencies,
			@Value("${hub.agent-run.task-parallelism:4}") int parallelism
	) {
		this.runs = runs;
		this.executor = executor;
		this.validator = validator;
		this.communicator = communicator;
		this.humanInput = humanInput;
		this.artifacts = artifacts;
		this.subagents = subagents;
		this.dependencies = dependencies;
		this.parallelism = Math.max(1, parallelism);
	}

	public void execute(String conversationId, String runId) {
		logger.info("Executing AgentRun. conversationId={}, agentRunId={}", conversationId, runId);
		try {
			List<Task> pending = runs.get(runId).tasks().stream().filter(task -> task.status() == TaskStatus.NEW).toList();
			if (!pending.isEmpty()) {
				runs.progress(runId, "TASKS_RUNNING", "Executando tasks planejadas");
				runRespectingDependencies(conversationId, runId, pending);
			}
			finalizeIfReady(conversationId, runId);
		} catch (Exception error) {
			logger.error("AgentRun failed. conversationId={}, agentRunId={}", conversationId, runId, error);
			runs.fail(conversationId, runId, "Falha executando AgentRun: " + error.getMessage());
		}
	}

	/**
	 * Independent tasks run in parallel. A task that consumes an artifact type starts only after the
	 * tasks of this run that produce it have finished, so it builds on their fresh result.
	 */
	private void runRespectingDependencies(String conversationId, String runId, List<Task> tasks) {
		Map<String, Subagent> subagentsById = subagents.findAllById(tasks.stream().map(Task::subagentSelected).distinct().toList()).stream()
				.collect(Collectors.toMap(Subagent::id, Function.identity()));
		TaskDependencyResolver.Plan plan = dependencies.resolve(tasks, subagentsById);
		try (ExecutorService pool = Executors.newFixedThreadPool(Math.min(parallelism, tasks.size()))) {
			Map<String, CompletableFuture<Void>> futures = new HashMap<>();
			for (Task task : plan.order()) {
				Set<String> prerequisites = plan.prerequisites().getOrDefault(task.id(), Set.of());
				CompletableFuture<?>[] waitFor = prerequisites.stream().map(futures::get).toArray(CompletableFuture[]::new);
				futures.put(task.id(), CompletableFuture.allOf(waitFor)
						.thenRunAsync(() -> runTask(conversationId, runId, task.id(), prerequisites), pool));
			}
			CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();
		}
	}

	private void runTask(String conversationId, String runId, String taskId, Set<String> prerequisites) {
		boolean prerequisitesApproved = prerequisites.stream().allMatch(id -> runs.task(runId, id).isApproved());
		if (!prerequisitesApproved && runs.isRunning(runId)) {
			runs.finishTask(runId, taskId, TaskStatus.CANCELLED);
			runs.progress(runId, "TASK_SKIPPED", "Task nao executada: depende de um resultado que nao foi aprovado");
			return;
		}
		executeTask(conversationId, runId, taskId);
	}

	/** Attempts the task until it is settled or the run ends (cancelled or failed elsewhere). */
	private void executeTask(String conversationId, String runId, String taskId) {
		try {
			boolean settled = false;
			while (!settled && runs.isRunning(runId)) {
				settled = attempt(conversationId, runId, taskId);
			}
		} catch (Exception error) {
			logger.error("Task execution failed. conversationId={}, agentRunId={}, taskId={}", conversationId, runId, taskId, error);
			runs.finishTask(runId, taskId, TaskStatus.FAILED);
			runs.fail(conversationId, runId, "Falha executando task: " + error.getMessage());
		}
	}

	/** One execute + validate cycle. Returns true when the task is settled and needs no further attempt. */
	private boolean attempt(String conversationId, String runId, String taskId) {
		Task task = runs.task(runId, taskId);
		runs.markTaskRunning(runId, taskId);
		runs.progress(runId, "TASK_RUNNING", "Executando task: " + task.description());
		TaskExecutionResult result = executor.execute(conversationId, runId, task,
				(status, message) -> runs.progress(runId, status, message));
		runs.recordTaskResult(conversationId, runId, task, result);

		runs.progress(runId, "TASK_VALIDATING", "Validando task: " + task.description());
		Task validated = runs.task(runId, taskId);
		TaskValidationResult validation = validator.validate(conversationId, runId, validated);
		runs.recordValidation(runId, validated, validation);
		return route(conversationId, runId, validated, validation, result);
	}

	private boolean route(String conversationId, String runId, Task task, TaskValidationResult validation, TaskExecutionResult result) {
		if (!runs.isRunning(runId)) {
			return true; // cancelled or failed while this task was in flight: no request, artifact or retry
		}
		switch (validation.status()) {
			case CONTINUE -> approve(conversationId, runId, task, result);
			case RETRY -> {
				if (task.attempts() < task.maxAttempts()) {
					runs.prepareRetry(runId, task.id(), validation.feedback());
					runs.progress(runId, "TASK_RETRY", "Retry da task: " + task.description());
					return false;
				}
				runs.finishTask(runId, task.id(), TaskStatus.CANCELLED);
				runs.progress(runId, "TASK_CANCELLED", "Task cancelada por limite de tentativas");
			}
			case HITL -> {
				humanInput.create(conversationId, runId, task.id(), validation.messageToUser());
				runs.finishTask(runId, task.id(), TaskStatus.FINISHED);
				runs.progress(runId, "HITL_PENDING", "Aguardando informacao do usuario");
			}
			case CANCEL -> {
				String intro = communicator.introduceCancelledRun(validation.feedback());
				runs.cancel(conversationId, runId, List.of(new FinalAnswerBlock("communicator", "Encerramento", intro, null, null)), "AgentRun cancelado.");
			}
		}
		return true;
	}

	private void approve(String conversationId, String runId, Task task, TaskExecutionResult result) {
		runs.finishTask(runId, task.id(), TaskStatus.FINISHED);
		artifacts.publish(runs.get(runId), task, result);
		runs.progress(runId, "TASK_FINISHED", "Task aprovada: " + task.description());
	}

	private void finalizeIfReady(String conversationId, String runId) {
		AgentRun run = runs.get(runId);
		boolean allSettled = run.tasks().stream().allMatch(task -> task.status() == TaskStatus.FINISHED || task.status() == TaskStatus.CANCELLED);
		if (!allSettled || !runs.isRunning(runId)) {
			return;
		}
		List<Task> approved = run.tasks().stream().filter(Task::isApproved).toList();
		List<Task> cancelled = run.tasks().stream().filter(task -> task.status() == TaskStatus.CANCELLED).toList();
		List<HumanInputRequest> pending = humanInput.pendingForRun(runId);
		runs.progress(runId, "FINALIZING", "Consolidando resposta");
		runs.finish(conversationId, runId, finalBlocks(approved, cancelled, pending),
				pending.isEmpty() ? "AgentRun finalizado." : "Resposta parcial com pendencias.");
	}

	private List<FinalAnswerBlock> finalBlocks(List<Task> approved, List<Task> cancelled, List<HumanInputRequest> pending) {
		List<FinalAnswerBlock> blocks = new ArrayList<>();
		String intro = pending.isEmpty() ? communicator.introduceFinishedRun(approved, cancelled) : communicator.introducePartialRun(approved, pending);
		blocks.add(new FinalAnswerBlock("communicator", "Resumo", intro, null, null));
		approved.forEach(task -> blocks.add(new FinalAnswerBlock("subagent", task.description(), outputText(task.output()), task.id(), task.subagentSelected())));
		if (!pending.isEmpty()) {
			String questions = String.join("\n", pending.stream().map(request -> "- " + request.question()).toList());
			blocks.add(new FinalAnswerBlock("communicator", "Pendencias", "## Ainda preciso de voce\n" + questions, null, null));
		}
		return blocks;
	}

	/** Assembles the text and visuals of the output into the markdown people read. */
	private String outputText(Map<String, Object> output) {
		return TaskOutput.from(output).markdown();
	}
}
