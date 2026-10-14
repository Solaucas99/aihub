package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Instance of a catalog {@link WorkContext} inside one conversation. */
@Document("conversation_work_contexts")
@CompoundIndex(def = "{'conversation_id': 1, 'work_context_id': 1}", unique = true)
@CompoundIndex(def = "{'conversation_id': 1, 'status': 1}")
public record ConversationWorkContext(
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("work_context_id")
		String workContextId,

		ConversationWorkContextStatus status,

		@Field("last_agent_run_id")
		String lastAgentRunId,

		@Field("created_at")
		Instant createdAt,

		@Field("updated_at")
		Instant updatedAt
) {

	public ConversationWorkContext withStatus(ConversationWorkContextStatus newStatus, Instant now) {
		return new ConversationWorkContext(id, conversationId, workContextId, newStatus, lastAgentRunId, createdAt, now);
	}

	public ConversationWorkContext withLastAgentRun(String runId, Instant now) {
		return new ConversationWorkContext(id, conversationId, workContextId, status, runId, createdAt, now);
	}
}
