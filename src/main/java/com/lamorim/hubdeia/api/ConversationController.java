package com.lamorim.hubdeia.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lamorim.hubdeia.api.dto.AgentRunStatusResponse;
import com.lamorim.hubdeia.api.dto.AgentRunSummaryResponse;
import com.lamorim.hubdeia.api.dto.ConversationDetailResponse;
import com.lamorim.hubdeia.api.dto.ConversationSummaryResponse;
import com.lamorim.hubdeia.api.dto.CreateConversationRequest;
import com.lamorim.hubdeia.api.dto.MessageAcceptedResponse;
import com.lamorim.hubdeia.api.dto.SendConversationMessageRequest;
import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.ArtifactView;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.ConversationWorker;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.domain.model.Message;

@RestController
public class ConversationController {

	private static final String AUTHORIZATION = "Authorization";

	private final AuthHeaders authHeaders;
	private final ConversationService conversations;
	private final AgentRunService runs;
	private final ArtifactService artifacts;
	private final ConversationWorker worker;

	public ConversationController(AuthHeaders authHeaders, ConversationService conversations, AgentRunService runs,
			ArtifactService artifacts, ConversationWorker worker) {
		this.authHeaders = authHeaders;
		this.conversations = conversations;
		this.runs = runs;
		this.artifacts = artifacts;
		this.worker = worker;
	}

	@PostMapping("/conversation")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageAcceptedResponse create(@RequestHeader(value = AUTHORIZATION, required = false) String authorization, @RequestBody CreateConversationRequest request) {
		MessageAcceptedResponse response = conversations.create(authHeaders.requireUserId(authorization), request.message());
		worker.dispatch(response.conversationId(), response.messageId());
		return response;
	}

	@PostMapping({"/conversations/{conversationId}", "/conversation/{conversationId}"})
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageAcceptedResponse sendMessage(
			@RequestHeader(value = AUTHORIZATION, required = false) String authorization,
			@PathVariable String conversationId,
			@RequestBody SendConversationMessageRequest request
	) {
		MessageAcceptedResponse response = conversations.appendUserMessage(authHeaders.requireUserId(authorization), conversationId, request.message());
		worker.dispatch(response.conversationId(), response.messageId());
		return response;
	}

	@GetMapping("/conversations")
	public List<ConversationSummaryResponse> conversations(@RequestHeader(value = AUTHORIZATION, required = false) String authorization) {
		return conversations.summaries(authHeaders.requireUserId(authorization));
	}

	@GetMapping("/conversations/{conversationId}")
	public ConversationDetailResponse conversation(@RequestHeader(value = AUTHORIZATION, required = false) String authorization, @PathVariable String conversationId) {
		return conversations.detail(authHeaders.requireUserId(authorization), conversationId);
	}

	@GetMapping("/conversation/{conversationId}/messages")
	public List<Message> messages(@RequestHeader(value = AUTHORIZATION, required = false) String authorization, @PathVariable String conversationId) {
		return conversations.messages(authHeaders.requireUserId(authorization), conversationId);
	}

	@GetMapping("/conversations/{conversationId}/agent-runs")
	public List<AgentRunSummaryResponse> agentRuns(
			@RequestHeader(value = AUTHORIZATION, required = false) String authorization,
			@PathVariable String conversationId,
			@RequestParam(value = "triggering_message_id", required = false) String messageId
	) {
		conversations.requireOwned(authHeaders.requireUserId(authorization), conversationId);
		return runs.summaries(conversationId, messageId);
	}

	@GetMapping("/conversations/{conversationId}/agent-runs/{agentRunId}")
	public AgentRunStatusResponse agentRunStatus(
			@RequestHeader(value = AUTHORIZATION, required = false) String authorization,
			@PathVariable String conversationId,
			@PathVariable String agentRunId
	) {
		conversations.requireOwned(authHeaders.requireUserId(authorization), conversationId);
		return runs.status(conversationId, agentRunId);
	}

	@GetMapping("/conversations/{conversationId}/artifacts")
	public List<ArtifactView> artifacts(@RequestHeader(value = AUTHORIZATION, required = false) String authorization, @PathVariable String conversationId) {
		conversations.requireOwned(authHeaders.requireUserId(authorization), conversationId);
		return artifacts.list(conversationId);
	}
}
