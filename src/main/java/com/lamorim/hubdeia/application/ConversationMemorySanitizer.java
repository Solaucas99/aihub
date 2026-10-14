package com.lamorim.hubdeia.application;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.application.content.TaskOutput;

/** Keeps rendered visuals (chart specs, HTML tables) out of what models read from the chat history. */
@Component
public class ConversationMemorySanitizer {

	private static final String OMITTED = "[visual omitido da memoria]";
	private static final Pattern VEGA_LITE_BLOCK = Pattern.compile("(?is)```\\s*vega-lite\\s+.*?```");
	private static final Pattern HTML_VISUAL = Pattern.compile("(?s)" + Pattern.quote(TaskOutput.VISUAL_START) + ".*?" + Pattern.quote(TaskOutput.VISUAL_END));

	public String sanitizeForAgent(String content) {
		if (content == null || content.isBlank()) {
			return "";
		}
		String withoutCharts = VEGA_LITE_BLOCK.matcher(content).replaceAll(OMITTED);
		return HTML_VISUAL.matcher(withoutCharts).replaceAll(OMITTED);
	}
}
