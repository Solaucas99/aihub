package com.lamorim.hubdeia.application.validation;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.TaskExecutionService;
import com.lamorim.hubdeia.application.Text;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.llm.OpenAiChatClient;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.ToolCall;

/** LLM-as-a-judge that decides whether a subagent output is good enough to continue. */
@Component
public class TaskValidatorAgent {

	private static final int MEMORY_MESSAGES = 8;
	private static final int MAX_TEXT_CHARS = 4000;

	private final OpenAiChatClient chat;
	private final ConversationService conversations;
	private final TaskExecutionService executions;
	private final String model;

	public TaskValidatorAgent(
			OpenAiChatClient chat,
			ConversationService conversations,
			TaskExecutionService executions,
			@Value("${hub.openai.model:gpt-5}") String model
	) {
		this.chat = chat;
		this.conversations = conversations;
		this.executions = executions;
		this.model = model;
	}

	public TaskValidationResult validate(String conversationId, String agentRunId, Task task) {
		Document payload = new Document()
				.append("conversation_id", conversationId)
				.append("agent_run_id", agentRunId)
				.append("task_id", task.id())
				.append("task_description", task.description())
				.append("task_input", task.input())
				.append("subagent_selected", task.subagentSelected())
				.append("subagent_output", outputPayload(task))
				.append("subagent_execution", executions.latestForTask(task.id()).map(this::executionPayload).orElseGet(Document::new))
				.append("conversation_memory", conversationMemory(conversationId));

		Document request = new Document("model", model)
				.append("messages", List.of(
						new Document("role", "system").append("content", systemPrompt()),
						new Document("role", "user").append("content", payload.toJson())))
				.append("response_format", responseFormat());
		Document content = chat.completeJson(request);
		return new TaskValidationResult(TaskValidationStatus.valueOf(content.getString("status")),
				content.getString("feedback"), content.getString("message_to_user"), Instant.now());
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
				"Retorne apenas JSON no schema solicitado.");
	}

	private Document responseFormat() {
		Document schema = new Document("type", "object")
				.append("additionalProperties", false)
				.append("required", List.of("status", "feedback", "message_to_user"))
				.append("properties", new Document()
						.append("status", new Document("type", "string").append("enum", List.of("RETRY", "CONTINUE", "CANCEL", "HITL")))
						.append("feedback", new Document("type", "string"))
						.append("message_to_user", new Document("type", "string")));
		return new Document("type", "json_schema")
				.append("json_schema", new Document("name", "task_validation").append("strict", true).append("schema", schema));
	}

	private Document executionPayload(SubagentExecution execution) {
		return new Document("id", execution.id())
				.append("subagent_id", execution.subagentId())
				.append("status", execution.status())
				.append("attempt", execution.attempt())
				.append("messages", execution.messages().stream().map(message -> messagePayload(message.role(), message.content())).toList())
				.append("tool_calls", execution.toolCalls().stream().map(this::toolCallPayload).toList());
	}

	/** The judge reads the text and sees which visuals exist, never their HTML or chart specs. */
	private Document outputPayload(Task task) {
		TaskOutput output = TaskOutput.from(task.output());
		return new Document("content", output.plainText()).append("visuals", output.visuals().stream()
				.map(visual -> new Document("id", visual.id()).append("title", visual.title()).append("format", visual.format().name())).toList());
	}

	private Document toolCallPayload(ToolCall toolCall) {
		return new Document("id", toolCall.id())
				.append("tool_id", toolCall.toolId())
				.append("provider_id", toolCall.providerId())
				.append("input", toolCall.input())
				.append("output_text", Text.truncate(toolCall.outputText(), MAX_TEXT_CHARS))
				.append("status", toolCall.status())
				.append("started_at", toolCall.startedAt() == null ? null : toolCall.startedAt().toString())
				.append("finished_at", toolCall.finishedAt() == null ? null : toolCall.finishedAt().toString());
	}

	private List<Document> conversationMemory(String conversationId) {
		return conversations.recent(conversationId, MEMORY_MESSAGES).stream().map(this::messagePayload).toList();
	}

	private Document messagePayload(Message message) {
		return messagePayload(message.role(), message.content());
	}

	private Document messagePayload(String role, String content) {
		return new Document("role", role).append("content", Text.truncate(content, MAX_TEXT_CHARS));
	}
}
