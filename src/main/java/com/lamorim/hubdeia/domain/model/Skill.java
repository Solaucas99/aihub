package com.lamorim.hubdeia.domain.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("skills")
public record Skill(
		@Id
		String id,

		@Indexed(unique = true)
		String key,

		String name,
		String description,
		String instructions,

		@Field("tool_ids")
		List<String> toolIds,

		boolean enabled
) {
}
