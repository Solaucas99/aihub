package com.lamorim.hubdeia.domain.model;

import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("tools")
public record Tool(
		@Id
		String id,

		@Field("provider_id")
		String providerId,

		String name,
		String type,

		@Field("function_name")
		String functionName,

		String description,

		@Field("input_schema")
		Map<String, Object> inputSchema,

		@Field("output_schema")
		Map<String, Object> outputSchema,

		Map<String, Object> config,
		boolean enabled
) {
}
