package com.lamorim.hubdeia.application.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.lamorim.hubdeia.application.provider.ProgressReporter;
import com.lamorim.hubdeia.application.provider.ProviderTool;
import com.lamorim.hubdeia.application.provider.ProviderToolContext;
import com.lamorim.hubdeia.application.provider.ProviderToolRegistry;
import com.lamorim.hubdeia.application.provider.ProviderToolResult;
import com.lamorim.hubdeia.config.HubRestClientFactory;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.ToolCall;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

@Component
public class SubagentTaskExecutor {

	private static final Pattern VISUALIZATION_MARKER = Pattern.compile("\\[\\[visualization:([^]]+)]]");

	private final RestClient restClient;
	private final String apiKey;
	private final String model;
	private final int maxToolRounds;
	private final SubagentRepository subagents;
	private final WorkContextRepository workContexts;
	private final AgentSkillAccessPolicy accessPolicy;
	private final ProviderToolRegistry providerTools;

	public SubagentTaskExecutor(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			@Value("${hub.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${hub.openai.api-key:}") String apiKey,
			@Value("${hub.openai.subagent-model:gpt-5}") String model,
			@Value("${hub.openai.timeout-ms:60000}") long timeoutMs,
			@Value("${hub.openai.max-tool-rounds:8}") int maxToolRounds,
			SubagentRepository subagents,
			WorkContextRepository workContexts,
			AgentSkillAccessPolicy accessPolicy,
			ProviderToolRegistry providerTools
	) {
		this.restClient = restClients.create(restClientBuilder, baseUrl, timeoutMs);
		this.apiKey = apiKey;
		this.model = model;
		this.maxToolRounds = Math.max(1, maxToolRounds);
		this.subagents = subagents;
		this.workContexts = workContexts;
		this.accessPolicy = accessPolicy;
		this.providerTools = providerTools;
	}

	public TaskExecutionResult execute(MainConversationChatHistory conversation, String runId, Task task, ProgressReporter reporter) {
		if (!StringUtils.hasText(apiKey)) throw new IllegalStateException("OPENAI_API_KEY nao configurada para executar subagente.");
		Subagent subagent = subagents.findById(task.subagentSelected()).orElseThrow(() -> new IllegalStateException("Subagente nao encontrado: " + task.subagentSelected()));
		Map<String, AgentSkillAccessPolicy.AllowedTool> allowedTools = accessPolicy.toolsFor(subagent);
		reporter.report("TASK_RUNNING", "Executando task: " + task.description());
		List<Document> messages = initialMessages(conversation, subagent, task);
		List<Message> persistedMessages = new ArrayList<>();
		List<ToolCall> calls = new ArrayList<>();
		List<ProviderSession> sessions = new ArrayList<>();
		Map<String, Object> output = Map.of("content", "");
		Map<String, Object> lastToolOutput = null;

		for (int round = 0; round < maxToolRounds; round++) {
			Document response = chat(messages, schemas(allowedTools));
			Document assistant = response.getList("choices", Document.class).getFirst().get("message", Document.class);
			messages.add(assistant);
			persistedMessages.add(new Message(UUID.randomUUID().toString(), "assistant", content(assistant), Instant.now()));
			List<Document> toolCalls = assistant.getList("tool_calls", Document.class);
			if (toolCalls == null || toolCalls.isEmpty()) {
				output = output(lastToolOutput, content(assistant));
				break;
			}
			for (Document call : toolCalls) {
				Document function = call.get("function", Document.class);
				String functionName = function.getString("name");
				AgentSkillAccessPolicy.AllowedTool allowed = allowedTools.get(functionName);
				if (allowed == null) throw new IllegalStateException("Tool nao autorizada para o subagente: " + functionName);
				ProviderTool tool = providerTools.get(functionName);
				Document arguments = Document.parse(function.getString("arguments"));
				Instant startedAt = Instant.now();
				reporter.report("TOOL_CALLING", "Consultando " + allowed.tool().name());
				ProviderToolResult result = tool.execute(new ProviderToolContext(conversation.id(), runId, task.id(), subagent.id(), allowed.provider(),
						existingSession(conversation, subagent.id(), allowed.provider().providerId()), reporter), arguments);
				Instant finishedAt = Instant.now();
				sessions.add(result.providerSession());
				calls.add(new ToolCall(UUID.randomUUID().toString(), allowed.tool().id(), allowed.provider().providerId(), arguments, result.structuredOutput(), "FINISHED", startedAt, finishedAt));
				messages.add(new Document("role", "tool").append("tool_call_id", call.getString("id")).append("content", result.content()));
				persistedMessages.add(new Message(UUID.randomUUID().toString(), "tool", result.content(), Instant.now()));
				lastToolOutput = result.structuredOutput();
				output = lastToolOutput;
			}
		}
		reporter.report("AWAITING_VALIDATION", "Output recebido, aguardando validacao");
		return new TaskExecutionResult(task.id(), subagent.id(), output, persistedMessages, sessions, calls);
	}

	private List<Document> initialMessages(MainConversationChatHistory conversation, Subagent subagent, Task task) {
		List<Document> messages = new ArrayList<>();
		messages.add(new Document("role", "system").append("content", instructions(conversation, subagent)));
		String memory = shortTermMemory(conversation, subagent.id());
		if (StringUtils.hasText(memory)) messages.add(new Document("role", "system").append("content", "Memoria individual do subagente:\n" + memory));
		messages.add(new Document("role", "user").append("content", "Objetivo da tarefa: %s\nInput: %s".formatted(task.description(), task.input())));
		return messages;
	}

	private String instructions(MainConversationChatHistory conversation, Subagent subagent) {
		List<String> sections = new ArrayList<>();
		sections.add(subagent.systemPrompt());
		activeContextInstructions(conversation).ifPresent(sections::add);
		List<Skill> skills = accessPolicy.skillsFor(subagent);
		if (!skills.isEmpty()) sections.add("Skills permitidas:\n" + skills.stream().map(skill -> "- " + skill.name() + ": " + skill.instructions()).reduce((a, b) -> a + "\n" + b).orElse(""));
		sections.add("Use somente as tools disponibilizadas para voce. Nao alegue consultas que nao realizou.");
		sections.add("Quando uma tool retornar marcadores no formato [[visualization:id]], preserve todos exatamente uma vez na resposta final. Posicione cada marcador logo apos o paragrafo que explica seus dados ou insight. Nao altere o texto do marcador, nao o exponha ao usuario e nao escreva JSON ou HTML para representa-lo.");
		return String.join("\n\n", sections);
	}

	private java.util.Optional<String> activeContextInstructions(MainConversationChatHistory conversation) {
		return safeContexts(conversation).stream().filter(context -> context.id().equals(conversation.activeWorkContextId())).findFirst()
				.flatMap(context -> workContexts.findById(context.workContextId())).map(WorkContext::instructions).filter(StringUtils::hasText);
	}

	private String shortTermMemory(MainConversationChatHistory conversation, String subagentId) {
		Map<String, SubagentExecution> latestByTask = new LinkedHashMap<>();
		for (SubagentExecution execution : safeExecutions(conversation)) if (subagentId.equals(execution.subagentId())) latestByTask.put(execution.taskId(), execution);
		List<SubagentExecution> values = new ArrayList<>(latestByTask.values());
		return values.stream().skip(Math.max(0, values.size() - 5)).map(this::memoryLine).reduce((a, b) -> a + "\n" + b).orElse("");
	}

	private String memoryLine(SubagentExecution execution) {
		String input = execution.input() == null ? "{}" : new Document(execution.input()).toJson();
		String output = execution.output() == null ? "{}" : new Document(execution.output()).toJson();
		return "- task_id=%s input=%s output=%s".formatted(execution.taskId(), truncate(input), truncate(output));
	}

	private List<Document> schemas(Map<String, AgentSkillAccessPolicy.AllowedTool> tools) {
		return tools.keySet().stream().map(providerTools::get).map(ProviderTool::schema).toList();
	}

	private Document chat(List<Document> messages, List<Document> tools) {
		Document request = new Document("model", model).append("messages", messages);
		if (!tools.isEmpty()) request.append("tools", tools).append("parallel_tool_calls", false);
		String response = restClient.post().uri("/chat/completions").header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON).body(request.toJson()).retrieve().body(String.class);
		return Document.parse(response);
	}

	private ProviderSession existingSession(MainConversationChatHistory conversation, String subagentId, String providerId) {
		return (conversation.providerSessions() == null ? List.<ProviderSession>of() : conversation.providerSessions()).stream()
				.filter(session -> subagentId.equals(session.subagentId()) && providerId.equals(session.providerId())).findFirst().orElse(null);
	}

	private String content(Document message) { Object value = message.get("content"); return value == null ? "" : value.toString(); }

	private Map<String, Object> output(Map<String, Object> toolOutput, String assistantOutput) {
		if (toolOutput == null) return Map.of("content", assistantOutput == null ? "" : assistantOutput);

		Map<String, Object> result = new LinkedHashMap<>(toolOutput);
		String toolContent = String.valueOf(toolOutput.getOrDefault("content", ""));
		String finalContent = StringUtils.hasText(assistantOutput) ? assistantOutput : toolContent;
		result.put("content", resolveVisualizations(finalContent, toolOutput));
		return result;
	}

	private String resolveVisualizations(String content, Map<String, Object> toolOutput) {
		Map<String, String> rendered = renderedVisualizations(toolOutput);
		Matcher markers = VISUALIZATION_MARKER.matcher(content == null ? "" : content);
		StringBuffer result = new StringBuffer();
		Set<String> used = new HashSet<>();
		while (markers.find()) {
			String id = markers.group(1);
			String replacement = used.add(id) ? rendered.getOrDefault(id, "") : "";
			markers.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		markers.appendTail(result);

		for (Map.Entry<String, String> visualization : rendered.entrySet()) {
			if (used.add(visualization.getKey())) {
				if (!result.isEmpty()) result.append("\n\n");
				result.append(visualization.getValue());
			}
		}
		return result.toString().trim();
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> renderedVisualizations(Map<String, Object> toolOutput) {
		Object artifactsValue = toolOutput.get("artifacts");
		if (!(artifactsValue instanceof Map<?, ?> artifacts)) return Map.of();
		Object visualizationsValue = artifacts.get("visualizations");
		if (!(visualizationsValue instanceof List<?> visualizations)) return Map.of();

		Map<String, String> rendered = new LinkedHashMap<>();
		for (Object value : visualizations) {
			if (!(value instanceof Map<?, ?> raw)) continue;
			Map<String, Object> artifact = (Map<String, Object>) raw;
			String id = String.valueOf(artifact.getOrDefault("id", ""));
			if (!StringUtils.hasText(id)) continue;
			Object html = artifact.get("html");
			Object spec = artifact.get("spec");
			if (html != null) rendered.put(id, String.valueOf(html));
			else if (spec != null) rendered.put(id, "```vega-lite\n" + spec + "\n```");
		}
		return rendered;
	}
	private String truncate(String value) { return value == null || value.length() <= 1200 ? value : value.substring(0, 1200) + "..."; }
	private List<SubagentExecution> safeExecutions(MainConversationChatHistory conversation) { return conversation.subagentsExecutions() == null ? List.of() : conversation.subagentsExecutions(); }
	private List<ConversationWorkContext> safeContexts(MainConversationChatHistory conversation) { return conversation.workContexts() == null ? List.of() : conversation.workContexts(); }
}
