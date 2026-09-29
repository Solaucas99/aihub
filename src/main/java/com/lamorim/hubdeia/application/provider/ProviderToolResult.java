package com.lamorim.hubdeia.application.provider;

import java.util.Map;

import com.lamorim.hubdeia.domain.model.ProviderSession;

public record ProviderToolResult(
		String content,
		Map<String, Object> structuredOutput,
		ProviderSession providerSession
) {
}
