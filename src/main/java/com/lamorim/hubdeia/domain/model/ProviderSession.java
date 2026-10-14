package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** External provider conversation reused 1:1 per conversation + subagent + provider. */
@Document("provider_sessions")
@CompoundIndex(def = "{'conversation_id': 1, 'subagent_id': 1, 'provider_id': 1}", unique = true)
public record ProviderSession(
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("subagent_id")
		String subagentId,

		@Field("provider_id")
		String providerId,

		@Field("external_session_id")
		String externalSessionId,

		String status,
		Map<String, Object> metadata,

		@Field("created_at")
		Instant createdAt,

		@Field("updated_at")
		Instant updatedAt
) {
}
