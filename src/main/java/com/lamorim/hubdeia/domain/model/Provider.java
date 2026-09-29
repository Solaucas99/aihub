package com.lamorim.hubdeia.domain.model;

import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("providers")
public record Provider(
		@Id
		String id,
		String name,
		String category,

		@Field("external_agent_id")
		String externalAgentId,

		List<Tool> tools,
		Map<String, Object> config,
		boolean enabled
) {
}
