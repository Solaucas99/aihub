package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Typed result produced by an approved task (for example an INSIGHT). Artifacts belong to the
 * conversation, so they survive a change of work context.
 */
@Document("artifacts")
@CompoundIndex(def = "{'conversation_id': 1, 'type': 1, 'version': 1}", unique = true)
@CompoundIndex(def = "{'conversation_id': 1, 'type': 1, 'status': 1}")
@CompoundIndex(def = "{'conversation_id': 1, 'created_at': -1}")
public record Artifact(
		@Id
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("work_context_id")
		String workContextId,

		String type,
		int version,
		ArtifactStatus status,

		@Field("derived_from")
		List<String> derivedFrom,

		String title,

		/** Text that may reference visuals with {@code [[visualization:id]]} markers. */
		String content,

		List<Visual> visuals,

		@Field("subagent_id")
		String subagentId,

		@Field("created_by_run_id")
		String createdByRunId,

		@Field("created_by_task_id")
		String createdByTaskId,

		@Field("created_at")
		Instant createdAt
) {

	public Artifact {
		derivedFrom = derivedFrom == null ? List.of() : derivedFrom;
		visuals = visuals == null ? List.of() : visuals;
	}
}
