package com.lamorim.hubdeia.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

class ArtifactServiceTest extends MongoIntegrationTest {

	private static final String CONVERSATION = "conversation-1";

	@Autowired
	ArtifactService artifacts;

	@Autowired
	SubagentRepository subagents;

	@BeforeEach
	void catalog() {
		subagents.save(new Subagent("agent-mensuracao", "Mensuracao", "", "", List.of(), List.of(), List.of("INSIGHT"), List.of(), true));
		subagents.save(new Subagent("agent-campanhas", "Campanhas", "", "", List.of(), List.of(), List.of("BRIEFING"), List.of("INSIGHT"), true));
		subagents.save(new Subagent("agent-sem-artefato", "Outro", "", "", List.of(), List.of(), List.of(), List.of(), true));
	}

	@Test
	void newArtifactSupersedesThePreviousActiveOneOfTheSameType() {
		Artifact first = publish("agent-mensuracao", "Insight setembro", "texto 1", List.of());
		Artifact second = publish("agent-mensuracao", "Insight jovens", "texto 2", List.of());

		assertThat(first.version()).isEqualTo(1);
		assertThat(second.version()).isEqualTo(2);
		assertThat(statusOf(first)).isEqualTo(ArtifactStatus.SUPERSEDED);
		assertThat(statusOf(second)).isEqualTo(ArtifactStatus.ACTIVE);
	}

	@Test
	void subagentsWithoutAProducedTypeOrWithBlankOutputPublishNothing() {
		assertThat(artifacts.publish(run(), task("agent-sem-artefato"), result("texto", List.of()))).isEmpty();
		assertThat(artifacts.publish(run(), task("agent-mensuracao"), result("  ", List.of()))).isEmpty();
		assertThat(artifacts.list(CONVERSATION)).isEmpty();
	}

	@Test
	void derivedArtifactBecomesStaleWhenItsParentIsSuperseded() {
		Artifact insight = publish("agent-mensuracao", "Insight setembro", "texto", List.of());
		Artifact briefing = publish("agent-campanhas", "Briefing", "briefing", List.of(insight.id()));
		assertThat(view(briefing).stale()).isFalse();

		publish("agent-mensuracao", "Insight jovens", "texto novo", List.of());

		assertThat(view(briefing).stale()).isTrue();
		assertThat(view(briefing).status()).isEqualTo(ArtifactStatus.ACTIVE);
	}

	@Test
	void reactivatingAnOlderVersionSwapsWhichOneIsActive() {
		Artifact first = publish("agent-mensuracao", "v1", "texto 1", List.of());
		Artifact second = publish("agent-mensuracao", "v2", "texto 2", List.of());

		Artifact reactivated = artifacts.reactivate(CONVERSATION, first.id()).orElseThrow();

		assertThat(reactivated.status()).isEqualTo(ArtifactStatus.ACTIVE);
		assertThat(statusOf(second)).isEqualTo(ArtifactStatus.SUPERSEDED);
		assertThat(artifacts.consumedBy(CONVERSATION, subagents.findById("agent-campanhas").orElseThrow())).extracting(Artifact::id).containsExactly(first.id());
	}

	@Test
	void reactivationIsScopedToTheConversation() {
		Artifact artifact = publish("agent-mensuracao", "v1", "texto", List.of());

		assertThat(artifacts.reactivate("other-conversation", artifact.id())).isEmpty();
		assertThat(artifacts.reactivate(CONVERSATION, "unknown-id")).isEmpty();
	}

	@Test
	void parallelPublishesGetDistinctVersionsAndExactlyOneStaysActive() {
		int publishers = 8;
		CompletableFuture.allOf(IntStream.range(0, publishers).mapToObj(index -> CompletableFuture.runAsync(
				() -> publish("agent-mensuracao", "Insight " + index, "texto " + index, List.of()))).toArray(CompletableFuture[]::new)).join();

		List<ArtifactView> all = artifacts.list(CONVERSATION);
		assertThat(all).hasSize(publishers);
		assertThat(all).extracting(ArtifactView::version).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, publishers).boxed().toList());
		assertThat(all).filteredOn(view -> view.status() == ArtifactStatus.ACTIVE).singleElement()
				.satisfies(view -> assertThat(view.version()).isEqualTo(publishers));
	}

	private Artifact publish(String subagentId, String title, String content, List<String> consumed) {
		return artifacts.publish(run(), task(subagentId, title), result(content, consumed)).orElseThrow();
	}

	private ArtifactStatus statusOf(Artifact artifact) {
		return mongo.findById(artifact.id(), Artifact.class).status();
	}

	private ArtifactView view(Artifact artifact) {
		return artifacts.list(CONVERSATION).stream().filter(value -> value.id().equals(artifact.id())).findFirst().orElseThrow();
	}

	private AgentRun run() {
		return new AgentRun("run-1", CONVERSATION, AgentRunStatus.RUNNING, List.of(), List.of(), List.of(), "message-1", "context-1", null, "", Instant.now(), null);
	}

	private Task task(String subagentId) {
		return task(subagentId, "Tarefa");
	}

	private Task task(String subagentId, String description) {
		return Task.create("task-" + System.nanoTime(), description, subagentId, "conteudo", 3, Instant.now());
	}

	private TaskExecutionResult result(String content, List<String> consumed) {
		return new TaskExecutionResult("task", "agent", Map.of("content", content), List.of(), List.of(), List.of(), consumed);
	}
}
