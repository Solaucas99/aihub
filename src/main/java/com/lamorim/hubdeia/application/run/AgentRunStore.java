package com.lamorim.hubdeia.application.run;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunLog;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.TaskValidation;

/**
 * Persistence of {@link AgentRun} state. Every write is a single atomic update on one run document,
 * so parallel tasks never overwrite each other. Terminal states are final: a run only leaves
 * RUNNING once.
 */
@Component
public class AgentRunStore {

	private final MongoTemplate mongo;

	public AgentRunStore(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	public AgentRun insert(AgentRun run) {
		return mongo.insert(run);
	}

	public Optional<AgentRun> find(String runId) {
		return Optional.ofNullable(mongo.findById(runId, AgentRun.class));
	}

	public AgentRun require(String runId) {
		return find(runId).orElseThrow(() -> new IllegalStateException("AgentRun nao encontrado: " + runId));
	}

	public Task requireTask(String runId, String taskId) {
		return require(runId).tasks().stream().filter(task -> task.id().equals(taskId)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Task nao encontrada: " + taskId));
	}

	public boolean isRunning(String runId) {
		return mongo.exists(running(runId), AgentRun.class);
	}

	/** Appends a log entry while the run is still RUNNING. Returns false once the run has ended. */
	public boolean appendLog(String runId, AgentRunLog log) {
		Update update = new Update().push("logs", log).set("agentRunLastMessage", log.message());
		return mongo.updateFirst(running(runId), update, AgentRun.class).getModifiedCount() > 0;
	}

	/** Moves the run from RUNNING to a terminal status. Returns false if it had already ended. */
	public boolean complete(String runId, AgentRunStatus terminal, List<FinalAnswerBlock> blocks, AgentRunLog log) {
		Update update = new Update()
				.set("status", terminal)
				.set("finalAnswer", blocks)
				.set("finishedAt", Instant.now())
				.set("agentRunLastMessage", log.message())
				.push("logs", log);
		return mongo.updateFirst(running(runId), update, AgentRun.class).getModifiedCount() > 0;
	}

	public void markTaskRunning(String runId, String taskId) {
		patchTask(runId, taskId, new Update().set("tasks.$.status", TaskStatus.RUNNING).set("tasks.$.finishedAt", null));
	}

	public void recordTaskOutput(String runId, String taskId, Map<String, Object> output, int attempts) {
		patchTask(runId, taskId, new Update().set("tasks.$.output", output).set("tasks.$.attempts", attempts));
	}

	public void appendTaskValidation(String runId, String taskId, TaskValidation validation) {
		patchTask(runId, taskId, new Update().push("tasks.$.validations", validation));
	}

	public void finishTask(String runId, String taskId, TaskStatus status) {
		patchTask(runId, taskId, new Update().set("tasks.$.status", status).set("tasks.$.finishedAt", Instant.now()));
	}

	public void resetTaskForRetry(String runId, String taskId, String validatorFeedback) {
		patchTask(runId, taskId, new Update()
				.set("tasks.$.status", TaskStatus.NEW)
				.set("tasks.$.output", Map.of())
				.set("tasks.$.finishedAt", null)
				.set("tasks.$.input.validator_feedback", validatorFeedback == null ? "" : validatorFeedback));
	}

	private void patchTask(String runId, String taskId, Update update) {
		mongo.updateFirst(Query.query(where("_id").is(runId).and("tasks.id").is(taskId)), update, AgentRun.class);
	}

	private static Query running(String runId) {
		return Query.query(where("_id").is(runId).and("status").is(AgentRunStatus.RUNNING));
	}
}
