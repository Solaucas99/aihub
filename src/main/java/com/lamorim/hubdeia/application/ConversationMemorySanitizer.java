package com.lamorim.hubdeia.application;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ConversationMemorySanitizer {

	private static final Pattern VEGA_LITE_BLOCK = Pattern.compile("(?is)```\\s*vega-lite\\s+.*?```");

	public String sanitizeForAgent(String content) {
		if (content == null || content.isBlank()) {
			return "";
		}
		return VEGA_LITE_BLOCK.matcher(content).replaceAll("[grafico vega-lite omitido da memoria]");
	}
}
