package com.lamorim.hubdeia.application.planner;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.application.ArtifactView;
import com.lamorim.hubdeia.application.ConversationMemorySanitizer;
import com.lamorim.hubdeia.application.Text;
import com.lamorim.hubdeia.application.llm.OpenAiChatClient;
import com.lamorim.hubdeia.domain.model.HumanInputRequest;

/** Builds the planner prompt and parses the structured decision returned by the model. */
@Component
public class OpenAiPlannerClient {

	private static final int ARTIFACT_SUMMARY_CHARS = 1200;

	private final OpenAiChatClient chat;
	private final String model;
	private final ConversationMemorySanitizer sanitizer;

	public OpenAiPlannerClient(
			OpenAiChatClient chat,
			@Value("${hub.openai.model:gpt-5}") String model,
			ConversationMemorySanitizer sanitizer
	) {
		this.chat = chat;
		this.model = model;
		this.sanitizer = sanitizer;
	}

	public PlannerDecision plan(PlanningInput input) {
		Document request = new Document("model", model)
				.append("messages", messages(input))
				.append("response_format", responseFormat());
		return parse(chat.completeJson(request));
	}

	private List<Document> messages(PlanningInput input) {
		List<Document> messages = new ArrayList<>();
		messages.add(new Document("role", "system").append("content", systemPrompt()));
		input.history().forEach(message -> messages.add(
				new Document("role", message.role()).append("content", sanitizer.sanitizeForAgent(message.content()))));
		messages.add(new Document("role", "user").append("content", planningInput(input).toJson()));
		return messages;
	}

	private String systemPrompt() {
		return String.join("\n",
				"PAPEL",
				"Voce e o planejador de um Hub de IA conversacional. Sua funcao e decidir quem faz o que: responder direto ou acionar especialistas que consultam dados e produzem uma resposta confiavel.",
				"O usuario conversa livremente. WorkContext organiza especialistas, mas nunca limita nem interrompe a conversa.",
				"Voce NAO acessa fontes de dados, NAO calcula e NAO produz graficos, tabelas, metricas ou analises. Isso e trabalho exclusivo dos especialistas, que consultam a fonte de dados e dispoem das ferramentas de visualizacao. Nunca diga ao usuario que nao ha ferramenta ou acesso para algo que um especialista pode fazer: delegue.",
				"",
				"LEIA O CONTEXTO ANTES DE DECIDIR",
				"Nunca decida olhando apenas current_request. Antes de qualquer decisao, leia o contexto nesta ordem:",
				"1. As mensagens anteriores da conversa.",
				"2. recent_runs: o que cada especialista ja executou nesta conversa, o que lhe foi pedido (request) e o que ele devolveu (result).",
				"3. artifacts: resultados ja produzidos e guardados.",
				"4. active_context e paused_contexts: em que modo de trabalho a conversa esta.",
				"5. pending_human_input_requests: perguntas que ainda aguardam resposta do usuario.",
				"Referencias como 'isso', 'esses dados', 'o grafico', 'continue', 'de novo' e 'agora' apontam para o resultado mais recente desse contexto. Resolva a referencia antes de classificar o pedido.",
				"Depois classifique current_request em uma destas tres situacoes:",
				"a) CONTINUACAO de trabalho de especialista: o usuario pede algo novo sobre dados ou resultados que um especialista ja produziu, como um formato (grafico, tabela, ranking, comparativo), um recorte (periodo, segmento, canal) ou um detalhamento. Exemplos: 'gere graficos dos dados', 'agora so os jovens', 'mostre em tabela', 'detalhe por canal'. DEVOLVA o trabalho ao mesmo especialista que produziu o resultado anterior (veja recent_runs e artifacts), com work_context_action KEEP.",
				"b) NOVO pedido de dados ou analise: delegue ao especialista adequado.",
				"c) CONVERSA: saudacao, agradecimento, ou explicacao e reformulacao de algo que ja esta escrito na conversa e pode ser respondido so com esse texto: responda direto.",
				"Regra de ouro: graficos, tabelas, calculos e numeros novos nunca saem de voce, mesmo quando os dados parecerem estar no historico. O historico pode ter apenas um resumo, e quem tem a fonte e as ferramentas e o especialista. Se o pedido toca em dados ou resultados anteriores e voce estiver em duvida entre responder e delegar, delegue.",
				"",
				"RESPOSTA DIRETA",
				"Use should_create_tasks=false somente quando o pedido nao exigir consultar nem transformar dados: saudacoes, despedidas, agradecimentos, explicar ou reformular algo que ja esta escrito na conversa, pedidos para repetir algo e perguntas gerais. Nunca use resposta direta para produzir graficos, tabelas, calculos ou numeros novos, nem para sugerir codigo ou ferramentas externas no lugar do especialista.",
				"Nessa decisao, response_message deve ser a resposta final que o usuario leria: clara, natural, curta quando o pedido for simples e no idioma do usuario. Nao mencione JSON, tasks, WorkContext, agentes ou o processo interno.",
				"Nunca invente dados, resultados de campanhas, clientes ou fatos que exigem consulta. Quando a solicitacao estiver fora dos WorkContexts disponiveis e exigir dados especializados, explique a limitacao de forma objetiva em response_message e nao crie tasks.",
				"Se a conversa ainda nao permite decidir como dividir o trabalho (falta periodo, segmento, campanha ou objetivo), faca uma pergunta objetiva em response_message e nao crie tasks. A resposta do usuario volta para voce na proxima mensagem.",
				"",
				"DELEGACAO",
				"Use should_create_tasks=true somente quando a solicitacao precisar de dados, metricas, segmentacao, clientes, campanhas ou uma analise que um subagente precise consultar na fonte autorizada.",
				"Escolha no maximo um WorkContext. Se a solicitacao exige delegacao, work_context_id deve ser um id presente em available_work_contexts. Escolha subagent_id somente entre os subagents daquele contexto.",
				"O especialista NAO ve a conversa. Cada task deve ser autocontida, orientada a resultado e ter um unico dono. Inclua em enriched_content: o que o usuario pede agora; o contexto herdado da conversa que o especialista precisa (tema, metrica, periodo, filtros e segmento ja analisados, e o que ele ja produziu antes); e o resultado esperado, incluindo o formato (por exemplo: grafico de barras de page_view por dia). Nao atribua ao subagente tarefas fora de sua descricao.",
				"Em continuacoes, use o mesmo subagent_id do trabalho anterior e cite explicitamente o resultado a ser reaproveitado.",
				"Crie varias tasks apenas quando especialistas diferentes precisarem produzir partes independentes da resposta. Se um unico especialista resolve o pedido, crie uma unica task. As tasks sem relacao entre si executam em paralelo.",
				"Se um subagente consome um tipo de resultado (consumes) que outro produz (produces) e o pedido exige os dois, crie as duas tasks: o sistema ordena sozinho e o consumidor parte do resultado novo. Nao declare dependencias.",
				"Quando should_create_tasks=true, response_message deve conter apenas uma atualizacao breve e natural para o usuario, por exemplo: 'Vou analisar os dados solicitados.'",
				"",
				"WORKCONTEXT E HISTORICO",
				"Use NONE quando nao houver delegacao. Use KEEP para continuar o WorkContext ativo. Use ACTIVATE para iniciar ou retornar a outro, informando o id do catalogo em work_context_id. Nunca use KEEP sem contexto ativo.",
				"Considere a conversa anterior para resolver referencias como 'isso', 'a campanha anterior' e 'continue'. A mensagem atual em current_request e a solicitacao que voce deve decidir agora.",
				"Se a mensagem responder uma pending_human_input_request, informe seu id em related_human_input_request_id. Caso contrario, envie uma string vazia.",
				"",
				"ARTEFATOS",
				"artifacts lista resultados ja produzidos nesta conversa (tipo, versao, status ACTIVE ou SUPERSEDED, stale, titulo e resumo).",
				"Se um artefato ACTIVE e nao stale ja responde ao pedido com os mesmos filtros, periodo e foco, responda direto com base nele ou crie tasks apenas para o que falta. Se o usuario mudar filtros, periodo ou foco, ou pedir dados mais recentes, crie tasks novamente.",
				"Quando o usuario pedir para voltar a uma versao anterior (por exemplo 'volta pro briefing anterior'), informe em reactivate_artifact_id o id de um artefato da lista e confirme em response_message. Nunca invente ids. Caso contrario, envie uma string vazia.",
				"",
				"CONTRATO DE SAIDA",
				"Retorne somente JSON valido conforme o schema. Preencha todas as strings: use string vazia quando um campo nao se aplicar. should_create_tasks=false exige tasks vazio. should_create_tasks=true exige ao menos uma task valida.");
	}

	private Document planningInput(PlanningInput input) {
		return new Document("current_request", input.currentRequest())
				.append("recent_runs", input.recentRuns().stream().map(this::run).toList())
				.append("active_context", input.activeContext() == null ? new Document() : contextState(input.activeContext()))
				.append("paused_contexts", input.pausedContexts().stream().map(this::contextState).toList())
				.append("pending_human_input_requests", input.pendingRequests().stream().map(this::pendingRequest).toList())
				.append("artifacts", input.artifacts().stream().map(this::artifact).toList())
				.append("available_work_contexts", input.availableContexts().stream().map(this::availableContext).toList());
	}

	private Document run(PlanningRunSummary run) {
		return new Document("agent_run_id", run.runId()).append("status", run.status()).append("tasks", run.tasks().stream()
				.map(task -> new Document("subagent_id", task.subagentId()).append("description", task.description()).append("status", task.status())
						.append("request", task.request()).append("result", task.result())).toList());
	}

	private Document contextState(PlanningContextState state) {
		return new Document("id", state.id())
				.append("work_context_id", state.workContextId())
				.append("last_agent_run_id", state.lastAgentRunId())
				.append("last_agent_run_message", state.lastAgentRunMessage());
	}

	private Document pendingRequest(HumanInputRequest request) {
		return new Document("id", request.id()).append("agent_run_id", request.agentRunId())
				.append("task_id", request.taskId()).append("question", request.question());
	}

	private Document artifact(ArtifactView artifact) {
		return new Document("id", artifact.id())
				.append("type", artifact.type())
				.append("version", artifact.version())
				.append("status", artifact.status().name())
				.append("stale", artifact.stale())
				.append("title", artifact.title())
				.append("subagent_id", artifact.subagentId())
				.append("created_at", artifact.createdAt().toString())
				.append("summary", Text.truncate(artifact.content(), ARTIFACT_SUMMARY_CHARS));
	}

	private Document availableContext(PlanningWorkContextView context) {
		return new Document()
				.append("id", context.id()).append("key", context.key()).append("name", context.name()).append("description", context.description())
				.append("instructions", context.instructions())
				.append("subagents", context.subagents().stream().map(agent -> new Document()
						.append("id", agent.id()).append("name", agent.name()).append("description", agent.description())
						.append("produces", agent.produces()).append("consumes", agent.consumes())).toList());
	}

	private Document responseFormat() {
		Document task = new Document("type", "object")
				.append("additionalProperties", false)
				.append("required", List.of("task_description", "enriched_content", "subagent_id"))
				.append("properties", new Document()
						.append("task_description", new Document("type", "string"))
						.append("enriched_content", new Document("type", "string"))
						.append("subagent_id", new Document("type", "string")));
		Document schema = new Document("type", "object")
				.append("additionalProperties", false)
				.append("required", List.of("should_create_tasks", "response_message", "work_context_action", "work_context_id",
						"related_human_input_request_id", "reactivate_artifact_id", "tasks"))
				.append("properties", new Document()
						.append("should_create_tasks", new Document("type", "boolean"))
						.append("response_message", new Document("type", "string"))
						.append("work_context_action", new Document("type", "string").append("enum", List.of("NONE", "KEEP", "ACTIVATE")))
						.append("work_context_id", new Document("type", "string"))
						.append("related_human_input_request_id", new Document("type", "string"))
						.append("reactivate_artifact_id", new Document("type", "string"))
						.append("tasks", new Document("type", "array").append("items", task)));
		return new Document("type", "json_schema")
				.append("json_schema", new Document("name", "agent_run_plan").append("strict", true).append("schema", schema));
	}

	private PlannerDecision parse(Document content) {
		List<PlannedTask> tasks = content.getList("tasks", Document.class, List.of()).stream()
				.map(task -> new PlannedTask(task.getString("task_description"), task.getString("enriched_content"), task.getString("subagent_id")))
				.toList();
		return new PlannerDecision(Boolean.TRUE.equals(content.getBoolean("should_create_tasks")), content.getString("response_message"),
				WorkContextAction.parse(content.getString("work_context_action")), content.getString("work_context_id"),
				content.getString("related_human_input_request_id"), content.getString("reactivate_artifact_id"), tasks);
	}
}
