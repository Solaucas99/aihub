package com.lamorim.hubdeia.application.communication;

import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.config.HubRestClientFactory;

@Component
public class CommunicatorAgent {

	private final RestClient restClient;
	private final String apiKey;
	private final String model;

	public CommunicatorAgent(
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

	public String introduceFinishedRun(List<Task> finishedTasks, List<Task> cancelledTasks) {
		if (!StringUtils.hasText(apiKey)) {
			return fallbackFinished(finishedTasks, cancelledTasks);
		}

		Document payload = new Document()
				.append("status", "COMPLETED")
				.append("finished_tasks", taskSummaries(finishedTasks))
				.append("cancelled_tasks", taskSummaries(cancelledTasks));
		return ask(payload, fallbackFinished(finishedTasks, cancelledTasks));
	}

	public String introduceCancelledRun(String reason) {
		if (!StringUtils.hasText(apiKey)) {
			return fallbackCancelled(reason);
		}

		Document payload = new Document()
				.append("status", "CANCELLED")
				.append("reason", reason);
		return ask(payload, fallbackCancelled(reason));
	}

	public String introducePartialRun(List<Task> finishedTasks, List<HumanInputRequest> pendingRequests) {
		if (!StringUtils.hasText(apiKey)) {
			return "Consolidei os resultados disponiveis. Ainda preciso de algumas informacoes para continuar o restante.";
		}
		Document payload = new Document()
				.append("status", "PARTIAL")
				.append("finished_tasks", taskSummaries(finishedTasks))
				.append("pending_questions", pendingRequests.stream().map(HumanInputRequest::question).toList());
		return ask(payload, "Consolidei os resultados disponiveis. Ainda preciso de algumas informacoes para continuar o restante.");
	}

	private String ask(Document payload, String fallback) {
		try {
			Document request = new Document()
					.append("model", model)
					.append("messages", List.of(
							new Document()
									.append("role", "system")
									.append("content", systemPrompt()),
							new Document()
									.append("role", "user")
									.append("content", payload.toJson())));

			String response = restClient.post()
					.uri("/chat/completions")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(request.toJson())
					.retrieve()
					.body(String.class);

			String content = Document.parse(response)
					.getList("choices", Document.class)
					.getFirst()
					.get("message", Document.class)
					.getString("content");
			return StringUtils.hasText(content) ? content : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private String systemPrompt() {
		return String.join("\n",
				"Voce e o comunicador final de um CRM agentico.",
				"Escreva apenas uma introducao curta ao usuario, em markdown simples.",
				"Nao escreva JSON, IDs, tools, providers ou detalhes internos.",
				"Nao reproduza a resposta completa dos subagentes.",
				"As respostas completas dos agentes serao exibidas depois da sua introducao.",
				"Nao invente resultados nem apresente uma task cancelada como concluida.");
	}

	private List<Document> taskSummaries(List<Task> tasks) {
		return tasks.stream()
				.map(task -> new Document()
						.append("description", task.description())
						.append("subagent_id", task.subagentSelected())
						.append("status", task.status()))
				.toList();
	}

	private String fallbackFinished(List<Task> finishedTasks, List<Task> cancelledTasks) {
		if (cancelledTasks.isEmpty()) {
			return "Consolidei os resultados das analises solicitadas.";
		}
		return "Consolidei os resultados disponiveis. Algumas analises nao entraram na resposta final porque foram canceladas.";
	}

	private String fallbackCancelled(String reason) {
		if (!StringUtils.hasText(reason)) {
			return "Nao foi possivel concluir esta execucao agora.";
		}
		return "Nao foi possivel concluir esta execucao agora. " + reason;
	}
}
