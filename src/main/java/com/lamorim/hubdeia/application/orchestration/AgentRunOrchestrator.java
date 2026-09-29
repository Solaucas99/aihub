package com.lamorim.hubdeia.application.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.communication.CommunicatorAgent;
import com.lamorim.hubdeia.application.validation.TaskValidationResult;
import com.lamorim.hubdeia.application.validation.TaskValidationStatus;
import com.lamorim.hubdeia.application.validation.TaskValidatorAgent;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;
import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;

@Component
public class AgentRunOrchestrator {

	private static final Logger logger = LoggerFactory.getLogger(AgentRunOrchestrator.class);

	private final ConversationService conversations;
	private final SubagentTaskExecutor executor;
	private final TaskValidatorAgent validator;
	private final CommunicatorAgent communicator;
	private final int parallelism;

	public AgentRunOrchestrator(
			ConversationService conversations,
			SubagentTaskExecutor executor,
			TaskValidatorAgent validator,
			CommunicatorAgent communicator,
			@Value("${hub.agent-run.task-parallelism:4}") int parallelism
	) {
		this.conversations = conversations;
		this.executor = executor;
		this.validator = validator;
		this.communicator = communicator;
		this.parallelism = Math.max(1, parallelism);
	}

	public void execute(String userId, String conversationId, String runId) {
		logger.info("Executing AgentRun. conversationId={}, agentRunId={}", conversationId, runId);
		AgentRun run = conversations.findAgentRun(userId, conversationId, runId);
		List<Task> pending = safeTasks(run).stream().filter(task -> TaskStatus.NEW.name().equals(task.status())).toList();
		if (pending.isEmpty()) {
			finalizeIfReady(userId, conversationId, runId);
			return;
		}
		conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), "TASKS_RUNNING", "Executando tasks planejadas");
		ExecutorService pool = Executors.newFixedThreadPool(Math.min(parallelism, pending.size()));
		try {
			List<CompletableFuture<Void>> futures = pending.stream()
					.map(task -> CompletableFuture.runAsync(() -> executeTask(userId, conversationId, runId, task), pool))
					.toList();
			CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
		} finally {
			pool.shutdown();
		}
		finalizeIfReady(userId, conversationId, runId);
	}

	private void executeTask(String userId, String conversationId, String runId, Task initialTask) {
		Task task = initialTask;
		while (true) {
			try {
				logger.info("Executing task. conversationId={}, agentRunId={}, taskId={}, attempt={}", conversationId, runId, task.id(), task.attempts() + 1);
				conversations.markTaskRunning(userId, conversationId, runId, task.id());
				conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), "TASK_RUNNING", "Executando task: " + task.description());
				TaskExecutionResult result = executor.execute(conversations.findConversation(userId, conversationId), runId, task,
						(status, message) -> conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), status, message));
				conversations.recordTaskOutput(userId, conversationId, runId, result);
				conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), "TASK_VALIDATING", "Validando task: " + task.description());
				TaskValidationResult validation = validator.validate(conversations.findConversation(userId, conversationId), runId, task.id());
				conversations.recordTaskValidation(userId, conversationId, runId, task.id(), validation);
				if (route(userId, conversationId, runId, task, validation)) return;
				task = conversations.findTask(userId, conversationId, runId, task.id());
			} catch (Exception error) {
				logger.error("Task execution failed. conversationId={}, agentRunId={}, taskId={}", conversationId, runId, task.id(), error);
				conversations.failAgentRun(userId, conversationId, runId, "Falha executando task: " + error.getMessage());
				return;
			}
		}
	}

	private boolean route(String userId, String conversationId, String runId, Task task, TaskValidationResult validation) {
		if (validation.status() == TaskValidationStatus.CONTINUE) {
			conversations.markTaskFinished(userId, conversationId, runId, task.id());
			conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), "TASK_FINISHED", "Task aprovada: " + task.description());
			return true;
		}
		Task latest = conversations.findTask(userId, conversationId, runId, task.id());
		if (validation.status() == TaskValidationStatus.RETRY) {
			if (latest.attempts() < latest.maxAttempts()) {
				conversations.prepareTaskRetry(userId, conversationId, runId, task.id(), validation.feedback());
				conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), "TASK_RETRY", "Retry da task: " + task.description());
				return false;
			}
			conversations.markTaskCancelled(userId, conversationId, runId, task.id());
			conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), "TASK_CANCELLED", "Task cancelada por limite de tentativas");
			return true;
		}
		if (validation.status() == TaskValidationStatus.HITL) {
			conversations.createHumanInputRequest(userId, conversationId, runId, task.id(), validation.messageToUser());
			conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), "HITL_PENDING", "Aguardando informacao do usuario");
			return true;
		}
		String intro = communicator.introduceCancelledRun(validation.feedback());
		conversations.cancelAgentRun(userId, conversationId, runId, List.of(new FinalAnswerBlock("communicator", "Encerramento", intro, null, null)), "AgentRun cancelado.");
		return true;
	}

	private void finalizeIfReady(String userId, String conversationId, String runId) {
		MainConversationChatHistory conversation = conversations.findConversation(userId, conversationId);
		AgentRun run = conversations.findAgentRun(userId, conversationId, runId);
		if (!AgentRunStatus.RUNNING.name().equals(run.status())) return;
		List<Task> tasks = safeTasks(run);
		if (!tasks.stream().allMatch(task -> TaskStatus.FINISHED.name().equals(task.status()) || TaskStatus.CANCELLED.name().equals(task.status()))) return;
		List<Task> approved = tasks.stream().filter(this::approved).toList();
		List<Task> cancelled = tasks.stream().filter(task -> TaskStatus.CANCELLED.name().equals(task.status())).toList();
		List<HumanInputRequest> pending = safeRequests(conversation).stream().filter(request -> runId.equals(request.agentRunId()) && "PENDING".equals(request.status())).toList();
		conversations.recordAgentRunProgress(userId, conversationId, runId, AgentRunStatus.RUNNING.name(), "FINALIZING", "Consolidando resposta");
		List<FinalAnswerBlock> blocks = finalBlocks(approved, cancelled, pending);
		conversations.finishAgentRun(userId, conversationId, runId, blocks, pending.isEmpty() ? "AgentRun finalizado." : "Resposta parcial com pendencias.");
	}

	private List<FinalAnswerBlock> finalBlocks(List<Task> approved, List<Task> cancelled, List<HumanInputRequest> pending) {
		List<FinalAnswerBlock> blocks = new ArrayList<>();
		String intro = pending.isEmpty() ? communicator.introduceFinishedRun(approved, cancelled) : communicator.introducePartialRun(approved, pending);
		blocks.add(new FinalAnswerBlock("communicator", "Resumo", intro, null, null));
		approved.stream().map(task -> new FinalAnswerBlock("subagent", task.description(), output(task.output()), task.id(), task.subagentSelected())).forEach(blocks::add);
		if (!pending.isEmpty()) {
			String questions = pending.stream().map(request -> "- " + request.question()).reduce((first, second) -> first + "\n" + second).orElse("");
			blocks.add(new FinalAnswerBlock("communicator", "Pendencias", "## Ainda preciso de voce\n" + questions, null, null));
		}
		return blocks;
	}

	private boolean approved(Task task) {
		return task.validations() != null && !task.validations().isEmpty() && "CONTINUE".equals(task.validations().getLast().status());
	}
	private String output(Map<String, Object> value) { if (value == null || value.isEmpty()) return ""; Object content = value.get("content"); return content != null && StringUtils.hasText(content.toString()) ? content.toString() : new Document(value).toJson(); }
	private List<Task> safeTasks(AgentRun run) { return run.tasks() == null ? List.of() : run.tasks(); }
	private List<HumanInputRequest> safeRequests(MainConversationChatHistory conversation) { return conversation.humanInputRequests() == null ? List.of() : conversation.humanInputRequests(); }
}
