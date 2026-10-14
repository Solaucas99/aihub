package com.lamorim.hubdeia.application.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.lamorim.hubdeia.application.TaskExecutionService;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.llm.OpenAiChatClient;
import com.lamorim.hubdeia.application.orchestration.AgentSkillAccessPolicy.AllowedTool;
import com.lamorim.hubdeia.application.orchestration.SubagentPromptBuilder.SubagentPrompt;
import com.lamorim.hubdeia.application.provider.ProgressReporter;
import com.lamorim.hubdeia.application.provider.ProviderTool;
import com.lamorim.hubdeia.application.provider.ProviderToolContext;
import com.lamorim.hubdeia.application.provider.ProviderToolRegistry;
import com.lamorim.hubdeia.application.provider.ProviderToolResult;
import com.lamorim.hubdeia.domain.model.ExecutionMessage;
import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.ToolCall;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

/** Runs one subagent attempt: the model/tool loop, restricted to the tools its skills grant. */
@Component
public class SubagentTaskExecutor {

	private final OpenAiChatClient chat;
	private final String model;
	private final int maxToolRounds;
	private final SubagentRepository subagents;
	private final AgentSkillAccessPolicy accessPolicy;
	private final ProviderToolRegistry providerTools;
	private final SubagentPromptBuilder prompts;
	private final TaskExecutionService executions;

	public SubagentTaskExecutor(
			OpenAiChatClient chat,
			@Value("${hub.openai.subagent-model:gpt-5}") String model,
			@Value("${hub.openai.max-tool-rounds:8}") int maxToolRounds,
			SubagentRepository subagents,
			AgentSkillAccessPolicy accessPolicy,
			ProviderToolRegistry providerTools,
			SubagentPromptBuilder prompts,
			TaskExecutionService executions
	) {
		this.chat = chat;
		this.model = model;
		this.maxToolRounds = Math.max(1, maxToolRounds);
		this.subagents = subagents;
		this.accessPolicy = accessPolicy;
		this.providerTools = providerTools;
		this.prompts = prompts;
		this.executions = executions;
	}

	public TaskExecutionResult execute(String conversationId, String runId, Task task, ProgressReporter reporter) {
		Subagent subagent = subagents.findById(task.subagentSelected())
				.orElseThrow(() -> new IllegalStateException("Subagente nao encontrado: " + task.subagentSelected()));
		Map<String, AllowedTool> allowedTools = accessPolicy.toolsFor(subagent);
		List<Document> toolSchemas = allowedTools.keySet().stream().map(providerTools::get).map(ProviderTool::schema).toList();
		SubagentPrompt prompt = prompts.build(conversationId, runId, subagent, task);
		Attempt attempt = new Attempt(conversationId, runId, task, subagent, reporter, prompt.messages());

		String answer = "";
		String lastToolText = "";
		for (int round = 0; round < maxToolRounds; round++) {
			Document assistant = chat.complete(request(attempt.messages, toolSchemas));
			attempt.messages.add(assistant);
			String text = OpenAiChatClient.content(assistant);
			attempt.persisted.add(new ExecutionMessage(UUID.randomUUID().toString(), "assistant", text, Instant.now()));
			List<Document> toolCalls = assistant.getList("tool_calls", Document.class);
			if (toolCalls == null || toolCalls.isEmpty()) {
				answer = text;
				break;
			}
			for (Document call : toolCalls) {
				lastToolText = callTool(attempt, allowedTools, call);
			}
		}
		reporter.report("AWAITING_VALIDATION", "Output recebido, aguardando validacao");
		TaskOutput output = new TaskOutput(StringUtils.hasText(answer) ? answer : lastToolText, List.copyOf(attempt.visuals.values()));
		return new TaskExecutionResult(task.id(), subagent.id(), output.toMap(), attempt.persisted, List.copyOf(attempt.sessions.values()),
				attempt.calls, prompt.consumedArtifactIds());
	}

	/** Runs one tool call and returns the text the tool answered with. */
	private String callTool(Attempt attempt, Map<String, AllowedTool> allowedTools, Document call) {
		Document function = call.get("function", Document.class);
		String functionName = function.getString("name");
		AllowedTool allowed = allowedTools.get(functionName);
		if (allowed == null) {
			throw new IllegalStateException("Tool nao autorizada para o subagente: " + functionName);
		}
		String providerId = allowed.provider().providerId();
		Document arguments = Document.parse(function.getString("arguments"));
		Instant startedAt = Instant.now();
		attempt.reporter.report("TOOL_CALLING", "Consultando " + allowed.tool().name());
		ProviderToolResult result = providerTools.get(functionName).execute(new ProviderToolContext(attempt.conversationId, attempt.runId,
				attempt.task.id(), attempt.subagent.id(), allowed.provider(), sessionFor(attempt, providerId), attempt.reporter), arguments);
		if (result.providerSession() != null) {
			attempt.sessions.put(providerId, result.providerSession());
		}
		attempt.messages.add(new Document("role", "tool").append("tool_call_id", call.getString("id")).append("content", result.content()));
		attempt.persisted.add(new ExecutionMessage(UUID.randomUUID().toString(), "tool", result.content(), Instant.now()));
		result.visuals().forEach(visual -> attempt.visuals.put(visual.id(), visual));
		attempt.calls.add(new ToolCall(UUID.randomUUID().toString(), allowed.tool().id(), providerId, arguments, result.structuredOutput(),
				result.content(), "FINISHED", startedAt, Instant.now()));
		return result.content() == null ? "" : result.content();
	}

	/** Reuses the session created earlier in this attempt, otherwise the one stored for the conversation. */
	private ProviderSession sessionFor(Attempt attempt, String providerId) {
		ProviderSession current = attempt.sessions.get(providerId);
		return current != null ? current : executions.session(attempt.conversationId, attempt.subagent.id(), providerId).orElse(null);
	}

	private Document request(List<Document> messages, List<Document> toolSchemas) {
		Document request = new Document("model", model).append("messages", messages);
		if (!toolSchemas.isEmpty()) {
			request.append("tools", toolSchemas).append("parallel_tool_calls", false);
		}
		return request;
	}

	/** Mutable state of a single attempt. */
	private static final class Attempt {
		final String conversationId;
		final String runId;
		final Task task;
		final Subagent subagent;
		final ProgressReporter reporter;
		final List<Document> messages;
		final List<ExecutionMessage> persisted = new ArrayList<>();
		final List<ToolCall> calls = new ArrayList<>();
		final Map<String, ProviderSession> sessions = new LinkedHashMap<>();
		final Map<String, Visual> visuals = new LinkedHashMap<>();

		Attempt(String conversationId, String runId, Task task, Subagent subagent, ProgressReporter reporter, List<Document> prompt) {
			this.conversationId = conversationId;
			this.runId = runId;
			this.task = task;
			this.subagent = subagent;
			this.reporter = reporter;
			this.messages = new ArrayList<>(prompt);
		}
	}
}
