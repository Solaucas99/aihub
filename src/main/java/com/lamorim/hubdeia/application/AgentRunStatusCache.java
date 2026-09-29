package com.lamorim.hubdeia.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.api.dto.AgentRunStatusResponse;
import com.lamorim.hubdeia.api.dto.AgentRunLogEntry;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;

@Component
public class AgentRunStatusCache {

	private final StringRedisTemplate redis;

	public AgentRunStatusCache(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public void save(String conversationId, String runId, String status, String executionStatus, String message) {
		appendLog(conversationId, runId, status, executionStatus, message);
	}

	public synchronized void appendLog(String conversationId, String runId, String status, String executionStatus, String message) {
		AgentRunStatusResponse current = find(conversationId, runId).orElseGet(() -> empty(conversationId, runId));
		List<AgentRunLogEntry> logs = new ArrayList<>(current.logs());
		Instant now = Instant.now();
		if (message != null && !message.isBlank()) {
			logs.add(new AgentRunLogEntry(now.toString(), "INFO", message));
		}
		saveResponse(new AgentRunStatusResponse(conversationId, runId, status, executionStatus, message, logs, current.finalAnswer(), now.toString(), "cache"));
	}

	public void saveFinal(String conversationId, String runId, String status, String executionStatus, String message, List<FinalAnswerBlock> answer) {
		AgentRunStatusResponse current = find(conversationId, runId).orElseGet(() -> empty(conversationId, runId));
		List<AgentRunLogEntry> logs = new ArrayList<>(current.logs());
		Instant now = Instant.now();
		logs.add(new AgentRunLogEntry(now.toString(), "INFO", message));
		saveResponse(new AgentRunStatusResponse(conversationId, runId, status, executionStatus, message, logs, answer == null ? List.of() : answer, now.toString(), "cache"));
	}

	public Optional<AgentRunStatusResponse> find(String conversationId, String runId) {
		String raw = redis.opsForValue().get(key(conversationId, runId));
		if (raw == null || raw.isBlank()) return Optional.empty();
		Document value = Document.parse(raw);
		return Optional.of(new AgentRunStatusResponse(value.getString("conversation_id"), value.getString("agent_run_id"), value.getString("status"),
				value.getString("execution_status"), value.getString("current_message"), logs(value), answer(value), value.getString("updated_at"), "cache"));
	}

	public void restore(AgentRunStatusResponse response) {
		saveResponse(new AgentRunStatusResponse(response.conversationId(), response.agentRunId(), response.status(), response.executionStatus(),
				response.currentMessage(), response.logs(), response.finalAnswer(), response.updatedAt(), "cache"));
	}

	private void saveResponse(AgentRunStatusResponse response) {
		Document value = new Document("conversation_id", response.conversationId())
				.append("agent_run_id", response.agentRunId()).append("status", response.status()).append("execution_status", response.executionStatus())
				.append("current_message", response.currentMessage()).append("updated_at", response.updatedAt())
				.append("logs", response.logs().stream().map(log -> new Document("at", log.at()).append("level", log.level()).append("message", log.message())).toList())
				.append("final_answer", response.finalAnswer().stream().map(block -> new Document("type", block.type()).append("title", block.title())
						.append("markdown", block.markdown()).append("task_id", block.taskId()).append("subagent_id", block.subagentId())).toList());
		redis.opsForValue().set(key(response.conversationId(), response.agentRunId()), value.toJson());
	}

	@SuppressWarnings("unchecked")
	private List<AgentRunLogEntry> logs(Document value) {
		List<Document> raw = (List<Document>) value.get("logs", List.class);
		return raw == null ? List.of() : raw.stream().map(log -> new AgentRunLogEntry(log.getString("at"), log.getString("level"), log.getString("message"))).toList();
	}

	@SuppressWarnings("unchecked")
	private List<FinalAnswerBlock> answer(Document value) {
		List<Document> raw = (List<Document>) value.get("final_answer", List.class);
		return raw == null ? List.of() : raw.stream().map(block -> new FinalAnswerBlock(block.getString("type"), block.getString("title"), block.getString("markdown"), block.getString("task_id"), block.getString("subagent_id"))).toList();
	}

	private AgentRunStatusResponse empty(String conversationId, String runId) {
		return new AgentRunStatusResponse(conversationId, runId, "NEW", "NEW", "", List.of(), List.of(), Instant.now().toString(), "cache");
	}

	private String key(String conversationId, String runId) {
		return "agent_run_status:%s:%s".formatted(conversationId, runId);
	}
}
