package com.lamorim.hubdeia.application.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;

/**
 * What a subagent produced: text that may reference visuals with {@code [[visualization:id]]}
 * markers, plus the visuals themselves. Text and visuals are stored apart and only put together
 * when something needs them: {@link #markdown()} for people, {@link #plainText()} for models.
 */
public record TaskOutput(String content, List<Visual> visuals) {

	public static final String VISUAL_START = "<!--visual:start-->";
	public static final String VISUAL_END = "<!--visual:end-->";

	private static final Pattern MARKER = Pattern.compile("\\[\\[visualization:([^]]+)]]");

	public TaskOutput {
		content = content == null ? "" : content;
		visuals = visuals == null ? List.of() : visuals;
	}

	public static TaskOutput from(Map<String, Object> output) {
		if (output == null) {
			return new TaskOutput("", List.of());
		}
		List<Visual> visuals = new ArrayList<>();
		if (output.get("visuals") instanceof List<?> raw) {
			for (Object item : raw) {
				if (item instanceof Map<?, ?> map) {
					toVisual(map).ifPresent(visuals::add);
				}
			}
		}
		Object content = output.get("content");
		return new TaskOutput(content == null ? "" : content.toString(), visuals);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("content", content);
		map.put("visuals", visuals.stream().map(visual -> {
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("id", visual.id());
			value.put("title", visual.title());
			value.put("format", visual.format().name());
			value.put("payload", visual.payload());
			return value;
		}).toList());
		return map;
	}

	/** Markdown for display: every marker becomes its visual, and visuals the text left out go at the end. */
	public String markdown() {
		Map<String, Visual> byId = byId();
		Set<String> used = new HashSet<>();
		StringBuilder result = new StringBuilder();
		Matcher markers = MARKER.matcher(content);
		while (markers.find()) {
			Visual visual = used.add(markers.group(1)) ? byId.get(markers.group(1)) : null;
			markers.appendReplacement(result, Matcher.quoteReplacement(visual == null ? "" : render(visual)));
		}
		markers.appendTail(result);
		byId.forEach((id, visual) -> {
			if (used.add(id)) {
				if (!result.isEmpty()) {
					result.append("\n\n");
				}
				result.append(render(visual));
			}
		});
		return result.toString().trim();
	}

	/** Text for a model: markers become short placeholders, so no HTML or chart specs reach a prompt. */
	public String plainText() {
		Map<String, Visual> byId = byId();
		Set<String> used = new HashSet<>();
		StringBuilder result = new StringBuilder();
		Matcher markers = MARKER.matcher(content);
		while (markers.find()) {
			Visual visual = used.add(markers.group(1)) ? byId.get(markers.group(1)) : null;
			markers.appendReplacement(result, Matcher.quoteReplacement(visual == null ? "" : placeholder(visual)));
		}
		markers.appendTail(result);
		return result.toString().replaceAll("\\n{3,}", "\n\n").trim();
	}

	private Map<String, Visual> byId() {
		Map<String, Visual> byId = new LinkedHashMap<>();
		visuals.forEach(visual -> byId.put(visual.id(), visual));
		return byId;
	}

	private static String render(Visual visual) {
		return switch (visual.format()) {
			case HTML -> VISUAL_START + "\n\n" + visual.payload() + "\n\n" + VISUAL_END;
			case VEGA_LITE -> "```vega-lite\n" + visual.payload() + "\n```";
		};
	}

	private static String placeholder(Visual visual) {
		return StringUtils.hasText(visual.title()) ? "[visual: " + visual.title() + "]" : "[visual]";
	}

	private static Optional<Visual> toVisual(Map<?, ?> map) {
		Object id = map.get("id");
		Object format = map.get("format");
		Object payload = map.get("payload");
		if (id == null || format == null || payload == null || !StringUtils.hasText(payload.toString())) {
			return Optional.empty();
		}
		try {
			Object title = map.get("title");
			return Optional.of(new Visual(id.toString(), title == null ? "" : title.toString(), VisualFormat.valueOf(format.toString()), payload.toString()));
		} catch (IllegalArgumentException unknownFormat) {
			return Optional.empty();
		}
	}
}
