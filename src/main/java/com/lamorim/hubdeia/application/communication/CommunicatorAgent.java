package com.lamorim.hubdeia.application.communication;

import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.lamorim.hubdeia.application.llm.OpenAiChatClient;
import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.Task;

/** Writes the short introduction shown before the subagents' answers. Falls back to fixed text on any failure. */
@Component
public class CommunicatorAgent {

	private static final String PARTIAL_FALLBACK = "Consolidei os resultados disponiveis. Ainda preciso de algumas informacoes para continuar o restante.";

	private final OpenAiChatClient chat;
	private final String model;

	public CommunicatorAgent(OpenAiChatClient chat, @Value("${hub.openai.model:gpt-5}") String model) {
		this.chat = chat;
		this.model = model;
	}

	public String introduceFinishedRun(List<Task> finishedTasks, List<Task> cancelledTasks) {
		Document payload = new Document("status", "COMPLETED")
				.append("finished_tasks", taskSummaries(finishedTasks))
				.append("cancelled_tasks", taskSummaries(cancelledTasks));
		return ask(payload, finishedFallback(cancelledTasks));
	}

	public String introduceCancelledRun(String reason) {
		return ask(new Document("status", "CANCELLED").append("reason", reason), cancelledFallback(reason));
	}

	public String introducePartialRun(List<Task> finishedTasks, List<HumanInputRequest> pendingRequests) {
		Document payload = new Document("status", "PARTIAL")
				.append("finished_tasks", taskSummaries(finishedTasks))
				.append("pending_questions", pendingRequests.stream().map(HumanInputRequest::question).toList());
		return ask(payload, PARTIAL_FALLBACK);
	}

	private String ask(Document payload, String fallback) {
		if (!chat.isConfigured()) {
			return fallback;
		}
		try {
			Document request = new Document("model", model).append("messages", List.of(
					new Document("role", "system").append("content", systemPrompt()),
					new Document("role", "user").append("content", payload.toJson())));
			String content = OpenAiChatClient.content(chat.complete(request));
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
				.map(task -> new Document("description", task.description())
						.append("subagent_id", task.subagentSelected())
						.append("status", task.status().name()))
				.toList();
	}

	private String finishedFallback(List<Task> cancelledTasks) {
		return cancelledTasks.isEmpty()
				? "Consolidei os resultados das analises solicitadas."
				: "Consolidei os resultados disponiveis. Algumas analises nao entraram na resposta final porque foram canceladas.";
	}

	private String cancelledFallback(String reason) {
		return StringUtils.hasText(reason)
				? "Nao foi possivel concluir esta execucao agora. " + reason
				: "Nao foi possivel concluir esta execucao agora.";
	}
}
