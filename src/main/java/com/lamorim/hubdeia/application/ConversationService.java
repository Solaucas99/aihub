package com.lamorim.hubdeia.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.lamorim.hubdeia.api.dto.AgentRunStatusResponse;
import com.lamorim.hubdeia.api.dto.AgentRunSummaryResponse;
import com.lamorim.hubdeia.api.dto.ConversationSummaryResponse;
import com.lamorim.hubdeia.api.dto.MessageAcceptedResponse;
import com.lamorim.hubdeia.api.dto.AgentRunLogEntry;
import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.application.planner.PlannedTask;
import com.lamorim.hubdeia.application.planner.PlannerDecision;
import com.lamorim.hubdeia.application.validation.TaskValidationResult;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.ConversationWorkContextStatus;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;
import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.HumanInputRequestStatus;
import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.TaskValidation;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.model.AgentRunLog;
import com.lamorim.hubdeia.domain.repository.MainConversationChatHistoryRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

@Service
public class ConversationService {

	private final MainConversationChatHistoryRepository conversations;
	private final WorkContextRepository workContexts;
	private final AgentRunStatusCache statusCache;
	private final int taskMaxAttempts;

	public ConversationService(
			MainConversationChatHistoryRepository conversations,
			WorkContextRepository workContexts,
			AgentRunStatusCache statusCache,
			@Value("${hub.agent-run.task-max-attempts:3}") int taskMaxAttempts
	) {
		this.conversations = conversations;
		this.workContexts = workContexts;
		this.statusCache = statusCache;
		this.taskMaxAttempts = Math.max(1, taskMaxAttempts);
	}

	public MessageAcceptedResponse create(String userId, String content) {
		requireText(content, "message is required.");
		Instant now = Instant.now();
		Message message = message("user", content, now);
		MainConversationChatHistory conversation = new MainConversationChatHistory(
				null, userId, content, List.of(message), List.of(), null, List.of(), List.of(), List.of(), List.of(), now, now);
		MainConversationChatHistory saved = conversations.save(conversation);
		return new MessageAcceptedResponse("ACCEPTED", saved.id(), message.id());
	}

	public MessageAcceptedResponse appendUserMessage(String userId, String conversationId, String content) {
		requireText(content, "message is required.");
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		Message message = message("user", content, Instant.now());
		conversations.save(withMessage(conversation, message));
		return new MessageAcceptedResponse("ACCEPTED", conversationId, message.id());
	}

	public void appendSystemFailure(String userId, String conversationId, String content) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		conversations.save(withMessage(conversation, message("assistant", content, Instant.now())));
	}

	public String applyPlannerDecision(String userId, String conversationId, String messageId, PlannerDecision decision) {
		MainConversationChatHistory current = findOwnedConversation(userId, conversationId);
		Instant now = Instant.now();
		MainConversationChatHistory withRequest = answerHumanRequest(current, decision.relatedHumanInputRequestId(), messageId, now);
		Activation activation = activateContext(withRequest, decision, now);
		MainConversationChatHistory conversation = activation.conversation();

		if (!decision.shouldCreateTasks() || decision.safeTasks().isEmpty()) {
			String response = StringUtils.hasText(decision.responseMessage()) ? decision.responseMessage() : "Posso ajudar com isso.";
			conversations.save(withMessage(conversation, message("assistant", response, now)));
			return null;
		}

		if (activation.context() == null) {
			throw new IllegalStateException("Planner criou tasks sem selecionar WorkContext.");
		}
		validateAgents(decision.safeTasks(), activation.context());
		String runId = UUID.randomUUID().toString();
		List<Task> tasks = decision.safeTasks().stream().map(task -> toTask(task, now)).toList();
		AgentRun run = new AgentRun(runId, AgentRunStatus.RUNNING.name(), tasks,
				List.of(new AgentRunLog(now, "INFO", "Tasks planejadas.", AgentRunStatus.RUNNING.name(), "TASKS_RUNNING")), List.of(), messageId,
				activation.context().id(), blankToNull(decision.relatedHumanInputRequestId()), decision.responseMessage(), now, null);
		MainConversationChatHistory saved = conversations.save(withAgentRun(conversation, run, now));
		statusCache.save(saved.id(), runId, AgentRunStatus.RUNNING.name(), "TASKS_RUNNING", "Tasks planejadas.");
		return runId;
	}

	public MainConversationChatHistory findConversation(String userId, String conversationId) {
		return findOwnedConversation(userId, conversationId);
	}

	public List<Message> findMessages(String userId, String conversationId) {
		return safeMessages(findOwnedConversation(userId, conversationId).messages());
	}

	public List<ConversationSummaryResponse> findSummaries(String userId) {
		return conversations.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(conversation -> new ConversationSummaryResponse(conversation.id(), conversation.chatLastMessage(), conversation.createdAt(), conversation.updatedAt()))
				.toList();
	}

	public List<AgentRunSummaryResponse> findAgentRuns(String userId, String conversationId, String messageId) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		return safeRuns(conversation).stream()
				.filter(run -> !StringUtils.hasText(messageId) || messageId.equals(run.triggeringMessageId()))
				.map(run -> new AgentRunSummaryResponse(run.id(), run.status(), run.triggeringMessageId(), workContextId(conversation, run), run.createdAt(), run.finishedAt()))
				.toList();
	}

	public AgentRunStatusResponse findAgentRunStatus(String userId, String conversationId, String runId) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		return statusCache.find(conversationId, runId).orElseGet(() -> {
			AgentRunStatusResponse status = statusFromMongo(conversation, runId);
			statusCache.restore(status);
			return status;
		});
	}

	public AgentRun findAgentRun(String userId, String conversationId, String runId) {
		return findRun(findOwnedConversation(userId, conversationId), runId);
	}

	public Task findTask(String userId, String conversationId, String runId, String taskId) {
		return findTask(findOwnedConversation(userId, conversationId), runId, taskId);
	}

	public synchronized void recordAgentRunProgress(String userId, String conversationId, String runId, String status, String executionStatus, String message) {
		Instant now = Instant.now();
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		MainConversationChatHistory updated = replaceRun(conversation, runId, run -> new AgentRun(run.id(), status, run.tasks(),
				append(run.logs(), new AgentRunLog(now, "INFO", message, status, executionStatus)), run.finalAnswer(), run.triggeringMessageId(),
				run.conversationWorkContextId(), run.continuedHumanInputRequestId(), message, run.createdAt(), run.finishedAt()), now);
		conversations.save(updated);
		statusCache.appendLog(conversationId, runId, status, executionStatus, message);
	}

	public synchronized void markTaskRunning(String userId, String conversationId, String runId, String taskId) {
		updateRun(userId, conversationId, runId, run -> new AgentRun(run.id(), AgentRunStatus.RUNNING.name(),
				replaceTask(run, taskId, task -> copyTask(task, TaskStatus.RUNNING.name(), task.input(), task.output(), task.validations(), task.attempts(), null)),
				run.logs(), run.finalAnswer(), run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), "Executando task.", run.createdAt(), null));
	}

	public synchronized void recordTaskOutput(String userId, String conversationId, String runId, TaskExecutionResult result) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		Task task = findTask(conversation, runId, result.taskId());
		Instant now = Instant.now();
		int attempt = Math.max(1, task.attempts() + 1);
		SubagentExecution execution = new SubagentExecution(UUID.randomUUID().toString(), conversationId, runId, result.taskId(), result.subagentId(),
				result.messages(), result.providerSessions(), result.toolCalls(), task.input(), result.output(), "OUTPUT_READY", attempt, now, now);
		MainConversationChatHistory updated = replaceRun(conversation, runId, run -> new AgentRun(run.id(), AgentRunStatus.RUNNING.name(),
				replaceTask(run, result.taskId(), value -> copyTask(value, TaskStatus.RUNNING.name(), value.input(), result.output(), value.validations(), attempt, null)),
				run.logs(), run.finalAnswer(), run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), "Output recebido, aguardando validacao.", run.createdAt(), null), now);
		updated = withExecution(updated, execution, now);
		updated = withProviderSessions(updated, result.providerSessions(), now);
		conversations.save(updated);
	}

	public synchronized void recordTaskValidation(String userId, String conversationId, String runId, String taskId, TaskValidationResult validation) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		Task task = findTask(conversation, runId, taskId);
		TaskValidation stored = new TaskValidation(validation.status().name(), validation.feedback(), validation.messageToUser(), task.attempts(), validation.validatedAt());
		updateRun(userId, conversationId, runId, run -> new AgentRun(run.id(), AgentRunStatus.RUNNING.name(),
				replaceTask(run, taskId, value -> copyTask(value, value.status(), value.input(), value.output(), append(value.validations(), stored), value.attempts(), value.finishedAt())),
				run.logs(), run.finalAnswer(), run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), "Task validada.", run.createdAt(), run.finishedAt()));
	}

	public synchronized void markTaskFinished(String userId, String conversationId, String runId, String taskId) {
		updateTaskStatus(userId, conversationId, runId, taskId, TaskStatus.FINISHED.name());
	}

	public synchronized void markTaskCancelled(String userId, String conversationId, String runId, String taskId) {
		updateTaskStatus(userId, conversationId, runId, taskId, TaskStatus.CANCELLED.name());
	}

	public synchronized void prepareTaskRetry(String userId, String conversationId, String runId, String taskId, String feedback) {
		updateRun(userId, conversationId, runId, run -> new AgentRun(run.id(), AgentRunStatus.RUNNING.name(),
				replaceTask(run, taskId, task -> copyTask(task, TaskStatus.NEW.name(), merge(task.input(), Map.of("validator_feedback", safeText(feedback))), Map.of(), task.validations(), task.attempts(), null)),
				run.logs(), run.finalAnswer(), run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), "Retry preparado.", run.createdAt(), null));
	}

	public synchronized void createHumanInputRequest(String userId, String conversationId, String runId, String taskId, String question) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		Instant now = Instant.now();
		HumanInputRequest request = new HumanInputRequest(UUID.randomUUID().toString(), runId, taskId,
				StringUtils.hasText(question) ? question : "Preciso de uma confirmacao para continuar.", HumanInputRequestStatus.PENDING.name(), null, now, null);
		MainConversationChatHistory withRequest = new MainConversationChatHistory(conversation.id(), conversation.userId(), conversation.chatLastMessage(), conversation.messages(),
				conversation.agentRuns(), conversation.activeWorkContextId(), conversation.workContexts(), append(conversation.humanInputRequests(), request), conversation.subagentsExecutions(), conversation.providerSessions(), conversation.createdAt(), now);
		conversations.save(replaceRun(withRequest, runId, run -> new AgentRun(run.id(), run.status(),
				replaceTask(run, taskId, task -> copyTask(task, TaskStatus.FINISHED.name(), task.input(), task.output(), task.validations(), task.attempts(), now)),
				run.logs(), run.finalAnswer(), run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), request.question(), run.createdAt(), run.finishedAt()), now));
	}

	public synchronized void finishAgentRun(String userId, String conversationId, String runId, List<FinalAnswerBlock> blocks, String message) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		Instant now = Instant.now();
		MainConversationChatHistory withMessages = appendAssistantBlocks(conversation, blocks, now);
		MainConversationChatHistory updated = replaceRun(withMessages, runId, run -> new AgentRun(run.id(), AgentRunStatus.FINISHED.name(), run.tasks(),
				append(run.logs(), new AgentRunLog(now, "INFO", message, AgentRunStatus.FINISHED.name(), "FINISHED")), blocks,
				run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), message, run.createdAt(), now), now);
		conversations.save(updated);
		statusCache.saveFinal(conversationId, runId, AgentRunStatus.FINISHED.name(), "FINISHED", message, blocks);
	}

	public synchronized void cancelAgentRun(String userId, String conversationId, String runId, List<FinalAnswerBlock> blocks, String message) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		Instant now = Instant.now();
		MainConversationChatHistory updated = replaceRun(appendAssistantBlocks(conversation, blocks, now), runId,
				run -> new AgentRun(run.id(), AgentRunStatus.CANCELLED.name(), run.tasks(),
					append(run.logs(), new AgentRunLog(now, "INFO", message, AgentRunStatus.CANCELLED.name(), "CANCELLED")), blocks,
					run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), message, run.createdAt(), now), now);
		conversations.save(updated);
		statusCache.saveFinal(conversationId, runId, AgentRunStatus.CANCELLED.name(), "CANCELLED", message, blocks);
	}

	public synchronized void failAgentRun(String userId, String conversationId, String runId, String message) {
		Instant now = Instant.now();
		updateRun(userId, conversationId, runId, run -> new AgentRun(run.id(), AgentRunStatus.FAILED.name(), run.tasks(),
				append(run.logs(), new AgentRunLog(now, "ERROR", message, AgentRunStatus.FAILED.name(), "FAILED")), run.finalAnswer(),
				run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), message, run.createdAt(), now));
		statusCache.save(conversationId, runId, AgentRunStatus.FAILED.name(), "FAILED", message);
	}

	public ProviderSession providerSession(String userId, String conversationId, String subagentId, String providerId) {
		return safeSessions(findOwnedConversation(userId, conversationId)).stream()
				.filter(session -> subagentId.equals(session.subagentId()) && providerId.equals(session.providerId()))
				.findFirst().orElse(null);
	}

	private Activation activateContext(MainConversationChatHistory conversation, PlannerDecision decision, Instant now) {
		String action = safeText(decision.workContextAction());
		if ("NONE".equals(action)) {
			return new Activation(conversation, activeContext(conversation));
		}
		if ("KEEP".equals(action)) {
			ConversationWorkContext active = activeContext(conversation);
			if (active == null) {
				throw new IllegalStateException("Planner pediu KEEP sem WorkContext ativo.");
			}
			return new Activation(conversation, active);
		}
		WorkContext definition = workContexts.findById(decision.workContextId())
				.filter(WorkContext::enabled)
				.orElseThrow(() -> new IllegalStateException("WorkContext selecionado nao encontrado."));
		List<ConversationWorkContext> updated = new ArrayList<>();
		ConversationWorkContext selected = null;
		for (ConversationWorkContext context : safeContexts(conversation)) {
			if (definition.id().equals(context.workContextId())) {
				selected = new ConversationWorkContext(context.id(), context.workContextId(), ConversationWorkContextStatus.ACTIVE.name(), context.lastAgentRunId(), context.createdAt(), now);
				updated.add(selected);
			} else {
				updated.add(new ConversationWorkContext(context.id(), context.workContextId(), ConversationWorkContextStatus.PAUSED.name(), context.lastAgentRunId(), context.createdAt(), now));
			}
		}
		if (selected == null) {
			selected = new ConversationWorkContext(UUID.randomUUID().toString(), definition.id(), ConversationWorkContextStatus.ACTIVE.name(), null, now, now);
			updated.add(selected);
		}
		MainConversationChatHistory changed = new MainConversationChatHistory(conversation.id(), conversation.userId(), conversation.chatLastMessage(), conversation.messages(), conversation.agentRuns(), selected.id(), updated,
				conversation.humanInputRequests(), conversation.subagentsExecutions(), conversation.providerSessions(), conversation.createdAt(), now);
		return new Activation(changed, selected);
	}

	private void validateAgents(List<PlannedTask> tasks, ConversationWorkContext context) {
		WorkContext definition = workContexts.findById(context.workContextId()).orElseThrow();
		for (PlannedTask task : tasks) {
			if (definition.agentIds() == null || !definition.agentIds().contains(task.subagentId())) {
				throw new IllegalStateException("Planner selecionou subagente fora do WorkContext: " + task.subagentId());
			}
		}
	}

	private MainConversationChatHistory answerHumanRequest(MainConversationChatHistory conversation, String requestId, String messageId, Instant now) {
		if (!StringUtils.hasText(requestId)) {
			return conversation;
		}
		List<HumanInputRequest> requests = safeRequests(conversation).stream().map(request -> requestId.equals(request.id()) && HumanInputRequestStatus.PENDING.name().equals(request.status())
				? new HumanInputRequest(request.id(), request.agentRunId(), request.taskId(), request.question(), HumanInputRequestStatus.ANSWERED.name(), messageId, request.createdAt(), now)
				: request).toList();
		return new MainConversationChatHistory(conversation.id(), conversation.userId(), conversation.chatLastMessage(), conversation.messages(), conversation.agentRuns(), conversation.activeWorkContextId(),
				conversation.workContexts(), requests, conversation.subagentsExecutions(), conversation.providerSessions(), conversation.createdAt(), now);
	}

	private MainConversationChatHistory withMessage(MainConversationChatHistory conversation, Message message) {
		return new MainConversationChatHistory(conversation.id(), conversation.userId(), message.content(), append(conversation.messages(), message), conversation.agentRuns(), conversation.activeWorkContextId(),
				conversation.workContexts(), conversation.humanInputRequests(), conversation.subagentsExecutions(), conversation.providerSessions(), conversation.createdAt(), message.createdAt());
	}

	private MainConversationChatHistory appendAssistantBlocks(MainConversationChatHistory conversation, List<FinalAnswerBlock> blocks, Instant now) {
		MainConversationChatHistory updated = conversation;
		for (FinalAnswerBlock block : blocks == null ? List.<FinalAnswerBlock>of() : blocks) {
			if (StringUtils.hasText(block.markdown())) {
				updated = withMessage(updated, message("assistant", block.markdown(), now));
			}
		}
		return updated;
	}

	private MainConversationChatHistory withAgentRun(MainConversationChatHistory conversation, AgentRun run, Instant now) {
		List<ConversationWorkContext> contexts = safeContexts(conversation).stream().map(context -> context.id().equals(run.conversationWorkContextId())
				? new ConversationWorkContext(context.id(), context.workContextId(), context.status(), run.id(), context.createdAt(), now)
				: context).toList();
		return new MainConversationChatHistory(conversation.id(), conversation.userId(), conversation.chatLastMessage(), conversation.messages(), append(conversation.agentRuns(), run),
				conversation.activeWorkContextId(), contexts, conversation.humanInputRequests(), conversation.subagentsExecutions(), conversation.providerSessions(), conversation.createdAt(), now);
	}

	private MainConversationChatHistory withExecution(MainConversationChatHistory conversation, SubagentExecution execution, Instant now) {
		return new MainConversationChatHistory(conversation.id(), conversation.userId(), conversation.chatLastMessage(), conversation.messages(), conversation.agentRuns(), conversation.activeWorkContextId(),
				conversation.workContexts(), conversation.humanInputRequests(), append(conversation.subagentsExecutions(), execution), conversation.providerSessions(), conversation.createdAt(), now);
	}

	private MainConversationChatHistory withProviderSessions(MainConversationChatHistory conversation, List<ProviderSession> values, Instant now) {
		List<ProviderSession> merged = new ArrayList<>(safeSessions(conversation));
		for (ProviderSession value : values == null ? List.<ProviderSession>of() : values) {
			merged.removeIf(existing -> existing.subagentId().equals(value.subagentId()) && existing.providerId().equals(value.providerId()));
			merged.add(value);
		}
		return new MainConversationChatHistory(conversation.id(), conversation.userId(), conversation.chatLastMessage(), conversation.messages(), conversation.agentRuns(), conversation.activeWorkContextId(),
				conversation.workContexts(), conversation.humanInputRequests(), conversation.subagentsExecutions(), merged, conversation.createdAt(), now);
	}

	private void updateTaskStatus(String userId, String conversationId, String runId, String taskId, String status) {
		Instant now = Instant.now();
		updateRun(userId, conversationId, runId, run -> new AgentRun(run.id(), run.status(),
				replaceTask(run, taskId, task -> copyTask(task, status, task.input(), task.output(), task.validations(), task.attempts(), now)),
				run.logs(), run.finalAnswer(), run.triggeringMessageId(), run.conversationWorkContextId(), run.continuedHumanInputRequestId(), run.agentRunLastMessage(), run.createdAt(), run.finishedAt()));
	}

	private void updateRun(String userId, String conversationId, String runId, java.util.function.Function<AgentRun, AgentRun> update) {
		MainConversationChatHistory conversation = findOwnedConversation(userId, conversationId);
		conversations.save(replaceRun(conversation, runId, update, Instant.now()));
	}

	private MainConversationChatHistory replaceRun(MainConversationChatHistory conversation, String runId, java.util.function.Function<AgentRun, AgentRun> update, Instant now) {
		List<AgentRun> runs = safeRuns(conversation).stream().map(run -> run.id().equals(runId) ? update.apply(run) : run).toList();
		return new MainConversationChatHistory(conversation.id(), conversation.userId(), conversation.chatLastMessage(), conversation.messages(), runs, conversation.activeWorkContextId(),
				conversation.workContexts(), conversation.humanInputRequests(), conversation.subagentsExecutions(), conversation.providerSessions(), conversation.createdAt(), now);
	}

	private AgentRunStatusResponse statusFromMongo(MainConversationChatHistory conversation, String runId) {
		AgentRun run = findRun(conversation, runId);
		List<AgentRunLogEntry> logs = (run.logs() == null ? List.<AgentRunLog>of() : run.logs()).stream()
				.map(log -> new AgentRunLogEntry(log.at().toString(), log.level(), log.message())).toList();
		return new AgentRunStatusResponse(conversation.id(), run.id(), run.status(), run.status(), run.agentRunLastMessage(), logs,
				run.finalAnswer() == null ? List.of() : run.finalAnswer(), conversation.updatedAt().toString(), "database");
	}

	private String workContextId(MainConversationChatHistory conversation, AgentRun run) {
		return safeContexts(conversation).stream().filter(context -> context.id().equals(run.conversationWorkContextId())).findFirst()
				.map(ConversationWorkContext::workContextId).orElse(null);
	}

	private AgentRun findRun(MainConversationChatHistory conversation, String runId) {
		return safeRuns(conversation).stream().filter(run -> run.id().equals(runId)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AgentRun not found."));
	}

	private Task findTask(MainConversationChatHistory conversation, String runId, String taskId) {
		return safeTasks(findRun(conversation, runId)).stream().filter(task -> task.id().equals(taskId)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
	}

	private Task toTask(PlannedTask task, Instant now) {
		return new Task(UUID.randomUUID().toString(), task.description(), task.subagentId(), TaskStatus.NEW.name(), task.safeDependsOn(),
				Map.of("enriched_content", safeText(task.enrichedContent())), Map.of(), List.of(), 0, taskMaxAttempts, now, null);
	}

	private Task copyTask(Task task, String status, Map<String, Object> input, Map<String, Object> output, List<TaskValidation> validations, int attempts, Instant finishedAt) {
		return new Task(task.id(), task.description(), task.subagentSelected(), status, task.dependsOn(), input, output, validations, attempts, task.maxAttempts(), task.createdAt(), finishedAt);
	}

	private List<Task> replaceTask(AgentRun run, String taskId, java.util.function.Function<Task, Task> update) {
		return safeTasks(run).stream().map(task -> task.id().equals(taskId) ? update.apply(task) : task).toList();
	}

	private ConversationWorkContext activeContext(MainConversationChatHistory conversation) {
		return safeContexts(conversation).stream().filter(context -> context.id().equals(conversation.activeWorkContextId())).findFirst().orElse(null);
	}

	private MainConversationChatHistory findOwnedConversation(String userId, String conversationId) {
		return conversations.findByIdAndUserId(conversationId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
	}

	private Message message(String role, String content, Instant now) { return new Message(UUID.randomUUID().toString(), role, content, now); }
	private <T> List<T> append(List<T> values, T value) { List<T> result = new ArrayList<>(values == null ? List.of() : values); result.add(value); return result; }
	private List<Message> safeMessages(List<Message> values) { return values == null ? List.of() : values; }
	private List<AgentRun> safeRuns(MainConversationChatHistory conversation) { return conversation.agentRuns() == null ? List.of() : conversation.agentRuns(); }
	private List<Task> safeTasks(AgentRun run) { return run.tasks() == null ? List.of() : run.tasks(); }
	private List<ConversationWorkContext> safeContexts(MainConversationChatHistory conversation) { return conversation.workContexts() == null ? List.of() : conversation.workContexts(); }
	private List<HumanInputRequest> safeRequests(MainConversationChatHistory conversation) { return conversation.humanInputRequests() == null ? List.of() : conversation.humanInputRequests(); }
	private List<ProviderSession> safeSessions(MainConversationChatHistory conversation) { return conversation.providerSessions() == null ? List.of() : conversation.providerSessions(); }
	private Map<String, Object> merge(Map<String, Object> first, Map<String, Object> second) { var value = new java.util.LinkedHashMap<String, Object>(first == null ? Map.of() : first); value.putAll(second); return value; }
	private String safeText(String value) { return value == null ? "" : value; }
	private String blankToNull(String value) { return StringUtils.hasText(value) ? value : null; }
	private void requireText(String value, String message) { if (!StringUtils.hasText(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

	private record Activation(MainConversationChatHistory conversation, ConversationWorkContext context) {}
}
