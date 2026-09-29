package com.lamorim.hubdeia.domain.model;

import org.springframework.data.mongodb.core.mapping.Field;

public record FinalAnswerBlock(
		String type,
		String title,
		String markdown,

		@Field("task_id")
		String taskId,

		@Field("subagent_id")
		String subagentId
) {
}
