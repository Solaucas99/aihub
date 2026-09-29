package com.lamorim.hubdeia.application.provider.genie;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.application.provider.ProviderTool;
import com.lamorim.hubdeia.application.provider.ProviderToolContext;
import com.lamorim.hubdeia.application.provider.ProviderToolResult;
import com.lamorim.hubdeia.domain.model.ProviderSession;

@Component
public class GenieMessageTool implements ProviderTool {

	private final GenieClient genieClient;
	private final GenieResponseParser parser;

	public GenieMessageTool(GenieClient genieClient, GenieResponseParser parser) {
		this.genieClient = genieClient;
		this.parser = parser;
	}

	@Override
	public String name() {
		return "tool-enviar-mensagem-genie";
	}

	@Override
	public Document schema() {
		return new Document()
				.append("type", "function")
				.append("function", new Document()
						.append("name", name())
						.append("description", "Consulta a Genie usando a conversa existente quando disponivel.")
						.append("strict", true)
						.append("parameters", new Document()
								.append("type", "object")
								.append("additionalProperties", false)
								.append("required", java.util.List.of("message"))
								.append("properties", new Document()
										.append("message", new Document()
												.append("type", "string")
												.append("description", "Pergunta ou instrucao de negocio para a Genie.")))));
	}

	@Override
	public ProviderToolResult execute(ProviderToolContext context, Document arguments) {
		String spaceId = resolveSpaceId(context);
		String existingConversationId = context.existingSession() == null
				? null
				: context.existingSession().externalSessionId();
		GenieMessageResult result = genieClient.send(
				spaceId,
				existingConversationId,
				arguments.getString("message"),
				context.progressReporter()
		);
		context.progressReporter().report("GENIE_PROCESSING", "Processando resposta Genie");
		Map<String, Object> structured = parser.parse(result, context.progressReporter());
		Instant now = Instant.now();
		ProviderSession session = new ProviderSession(
				context.existingSession() == null ? UUID.randomUUID().toString() : context.existingSession().id(),
				context.subagentId(),
				context.subagentProvider().providerId(),
				result.conversationId(),
				"ACTIVE",
				Map.of("message_id", result.messageId(), "space_id", spaceId),
				context.existingSession() == null ? now : context.existingSession().createdAt(),
				now
		);

		return new ProviderToolResult(
				String.valueOf(structured.get("content")),
				structured,
				session
		);
	}

	@SuppressWarnings("unchecked")
	private String resolveSpaceId(ProviderToolContext context) {
		Object space = context.subagentProvider().config().get("space_id");
		if (space instanceof String value) {
			return value;
		}
		if (space instanceof Map<?, ?> map) {
			Object env = map.get("$env");
			if (env != null && System.getenv(String.valueOf(env)) != null) {
				return System.getenv(String.valueOf(env));
			}
			Object fallbackEnv = map.get("fallback_env");
			if (fallbackEnv != null && System.getenv(String.valueOf(fallbackEnv)) != null) {
				return System.getenv(String.valueOf(fallbackEnv));
			}
			return String.valueOf(map.get("fallback"));
		}
		throw new IllegalStateException("Genie provider config requires space_id.");
	}
}
