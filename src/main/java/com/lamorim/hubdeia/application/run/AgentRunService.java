package com.lamorim.hubdeia.application.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.lamorim.hubdeia.api.dto.AgentRunLogEntry;
import com.lamorim.hubdeia.api.dto.AgentRunStatusResponse;
import com.lamorim.hubdeia.api.dto.AgentRunSummaryResponse;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.TaskExecutionService;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.application.planner.PlannedTask;
import com.lamorim.hubdeia.application.validation.TaskValidationResult;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunLog;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.TaskValidation;
import com.lamorim.hubdeia.domain.repository.AgentRunRepository;

/** Lifecycle of an agent run: every transition is persisted, and polling reads the same document. */
@Service
public class AgentRunService {

	private static final String FAILURE_MESSAGE = "Nao foi possivel concluir esta analise agora. Tente novamente em instantes.";

	private final AgentRunStore store;
	private final AgentRunRepository runs;
	private final ConversationService conversations;
	private final TaskExecutionService executions;
	private final WorkContextService contexts;
	private final int taskMaxAttempts;

	public AgentRunService(
			AgentRunStore store,
			AgentRunRepository runs,
			ConversationService conversations,
			TaskExecutionService executions,
			WorkContextService contexts,
			@Value("${hub.agent-run.task-max-attempts:3}") int taskMaxAttempts
	) {
		this.store = store;
		this.runs = runs;
		this.conversations = conversations;
		this.executions = executions;
		this.contexts = contexts;
		this.taskMaxAttempts = Math.max(1, taskMaxAttempts);
	}

	public AgentRun start(String conversationId, String triggeringMessageId, ConversationWorkContext context,
			String continuedRequestId, String plannerMessage, List<PlannedTask> plannedTasks) {
		Instant now = Instant.now();
		List<Task> tasks = plannedTasks.stream()
				.map(planned -> Task.create(UUID.randomUUID().toString(), planned.description(), planned.subagentId(), planned.enrichedContent(), taskMaxAttempts, now))
				.toList();
		AgentRunLog log = AgentRunLog.info(AgentRunStatus.RUNNING, "TASKS_RUNNING", "Tasks planejadas.");
		AgentRun run = store.insert(new AgentRun(UUID.randomUUID().toString(), conversationId, AgentRunStatus.RUNNING, tasks, List.of(log), List.of(),
				triggeringMessageId, context.id(), StringUtils.hasText(continuedRequestId) ? continuedRequestId : null, plannerMessage, now, null));
		contexts.recordRun(context.id(), run.id());
		return run;
	}

	public AgentRun get(String runId) {
		return store.require(runId);
	}

	public Task task(String runId, String taskId) {
		return store.requireTask(runId, taskId);
	}

	public boolean isRunning(String runId) {
		return store.isRunning(runId);
	}

	/** The most recent runs of the conversation, oldest first. */
	public List<AgentRun> recent(String conversationId, int limit) {
		return runs.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit)).reversed();
	}

	public String lastMessage(String runId) {
		return runId == null ? "" : store.find(runId).map(AgentRun::agentRunLastMessage).orElse("");
	}

	public void progress(String runId, String executionStatus, String message) {
		store.appendLog(runId, AgentRunLog.info(AgentRunStatus.RUNNING, executionStatus, message));
	}

	public void markTaskRunning(String runId, String taskId) {
		store.markTaskRunning(runId, taskId);
	}

	public void recordTaskResult(String conversationId, String runId, Task task, TaskExecutionResult result) {
		executions.record(conversationId, runId, task, result);
		store.recordTaskOutput(runId, task.id(), result.output(), task.attempts() + 1);
	}

	public void recordValidation(String runId, Task task, TaskValidationResult validation) {
		store.appendTaskValidation(runId, task.id(),
				new TaskValidation(validation.status().name(), validation.feedback(), validation.messageToUser(), task.attempts(), validation.validatedAt()));
	}

	public void finishTask(String runId, String taskId, TaskStatus status) {
		store.finishTask(runId, taskId, status);
	}

	public void prepareRetry(String runId, String taskId, String feedback) {
		store.resetTaskForRetry(runId, taskId, feedback);
	}

	public void finish(String conversationId, String runId, List<FinalAnswerBlock> blocks, String message) {
		terminate(conversationId, runId, AgentRunStatus.FINISHED, blocks, AgentRunLog.info(AgentRunStatus.FINISHED, "FINISHED", message));
	}

	public void cancel(String conversationId, String runId, List<FinalAnswerBlock> blocks, String message) {
		terminate(conversationId, runId, AgentRunStatus.CANCELLED, blocks, AgentRunLog.info(AgentRunStatus.CANCELLED, "CANCELLED", message));
	}

	/** Ends the run and tells the user in the chat, so a failure is never silent. */
	public void fail(String conversationId, String runId, String message) {
		List<FinalAnswerBlock> blocks = List.of(new FinalAnswerBlock("communicator", "Falha", FAILURE_MESSAGE, null, null));
		terminate(conversationId, runId, AgentRunStatus.FAILED, blocks, AgentRunLog.error(AgentRunStatus.FAILED, "FAILED", message));
	}

	public AgentRunStatusResponse status(String conversationId, String runId) {
		return toStatusResponse(require(conversationId, runId));
	}

	public List<AgentRunSummaryResponse> summaries(String conversationId, String triggeringMessageId) {
		Map<String, String> definitionIds = contexts.definitionIds(conversationId);
		List<AgentRun> found = StringUtils.hasText(triggeringMessageId)
				? runs.findByConversationIdAndTriggeringMessageIdOrderByCreatedAtAsc(conversationId, triggeringMessageId)
				: runs.findByConversationIdOrderByCreatedAtAsc(conversationId);
		return found.stream()
				.map(run -> new AgentRunSummaryResponse(run.id(), run.status().name(), run.triggeringMessageId(),
						definitionIds.get(run.conversationWorkContextId()), run.createdAt(), run.finishedAt()))
				.toList();
	}

	private void terminate(String conversationId, String runId, AgentRunStatus status, List<FinalAnswerBlock> blocks, AgentRunLog log) {
		if (store.complete(runId, status, blocks, log)) {
			conversations.appendAssistantBlocks(conversationId, blocks);
		}
	}

	private AgentRun require(String conversationId, String runId) {
		return runs.findByIdAndConversationId(runId, conversationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AgentRun not found."));
	}

	private AgentRunStatusResponse toStatusResponse(AgentRun run) {
		List<AgentRunLogEntry> logs = run.logs().stream().map(log -> new AgentRunLogEntry(log.at().toString(), log.level(), log.message())).toList();
		Instant updatedAt = run.finishedAt() != null ? run.finishedAt() : run.logs().isEmpty() ? run.createdAt() : run.logs().getLast().at();
		return new AgentRunStatusResponse(run.conversationId(), run.id(), run.status().name(), run.status().name(), run.agentRunLastMessage(),
				logs, run.finalAnswer(), updatedAt.toString(), "database");
	}
}
