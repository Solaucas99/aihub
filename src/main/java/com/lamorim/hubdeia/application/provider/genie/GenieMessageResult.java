package com.lamorim.hubdeia.application.provider.genie;

import java.util.Map;

public record GenieMessageResult(
		String conversationId,
		String messageId,
		Map<String, Object> rawResponse
) {
}
