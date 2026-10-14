package com.lamorim.hubdeia.application;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.repository.ProviderSessionRepository;
import com.lamorim.hubdeia.domain.repository.SubagentExecutionRepository;

/** Raw subagent attempts (messages, tool calls) and the provider sessions they reuse. */
@Service
public class TaskExecutionService {

	private static final int MEMORY_TASKS = 5;
	/** Several attempts of one task count once, so look far enough back to still find MEMORY_TASKS distinct tasks. */
	private static final int MEMORY_SCAN = 40;

	private final SubagentExecutionRepository executions;
	private final ProviderSessionRepository sessions;
	private final MongoTemplate mongo;

	public TaskExecutionService(SubagentExecutionRepository executions, ProviderSessionRepository sessions, MongoTemplate mongo) {
		this.executions = executions;
		this.sessions = sessions;
		this.mongo = mongo;
	}

	public void record(String conversationId, String runId, Task task, TaskExecutionResult result) {
		Instant now = MonotonicTime.next();
		executions.insert(new SubagentExecution(UUID.randomUUID().toString(), conversationId, runId, task.id(), result.subagentId(),
				result.messages(), result.toolCalls(), task.input(), result.output(), "OUTPUT_READY", task.attempts() + 1, now, now));
		result.providerSessions().forEach(this::upsertSession);
	}

	public Optional<SubagentExecution> latestForTask(String taskId) {
		return executions.findFirstByTaskIdOrderByStartedAtDesc(taskId);
	}

	/** Latest execution of each of the subagent's most recent tasks, oldest first. */
	public List<SubagentExecution> recentMemory(String conversationId, String subagentId) {
		Map<String, SubagentExecution> latestByTask = new LinkedHashMap<>();
		executions.findByConversationIdAndSubagentIdOrderByStartedAtDesc(conversationId, subagentId, PageRequest.of(0, MEMORY_SCAN))
				.forEach(execution -> latestByTask.putIfAbsent(execution.taskId(), execution));
		return latestByTask.values().stream().limit(MEMORY_TASKS).toList().reversed();
	}

	public Optional<ProviderSession> session(String conversationId, String subagentId, String providerId) {
		return sessions.findByConversationIdAndSubagentIdAndProviderId(conversationId, subagentId, providerId);
	}

	/** Atomic per (conversation, subagent, provider), so parallel tasks of one subagent cannot collide. */
	private void upsertSession(ProviderSession session) {
		Query query = Query.query(where("conversationId").is(session.conversationId())
				.and("subagentId").is(session.subagentId()).and("providerId").is(session.providerId()));
		Update update = new Update()
				.set("externalSessionId", session.externalSessionId())
				.set("status", session.status())
				.set("metadata", session.metadata())
				.set("updatedAt", session.updatedAt())
				.setOnInsert("_id", session.id())
				.setOnInsert("createdAt", session.createdAt());
		mongo.upsert(query, update, ProviderSession.class);
	}
}
