package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

public record ProviderSession(
		String id,

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
