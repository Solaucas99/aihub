package com.lamorim.hubdeia.domain.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("work_contexts")
public record WorkContext(
		@Id
		String id,

		@Indexed(unique = true)
		String key,

		String name,
		String description,
		String instructions,

		@Field("agent_ids")
		List<String> agentIds,

		boolean enabled
) {

	public WorkContext {
		agentIds = agentIds == null ? List.of() : agentIds;
	}
}
