package com.lamorim.hubdeia.domain.model;

import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

public record SubagentProvider(
		String id,

		@Field("provider_id")
		String providerId,

		Map<String, Object> config,

		boolean enabled
) {
}
