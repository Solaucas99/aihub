package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("main_conversation_chat_history")
public record MainConversationChatHistory(
		@Id
		String id,

		@Field("user_id")
		String userId,

		@Field("chat_last_message")
		String chatLastMessage,

		List<Message> messages,

	@Field("agent_runs")
	List<AgentRun> agentRuns,

	@Field("active_work_context_id")
	String activeWorkContextId,

	@Field("work_contexts")
	List<ConversationWorkContext> workContexts,

	@Field("human_input_requests")
	List<HumanInputRequest> humanInputRequests,

		@Field("subagents_executions")
		List<SubagentExecution> subagentsExecutions,

	@Field("provider_sessions")
	List<ProviderSession> providerSessions,

		@Field("created_at")
		Instant createdAt,

		@Field("updated_at")
		Instant updatedAt
) {
}
