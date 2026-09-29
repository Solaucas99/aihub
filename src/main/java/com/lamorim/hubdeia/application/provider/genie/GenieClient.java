package com.lamorim.hubdeia.application.provider.genie;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lamorim.hubdeia.application.provider.ProgressReporter;
import com.lamorim.hubdeia.config.HubRestClientFactory;

@Component
public class GenieClient {

	private static final Logger logger = LoggerFactory.getLogger(GenieClient.class);

	private final RestClient restClient;
	private final GenieAuthClient authClient;
	private final long pollIntervalMs;
	private final long pollTimeoutMs;

	public GenieClient(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			GenieAuthClient authClient,
			@Value("${hub.genie.base-url:}") String baseUrl,
			@Value("${hub.genie.request-timeout-ms:60000}") long requestTimeoutMs,
			@Value("${hub.genie.poll-interval-ms:8000}") long pollIntervalMs,
			@Value("${hub.genie.poll-timeout-ms:1800000}") long pollTimeoutMs
	) {
		this.restClient = restClients.create(restClientBuilder, baseUrl, requestTimeoutMs);
		this.authClient = authClient;
		this.pollIntervalMs = pollIntervalMs;
		this.pollTimeoutMs = pollTimeoutMs;
	}

	public GenieMessageResult send(
			String spaceId,
			String conversationId,
			String message,
			ProgressReporter reporter
	) {
		String activeConversationId = conversationId;
		try {
			if (activeConversationId == null || activeConversationId.isBlank()) {
				reporter.report("GENIE_STARTING", "Iniciando conversa Genie");
				logger.info("Starting Genie conversation. spaceId={}", spaceId);
				activeConversationId = startConversation(spaceId, message);
			}

			reporter.report("GENIE_SENDING", "Consultando Genie");
			logger.info("Sending Genie message. spaceId={}, conversationId={}", spaceId, activeConversationId);
			String messageId = sendMessage(spaceId, activeConversationId, message);
			reporter.report("GENIE_POLLING", "Aguardando resposta Genie");
			Map<String, Object> result = poll(spaceId, activeConversationId, messageId, reporter);
			logger.info("Genie message completed. spaceId={}, conversationId={}, messageId={}", spaceId, activeConversationId, messageId);
			return new GenieMessageResult(activeConversationId, messageId, result);
		} catch (RestClientResponseException error) {
			logger.error("Genie HTTP request failed. spaceId={}, conversationId={}, status={}, response={}", spaceId, activeConversationId,
					error.getStatusCode(), error.getResponseBodyAsString(), error);
			throw error;
		} catch (Exception error) {
			logger.error("Genie execution failed. spaceId={}, conversationId={}", spaceId, activeConversationId, error);
			throw error;
		}
	}

	@SuppressWarnings("unchecked")
	private String startConversation(String spaceId, String message) {
		Map<String, Object> response = restClient.post()
				.uri("/api/2.0/data-rooms/{spaceId}/conversations", spaceId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"title", message,
						"model", "SMART_AI",
						"visibility", "PRIVATE",
						"conversation_type", "DEEP_RESEARCH",
						"utm_metadata", Map.of()))
				.retrieve()
				.body(Map.class);
		return String.valueOf(response.get("conversation_id"));
	}

	@SuppressWarnings("unchecked")
	private String sendMessage(String spaceId, String conversationId, String message) {
		Map<String, Object> response = restClient.post()
				.uri("/api/2.0/data-rooms/{spaceId}/conversations/{conversationId}/messages", spaceId, conversationId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"content", message,
						"client_context", Map.of(
								"genie_app_context", Map.of("force_deep_research_planning", true))))
				.retrieve()
				.body(Map.class);
		return String.valueOf(response.get("message_id"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> poll(
			String spaceId,
			String conversationId,
			String messageId,
			ProgressReporter reporter
	) {
		long deadline = System.nanoTime() + Duration.ofMillis(pollTimeoutMs).toNanos();
		while (System.nanoTime() < deadline) {
			sleep();
			Map<String, Object> response = restClient.get()
					.uri("/api/2.0/data-rooms/{spaceId}/conversations/{conversationId}/messages/{messageId}",
							spaceId,
							conversationId,
							messageId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.accessToken())
					.retrieve()
					.body(Map.class);

			String status = String.valueOf(response.get("status"));
			reporter.report("GENIE_" + status, "Genie: " + status);
			if ("COMPLETED".equals(status)) {
				return response;
			}
			if ("FAILED".equals(status)) {
				logger.error("Genie returned FAILED. spaceId={}, conversationId={}, messageId={}, response={}", spaceId, conversationId, messageId, response);
				throw new IllegalStateException("A consulta Genie falhou.");
			}
		}
		throw new IllegalStateException("A consulta Genie excedeu o timeout configurado.");
	}

	private void sleep() {
		try {
			Thread.sleep(pollIntervalMs);
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Polling Genie interrompido.", error);
		}
	}
}
