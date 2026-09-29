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
import com.lamorim.hubdeia.api.dto.ConversationSummaryResponse;
import com.lamorim.hubdeia.api.dto.CreateConversationRequest;
import com.lamorim.hubdeia.api.dto.MessageAcceptedResponse;
import com.lamorim.hubdeia.api.dto.SendConversationMessageRequest;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.ConversationWorker;
import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;
import com.lamorim.hubdeia.domain.model.Message;

@RestController
public class ConversationController {

	private final AuthHeaders authHeaders;
	private final ConversationService conversations;
	private final ConversationWorker worker;

	public ConversationController(AuthHeaders authHeaders, ConversationService conversations, ConversationWorker worker) {
		this.authHeaders = authHeaders;
		this.conversations = conversations;
		this.worker = worker;
	}

	@PostMapping("/conversation")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageAcceptedResponse create(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody CreateConversationRequest request) {
		String userId = authHeaders.requireUserId(authorization);
		MessageAcceptedResponse response = conversations.create(userId, request.message());
		worker.dispatch(userId, response.conversationId(), response.messageId());
		return response;
	}

	@PostMapping({"/conversations/{conversationId}", "/conversation/{conversationId}"})
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageAcceptedResponse sendMessage(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable String conversationId,
			@RequestBody SendConversationMessageRequest request
	) {
		String userId = authHeaders.requireUserId(authorization);
		MessageAcceptedResponse response = conversations.appendUserMessage(userId, conversationId, request.message());
		worker.dispatch(userId, response.conversationId(), response.messageId());
		return response;
	}

	@GetMapping("/conversations/{conversationId}/agent-runs")
	public List<AgentRunSummaryResponse> agentRuns(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable String conversationId,
			@RequestParam(value = "triggering_message_id", required = false) String messageId
	) {
		return conversations.findAgentRuns(authHeaders.requireUserId(authorization), conversationId, messageId);
	}

	@GetMapping("/conversations/{conversationId}/agent-runs/{agentRunId}")
	public AgentRunStatusResponse agentRunStatus(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable String conversationId,
			@PathVariable String agentRunId
	) {
		return conversations.findAgentRunStatus(authHeaders.requireUserId(authorization), conversationId, agentRunId);
	}

	@GetMapping("/conversations/{conversationId}")
	public MainConversationChatHistory conversation(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String conversationId) {
		return conversations.findConversation(authHeaders.requireUserId(authorization), conversationId);
	}

	@GetMapping("/conversation/{conversationId}/messages")
	public List<Message> messages(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String conversationId) {
		return conversations.findMessages(authHeaders.requireUserId(authorization), conversationId);
	}

	@GetMapping("/conversations")
	public List<ConversationSummaryResponse> conversations(@RequestHeader(value = "Authorization", required = false) String authorization) {
		return conversations.findSummaries(authHeaders.requireUserId(authorization));
	}
}
