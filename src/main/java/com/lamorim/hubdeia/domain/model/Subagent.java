package com.lamorim.hubdeia.domain.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("subagents")
public record Subagent(
		@Id
		String id,

		@Indexed
		String name,

		String description,

		@Field("system_prompt")
		String systemPrompt,

	@Field("skill_ids")
	List<String> skillIds,

		List<SubagentProvider> providers,

		@Indexed
		boolean enabled
) {
}
