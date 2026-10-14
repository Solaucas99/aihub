package com.lamorim.hubdeia.application.planner;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.HumanInputService;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.WorkContext;

/**
 * Turns a {@link PlannerDecision} into side effects: answers, context switches, artifact reuse and
 * agent runs. A decision that needs tasks is validated completely before anything is written, so a
 * rejected plan leaves no trace.
 */
@Component
public class PlannerDecisionHandler {

	private static final Logger logger = LoggerFactory.getLogger(PlannerDecisionHandler.class);
	private static final String DEFAULT_RESPONSE = "Posso ajudar com isso.";
	private static final String ARTIFACT_NOT_FOUND_RESPONSE = "Nao encontrei essa versao para retomar.";

	private final ConversationService conversations;
	private final WorkContextService contexts;
	private final HumanInputService humanInput;
	private final ArtifactService artifacts;
	private final AgentRunService runs;

	public PlannerDecisionHandler(ConversationService conversations, WorkContextService contexts, HumanInputService humanInput,
			ArtifactService artifacts, AgentRunService runs) {
		this.conversations = conversations;
		this.contexts = contexts;
		this.humanInput = humanInput;
		this.artifacts = artifacts;
		this.runs = runs;
	}

	/** Applies the decision and returns the id of the agent run it started, if any. */
	public Optional<String> apply(String conversationId, String messageId, PlannerDecision decision) {
		String contextId = decision.hasTasks() ? validatedContextId(conversationId, decision) : null;

		humanInput.answer(conversationId, decision.relatedHumanInputRequestId(), messageId);
		Optional<Artifact> reactivated = reactivateArtifact(conversationId, decision);

		if (contextId == null) {
			conversations.appendAssistantMessage(conversationId, responseText(decision, reactivated));
			return Optional.empty();
		}
		ConversationWorkContext context = contexts.activate(conversationId, contextId);
		AgentRun run = runs.start(conversationId, messageId, context, decision.relatedHumanInputRequestId(), decision.responseMessage(), decision.tasks());
		return Optional.of(run.id());
	}

	/** Catalog context the tasks run in, after checking that it exists and offers every selected subagent. */
	private String validatedContextId(String conversationId, PlannerDecision decision) {
		String definitionId = switch (decision.workContextAction()) {
			case ACTIVATE -> decision.workContextId();
			case KEEP, NONE -> contexts.active(conversationId).map(ConversationWorkContext::workContextId)
					.orElseThrow(() -> new IllegalStateException("Planner criou tasks sem WorkContext ativo."));
		};
		WorkContext definition = contexts.definition(definitionId).filter(WorkContext::enabled)
				.orElseThrow(() -> new IllegalStateException("WorkContext selecionado nao encontrado: " + definitionId));
		decision.tasks().stream().filter(task -> !definition.agentIds().contains(task.subagentId())).findFirst().ifPresent(task -> {
			throw new IllegalStateException("Planner selecionou subagente fora do WorkContext: " + task.subagentId());
		});
		return definition.id();
	}

	private Optional<Artifact> reactivateArtifact(String conversationId, PlannerDecision decision) {
		if (!StringUtils.hasText(decision.reactivateArtifactId())) {
			return Optional.empty();
		}
		Optional<Artifact> artifact = artifacts.reactivate(conversationId, decision.reactivateArtifactId());
		if (artifact.isEmpty()) {
			logger.warn("Planner asked to reactivate an unknown artifact. conversationId={}, artifactId={}", conversationId, decision.reactivateArtifactId());
		}
		return artifact;
	}

	private String responseText(PlannerDecision decision, Optional<Artifact> reactivated) {
		if (StringUtils.hasText(decision.reactivateArtifactId()) && reactivated.isEmpty()) {
			return ARTIFACT_NOT_FOUND_RESPONSE;
		}
		if (StringUtils.hasText(decision.responseMessage())) {
			return decision.responseMessage();
		}
		return reactivated.map(artifact -> "Retomei \"%s\" (versao %d).".formatted(artifact.title(), artifact.version())).orElse(DEFAULT_RESPONSE);
	}
}
