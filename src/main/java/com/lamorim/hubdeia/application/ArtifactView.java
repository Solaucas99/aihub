package com.lamorim.hubdeia.application;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;

/**
 * Artifact as served to clients, assembled when asked for. {@code stale} is true when a parent it was
 * built from is no longer active. {@code content} is plain text (visuals shown as placeholders);
 * {@code renderedContent} is the markdown with the visuals put back, for display.
 */
public record ArtifactView(
		String id,
		String type,
		int version,
		ArtifactStatus status,
		boolean stale,
		String title,
		String content,

		@JsonProperty("rendered_content")
		String renderedContent,

		@JsonProperty("derived_from")
		List<String> derivedFrom,

		@JsonProperty("work_context_id")
		String workContextId,

		@JsonProperty("subagent_id")
		String subagentId,

		@JsonProperty("created_by_run_id")
		String createdByRunId,

		@JsonProperty("created_at")
		Instant createdAt
) {

	public static ArtifactView of(Artifact artifact, boolean stale) {
		TaskOutput output = new TaskOutput(artifact.content(), artifact.visuals());
		return new ArtifactView(artifact.id(), artifact.type(), artifact.version(), artifact.status(), stale, artifact.title(),
				output.plainText(), output.markdown(), artifact.derivedFrom(), artifact.workContextId(), artifact.subagentId(),
				artifact.createdByRunId(), artifact.createdAt());
	}
}
