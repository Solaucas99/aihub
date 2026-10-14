package com.lamorim.hubdeia.application.llm;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.lamorim.hubdeia.config.HubRestClientFactory;

/** Single entry point for OpenAI chat completions, shared by every agent. */
@Component
public class OpenAiChatClient {

	private final RestClient restClient;
	private final String apiKey;

	public OpenAiChatClient(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			@Value("${hub.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${hub.openai.api-key:}") String apiKey,
			@Value("${hub.openai.timeout-ms:60000}") long timeoutMs
	) {
		this.restClient = restClients.create(restClientBuilder, baseUrl, timeoutMs);
		this.apiKey = apiKey;
	}

	public boolean isConfigured() {
		return StringUtils.hasText(apiKey);
	}

	/** Posts a chat completion request and returns the first choice's message. */
	public Document complete(Document request) {
		if (!isConfigured()) {
			throw new IllegalStateException("OPENAI_API_KEY nao configurada.");
		}
		String response = restClient.post().uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request.toJson())
				.retrieve()
				.body(String.class);
		return Document.parse(response).getList("choices", Document.class).getFirst().get("message", Document.class);
	}

	/** Same as {@link #complete(Document)} for responses constrained by a JSON schema. */
	public Document completeJson(Document request) {
		return Document.parse(content(complete(request)));
	}

	public static String content(Document message) {
		Object value = message.get("content");
		return value == null ? "" : value.toString();
	}
}
