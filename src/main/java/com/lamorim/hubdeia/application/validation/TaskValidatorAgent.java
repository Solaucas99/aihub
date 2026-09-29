package com.lamorim.hubdeia.application.validation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.ToolCall;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.config.HubRestClientFactory;

@Component
public class TaskValidatorAgent {

	private final RestClient restClient;
	private final String apiKey;
	private final String model;

	public TaskValidatorAgent(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			@Value("${hub.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${hub.openai.api-key:}") String apiKey,
			@Value("${hub.openai.model:gpt-5}") String model,
			@Value("${hub.openai.timeout-ms:60000}") long timeoutMs
	) {
		this.restClient = restClients.create(restClientBuilder, baseUrl, timeoutMs);
		this.apiKey = apiKey;
		this.model = model;
	}

	public TaskValidationResult validate(
			MainConversationChatHistory conversation,
			String agentRunId,
			String taskId
	) {
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalStateException("OPENAI_API_KEY nao configurada para executar o agente validador.");
		}

		Task task = task(conversation, agentRunId, taskId);
		Document payload = new Document()
				.append("conversation_id", conversation.id())
				.append("agent_run_id", agentRunId)
				.append("task_id", task.id())
				.append("task_description", task.description())
				.append("task_input", task.input())
				.append("subagent_selected", task.subagentSelected())
				.append("subagent_output", task.output())
				.append("subagent_execution", executionPayload(conversation, task.id()))
				.append("conversation_memory", conversationMemory(conversation));

		Document response = chat(payload);
		Document content = Document.parse(response.getList("choices", Document.class)
				.getFirst()
				.get("message", Document.class)
				.getString("content"));

		return new TaskValidationResult(
				TaskValidationStatus.valueOf(content.getString("status")),
				content.getString("feedback"),
				content.getString("message_to_user"),
				Instant.now()
		);
	}

	private Document chat(Document validationPayload) {
		Document payload = new Document()
				.append("model", model)
				.append("messages", List.of(
						new Document()
								.append("role", "system")
								.append("content", systemPrompt()),
						new Document()
								.append("role", "user")
								.append("content", validationPayload.toJson())))
				.append("response_format", responseFormat());

		String response = restClient.post()
				.uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload.toJson())
				.retrieve()
				.body(String.class);

		return Document.parse(response);
	}

	private String systemPrompt() {
		return String.join("\n",
				"Voce e o agente validador de uma execucao agentica, atuando como LLM-as-a-judge.",
				"Valide a resposta de um subagente contra o objetivo da task, o input recebido e a memoria curta da conversa.",
				"RETRY: resposta nao satisfatoria, acuracia muito baixa, vazia, irrelevante ou falha sistemica de provider/API.",
				"CONTINUE: resposta satisfatoria, util e condizente com o pedido da task.",
				"CANCEL: falha total pos retry ou impedimento que torna a task inutil para a resposta final.",
				"HITL: o subagente precisa de uma decisao, confirmacao ou dado do usuario para conseguir concluir a task.",
				"A regra de HITL tem prioridade: se o output pedir segmento, periodo, filtro, escopo, aprovacao ou qualquer informacao ausente necessaria para concluir a task, escolha HITL.",
				"Nao escolha CONTINUE para uma resposta que apenas pede esclarecimento, mesmo que a pergunta esteja bem escrita ou seja tecnicamente correta.",
				"Exemplo: se a task pede analisar clientes e o output pergunta 'Business, Individual ou ambos?', o status obrigatorio e HITL.",
				"Nao aplique regras de negocio de retry, cancelamento ou HITL; apenas julgue e reporte o estado.",
				"Quando escolher HITL, escreva em message_to_user a pergunta objetiva que o usuario precisa responder para destravar a task.",
				"Quando nao for HITL, deixe message_to_user vazio.",
				"Retorne apenas JSON no schema solicitado."
		);
	}

	private Document responseFormat() {
		Document schema = new Document()
				.append("type", "object")
				.append("additionalProperties", false)
				.append("required", List.of("status", "feedback", "message_to_user"))
				.append("properties", new Document()
						.append("status", new Document()
								.append("type", "string")
								.append("enum", List.of("RETRY", "CONTINUE", "CANCEL", "HITL")))
						.append("feedback", new Document("type", "string"))
						.append("message_to_user", new Document("type", "string")));

		return new Document()
				.append("type", "json_schema")
				.append("json_schema", new Document()
						.append("name", "task_validation")
						.append("strict", true)
						.append("schema", schema));
	}

	private Task task(MainConversationChatHistory conversation, String agentRunId, String taskId) {
		return safeRuns(conversation).stream()
				.filter(run -> run.id().equals(agentRunId))
				.flatMap(run -> safeTasks(run).stream())
				.filter(candidate -> candidate.id().equals(taskId))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Task nao encontrada para validacao: " + taskId));
	}

	private Document executionPayload(MainConversationChatHistory conversation, String taskId) {
		return safeExecutions(conversation).stream()
				.filter(execution -> taskId.equals(execution.taskId()))
				.reduce((first, second) -> second)
				.map(execution -> new Document()
						.append("id", execution.id())
						.append("subagent_id", execution.subagentId())
						.append("status", execution.status())
						.append("attempt", execution.attempt())
						.append("messages", safeMessages(execution.messages()).stream()
								.map(message -> new Document()
										.append("role", message.role())
										.append("content", truncate(message.content())))
								.toList())
						.append("tool_calls", safeToolCalls(execution.toolCalls()).stream()
								.map(this::toolCallPayload)
								.toList()))
				.orElseGet(Document::new);
	}

	private Document toolCallPayload(ToolCall toolCall) {
		return new Document()
				.append("id", toolCall.id())
				.append("tool_id", toolCall.toolId())
				.append("provider_id", toolCall.providerId())
				.append("input", toolCall.input())
				.append("output", toolCall.output())
				.append("status", toolCall.status())
				.append("started_at", toolCall.startedAt() == null ? null : toolCall.startedAt().toString())
				.append("finished_at", toolCall.finishedAt() == null ? null : toolCall.finishedAt().toString());
	}

	private List<Document> conversationMemory(MainConversationChatHistory conversation) {
		List<Message> messages = safeMessages(conversation.messages());
		return messages.stream()
				.skip(Math.max(0, messages.size() - 8))
				.map(message -> new Document()
						.append("role", message.role())
						.append("content", truncate(message.content())))
				.toList();
	}

	private List<AgentRun> safeRuns(MainConversationChatHistory conversation) {
		return conversation.agentRuns() == null ? List.of() : conversation.agentRuns();
	}

	private List<Task> safeTasks(AgentRun run) {
		return run.tasks() == null ? List.of() : run.tasks();
	}

	private List<SubagentExecution> safeExecutions(MainConversationChatHistory conversation) {
		return conversation.subagentsExecutions() == null ? List.of() : conversation.subagentsExecutions();
	}

	private List<Message> safeMessages(List<Message> messages) {
		return messages == null ? List.of() : messages;
	}

	private List<ToolCall> safeToolCalls(List<ToolCall> toolCalls) {
		return toolCalls == null ? List.of() : toolCalls;
	}

	private String truncate(String value) {
		if (value == null || value.length() <= 4000) {
			return value;
		}
		return value.substring(0, 4000) + "...";
	}
}
