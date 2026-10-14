package com.lamorim.hubdeia.application.provider;

import java.util.List;
import java.util.Map;

import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.Visual;

/**
 * What a provider tool hands back to the core.
 *
 * @param content          text the model reads; it may reference {@code visuals} with {@code [[visualization:id]]} markers
 * @param structuredOutput provider-specific details, kept only for audit; the core never interprets it
 * @param providerSession  provider conversation to reuse next time, if the provider has one
 * @param visuals          neutral tables/charts the tool built; empty for providers without visual output
 */
public record ProviderToolResult(
		String content,
		Map<String, Object> structuredOutput,
		ProviderSession providerSession,
		List<Visual> visuals
) {

	public ProviderToolResult {
		visuals = visuals == null ? List.of() : visuals;
	}
}
