package com.lamorim.hubdeia.application;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.repository.ArtifactRepository;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

/**
 * Artifacts are published when a task output is approved. A new artifact supersedes the previous
 * active one of the same type; staleness is derived on read, never stored.
 */
@Service
public class ArtifactService {

	private static final int LISTED = 20;
	/** Every collision has a winner, so N concurrent publishes of one type need at most N attempts. */
	private static final int MAX_VERSION_ATTEMPTS = 16;

	private final ArtifactRepository artifacts;
	private final SubagentRepository subagents;
	private final WorkContextService contexts;
	private final MongoTemplate mongo;

	public ArtifactService(ArtifactRepository artifacts, SubagentRepository subagents, WorkContextService contexts, MongoTemplate mongo) {
		this.artifacts = artifacts;
		this.subagents = subagents;
		this.contexts = contexts;
		this.mongo = mongo;
	}

	/** Publishes the approved task output when its subagent declares an artifact type to produce. */
	public Optional<Artifact> publish(AgentRun run, Task task, TaskExecutionResult result) {
		Optional<Subagent> subagent = subagents.findById(task.subagentSelected()).filter(value -> !value.produces().isEmpty());
		TaskOutput output = TaskOutput.from(result.output());
		if (subagent.isEmpty() || output.content().isBlank()) {
			return Optional.empty();
		}
		String type = subagent.get().produces().getFirst();
		String workContextId = contexts.find(run.conversationWorkContextId()).map(ConversationWorkContext::workContextId).orElse(null);
		Artifact artifact = insertNextVersion(run.conversationId(), type, version -> new Artifact(UUID.randomUUID().toString(), run.conversationId(),
				workContextId, type, version, ArtifactStatus.ACTIVE, result.consumedArtifactIds(), task.description(), output.content(), output.visuals(), subagent.get().id(),
				run.id(), task.id(), Instant.now()));
		supersedeOlderVersions(artifact);
		return Optional.of(artifact);
	}

	/** Makes an existing artifact of this conversation the active one of its type again. */
	public Optional<Artifact> reactivate(String conversationId, String artifactId) {
		Optional<Artifact> target = artifacts.findByIdAndConversationId(artifactId, conversationId);
		target.filter(artifact -> artifact.status() != ArtifactStatus.ACTIVE).ifPresent(artifact -> {
			supersedeOthers(artifact);
			mongo.updateFirst(Query.query(where("_id").is(artifact.id())), new Update().set("status", ArtifactStatus.ACTIVE), Artifact.class);
		});
		return target.flatMap(artifact -> artifacts.findById(artifact.id()));
	}

	/** Most recent artifacts of the conversation (newest first) with their derived staleness. */
	public List<ArtifactView> list(String conversationId) {
		List<Artifact> recent = artifacts.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, LISTED));
		List<String> parentIds = recent.stream().flatMap(artifact -> artifact.derivedFrom().stream()).distinct().toList();
		Map<String, ArtifactStatus> parentStatus = artifacts.findAllById(parentIds).stream().collect(Collectors.toMap(Artifact::id, Artifact::status));
		return recent.stream()
				.map(artifact -> ArtifactView.of(artifact, artifact.derivedFrom().stream().anyMatch(id -> parentStatus.get(id) == ArtifactStatus.SUPERSEDED)))
				.toList();
	}

	/** Latest active artifact of every type the subagent consumes. */
	public List<Artifact> consumedBy(String conversationId, Subagent subagent) {
		return subagent.consumes().stream()
				.map(type -> artifacts.findFirstByConversationIdAndTypeAndStatusOrderByVersionDesc(conversationId, type, ArtifactStatus.ACTIVE))
				.flatMap(Optional::stream)
				.toList();
	}

	/** Versions are unique per conversation + type; a parallel publish that took the same number retries with the next one. */
	private Artifact insertNextVersion(String conversationId, String type, IntFunction<Artifact> factory) {
		for (int attempt = 0; attempt < MAX_VERSION_ATTEMPTS; attempt++) {
			int version = artifacts.findFirstByConversationIdAndTypeOrderByVersionDesc(conversationId, type).map(Artifact::version).orElse(0) + 1;
			try {
				return artifacts.insert(factory.apply(version));
			} catch (DuplicateKeyException concurrentPublish) {
				// another task published this version first
			}
		}
		throw new IllegalStateException("Nao foi possivel publicar o artefato %s da conversa %s.".formatted(type, conversationId));
	}

	/** The highest version always wins, whatever order concurrent publishes finish in. */
	private void supersedeOlderVersions(Artifact keep) {
		Query query = Query.query(where("conversationId").is(keep.conversationId()).and("type").is(keep.type())
				.and("status").is(ArtifactStatus.ACTIVE).and("version").lt(keep.version()));
		mongo.updateMulti(query, new Update().set("status", ArtifactStatus.SUPERSEDED), Artifact.class);
	}

	private void supersedeOthers(Artifact keep) {
		Query query = Query.query(where("conversationId").is(keep.conversationId()).and("type").is(keep.type())
				.and("status").is(ArtifactStatus.ACTIVE).and("_id").ne(keep.id()));
		mongo.updateMulti(query, new Update().set("status", ArtifactStatus.SUPERSEDED), Artifact.class);
	}
}
