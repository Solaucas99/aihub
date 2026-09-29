package com.lamorim.hubdeia.application.planner;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.lamorim.hubdeia.application.ConversationMemorySanitizer;
import com.lamorim.hubdeia.config.HubRestClientFactory;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.AgentRun;

@Component
public class OpenAiPlannerClient {

	private final RestClient restClient;
	private final String apiKey;
	private final String model;
	private final ConversationMemorySanitizer sanitizer;

	public OpenAiPlannerClient(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			@Value("${hub.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${hub.openai.api-key:}") String apiKey,
			@Value("${hub.openai.model:gpt-5}") String model,
			@Value("${hub.openai.timeout-ms:60000}") long timeoutMs,
			ConversationMemorySanitizer sanitizer
	) {
		this.restClient = restClients.create(restClientBuilder, baseUrl, timeoutMs);
		this.apiKey = apiKey;
		this.model = model;
		this.sanitizer = sanitizer;
	}

	public boolean isEnabled() {
		return StringUtils.hasText(apiKey);
	}

	public PlannerDecision plan(MainConversationChatHistory conversation, String userMessage, List<PlanningWorkContextView> contexts) {
		Document request = new Document()
				.append("model", model)
				.append("messages", messages(conversation, userMessage, contexts))
				.append("response_format", responseFormat());
		String response = restClient.post()
				.uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request.toJson())
				.retrieve()
				.body(String.class);
		return parse(Document.parse(response));
	}

	private List<Document> messages(MainConversationChatHistory conversation, String userMessage, List<PlanningWorkContextView> contexts) {
		List<Document> messages = new ArrayList<>();
		messages.add(new Document("role", "system").append("content", systemPrompt()));
		List<Message> history = historyBeforeCurrentMessage(conversation.messages(), userMessage);
		history.stream().skip(Math.max(0, history.size() - 10))
				.map(message -> new Document("role", message.role()).append("content", sanitizer.sanitizeForAgent(message.content())))
				.forEach(messages::add);
		messages.add(new Document("role", "user").append("content", planningInput(conversation, userMessage, contexts).toJson()));
		return messages;
	}

	private String systemPrompt() {
		return String.join("\n",
				"PAPEL",
				"Voce e o planejador de um Hub de IA conversacional. Sua funcao e decidir com precisao se a mensagem atual deve receber uma resposta direta ou se precisa de especialistas para consultar dados e produzir uma resposta confiavel.",
				"O usuario conversa livremente. WorkContext organiza especialistas, mas nunca limita nem interrompe a conversa.",
				"",
				"RESPOSTA DIRETA",
				"Use should_create_tasks=false para saudacoes, despedidas, agradecimentos, explicacoes, reformulacoes, pedidos para repetir algo, perguntas gerais e qualquer pedido que possa ser respondido sem consultar dados externos.",
				"Nessa decisao, response_message deve ser a resposta final que o usuario leria: clara, natural, curta quando o pedido for simples e no idioma do usuario. Nao mencione JSON, tasks, WorkContext, agentes ou o processo interno.",
				"Nunca invente dados, resultados de campanhas, clientes ou fatos que exigem consulta. Quando a solicitacao estiver fora dos WorkContexts disponiveis e exigir dados especializados, explique a limitacao de forma objetiva em response_message e nao crie tasks.",
				"",
				"DELEGACAO",
				"Use should_create_tasks=true somente quando a solicitacao precisar de dados, metricas, segmentacao, clientes, campanhas ou uma analise que um subagente precise consultar na fonte autorizada.",
				"Escolha no maximo um WorkContext. Se a solicitacao exige delegacao, work_context_id deve ser um id presente em available_work_contexts. Escolha subagent_id somente entre os subagents daquele contexto.",
				"Cada task deve ser autocontida, orientada a resultado e ter um unico dono. Inclua em enriched_content a pergunta do usuario, filtros, periodo, metricas, criterios e o resultado esperado. Nao atribua ao subagente tarefas fora de sua descricao.",
				"Crie varias tasks apenas quando especialistas diferentes precisarem produzir partes independentes da resposta. Se um unico especialista resolve o pedido, crie uma unica task. As tasks deste corte executam em paralelo: depends_on deve ser sempre um array vazio.",
				"Quando should_create_tasks=true, response_message deve conter apenas uma atualizacao breve e natural para o usuario, por exemplo: 'Vou analisar os dados solicitados.'",
				"",
				"WORKCONTEXT E HISTORICO",
				"Use NONE quando nao houver delegacao. Use KEEP para continuar o WorkContext ativo, ACTIVATE para iniciar outro e REACTIVATE para retornar a um pausado. Nunca use KEEP sem contexto ativo.",
				"Considere a conversa anterior para resolver referencias como 'isso', 'a campanha anterior' e 'continue'. A mensagem atual em current_request e a solicitacao que voce deve decidir agora.",
				"Se a mensagem responder uma pending_human_input_request, informe seu id em related_human_input_request_id. Caso contrario, envie uma string vazia.",
				"",
				"CONTRATO DE SAIDA",
				"Retorne somente JSON valido conforme o schema. Preencha todas as strings: use string vazia quando um campo nao se aplicar. should_create_tasks=false exige tasks vazio. should_create_tasks=true exige ao menos uma task valida.");
	}

	private Document planningInput(MainConversationChatHistory conversation, String userMessage, List<PlanningWorkContextView> contexts) {
		return new Document("current_request", userMessage)
				.append("active_context", activeContext(conversation))
				.append("paused_contexts", pausedContexts(conversation))
				.append("pending_human_input_requests", pendingRequests(conversation))
				.append("available_work_contexts", contexts.stream().map(context -> new Document()
						.append("id", context.id()).append("key", context.key()).append("name", context.name()).append("description", context.description())
						.append("instructions", context.instructions())
						.append("subagents", context.subagents().stream().map(agent -> new Document()
								.append("id", agent.id()).append("name", agent.name()).append("description", agent.description())).toList()))
						.toList());
	}

	private Document responseFormat() {
		Document task = new Document("type", "object")
				.append("additionalProperties", false)
				.append("required", List.of("task_description", "enriched_content", "subagent_id", "depends_on"))
				.append("properties", new Document()
						.append("task_description", new Document("type", "string"))
						.append("enriched_content", new Document("type", "string"))
						.append("subagent_id", new Document("type", "string"))
						.append("depends_on", new Document("type", "array").append("items", new Document("type", "string"))));
		Document schema = new Document("type", "object")
				.append("additionalProperties", false)
				.append("required", List.of("should_create_tasks", "response_message", "work_context_action", "work_context_id", "related_human_input_request_id", "tasks"))
				.append("properties", new Document()
						.append("should_create_tasks", new Document("type", "boolean"))
						.append("response_message", new Document("type", "string"))
						.append("work_context_action", new Document("type", "string").append("enum", List.of("NONE", "KEEP", "ACTIVATE", "REACTIVATE")))
						.append("work_context_id", new Document("type", "string"))
						.append("related_human_input_request_id", new Document("type", "string"))
						.append("tasks", new Document("type", "array").append("items", task)));
		return new Document("type", "json_schema")
				.append("json_schema", new Document("name", "agent_run_plan").append("strict", true).append("schema", schema));
	}

	@SuppressWarnings("unchecked")
	private PlannerDecision parse(Document response) {
		Document message = response.getList("choices", Document.class).getFirst().get("message", Document.class);
		Document content = Document.parse(message.getString("content"));
		List<Document> rawTasks = content.get("tasks", List.class);
		List<PlannedTask> tasks = rawTasks == null ? List.of() : rawTasks.stream()
				.map(task -> new PlannedTask(task.getString("task_description"), task.getString("enriched_content"), task.getString("subagent_id"), (List<String>) task.get("depends_on", List.class)))
				.toList();
		return new PlannerDecision(Boolean.TRUE.equals(content.getBoolean("should_create_tasks")), content.getString("response_message"),
				content.getString("work_context_action"), content.getString("work_context_id"), content.getString("related_human_input_request_id"), tasks);
	}

	private Document activeContext(MainConversationChatHistory conversation) {
		return safeContexts(conversation).stream().filter(context -> context.id().equals(conversation.activeWorkContextId())).findFirst()
				.map(context -> contextSummary(conversation, context))
				.orElseGet(Document::new);
	}

	private List<Document> pausedContexts(MainConversationChatHistory conversation) {
		return safeContexts(conversation).stream().filter(context -> !context.id().equals(conversation.activeWorkContextId()))
				.map(context -> contextSummary(conversation, context)).toList();
	}

	private Document contextSummary(MainConversationChatHistory conversation, ConversationWorkContext context) {
		return new Document("id", context.id())
				.append("work_context_id", context.workContextId())
				.append("last_agent_run_id", context.lastAgentRunId())
				.append("last_agent_run_message", lastRunMessage(conversation, context.lastAgentRunId()));
	}

	private String lastRunMessage(MainConversationChatHistory conversation, String runId) {
		if (!StringUtils.hasText(runId)) return "";
		return safeRuns(conversation).stream().filter(run -> runId.equals(run.id())).findFirst()
				.map(AgentRun::agentRunLastMessage).orElse("");
	}

	private List<Document> pendingRequests(MainConversationChatHistory conversation) {
		return safeRequests(conversation).stream().filter(request -> "PENDING".equals(request.status()))
				.map(request -> new Document("id", request.id()).append("agent_run_id", request.agentRunId()).append("task_id", request.taskId()).append("question", request.question())).toList();
	}

	private List<Message> safeMessages(List<Message> values) { return values == null ? List.of() : values; }
	private List<AgentRun> safeRuns(MainConversationChatHistory conversation) { return conversation.agentRuns() == null ? List.of() : conversation.agentRuns(); }
	private List<ConversationWorkContext> safeContexts(MainConversationChatHistory conversation) { return conversation.workContexts() == null ? List.of() : conversation.workContexts(); }
	private List<HumanInputRequest> safeRequests(MainConversationChatHistory conversation) { return conversation.humanInputRequests() == null ? List.of() : conversation.humanInputRequests(); }

	private List<Message> historyBeforeCurrentMessage(List<Message> values, String userMessage) {
		List<Message> history = safeMessages(values);
		if (history.isEmpty()) return history;
		Message latest = history.getLast();
		if ("user".equals(latest.role()) && userMessage.equals(latest.content())) {
			return history.subList(0, history.size() - 1);
		}
		return history;
	}
}
