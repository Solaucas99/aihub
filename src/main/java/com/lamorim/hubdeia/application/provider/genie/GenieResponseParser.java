package com.lamorim.hubdeia.application.provider.genie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.application.provider.ProgressReporter;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;

@Component
public class GenieResponseParser {

	private static final Logger logger = LoggerFactory.getLogger(GenieResponseParser.class);
	private static final Pattern EMBED_ID = Pattern.compile("(?:!\\[Visualization\\]\\(#|:embedVisualization\\[)(viz_[^\\])]+)");
	private static final Pattern SQL_CITATION = Pattern.compile("\\s*:sqlCitation\\[[^]]+]", Pattern.CASE_INSENSITIVE);

	private final GenieQueryResultClient queryResults;
	private final GenieTableRenderer tableRenderer;
	private final VisualizationSpecConverter visualizationSpecConverter;

	public GenieResponseParser(GenieQueryResultClient queryResults, GenieTableRenderer tableRenderer, VisualizationSpecConverter visualizationSpecConverter) {
		this.queryResults = queryResults;
		this.tableRenderer = tableRenderer;
		this.visualizationSpecConverter = visualizationSpecConverter;
	}

	public GenieParsedResponse parse(GenieMessageResult result, ProgressReporter reporter) {
		Map<String, Object> raw = message(result.rawResponse());
		logger.debug("Genie completed payload. conversationId={}, messageId={}, payload={}", result.conversationId(), result.messageId(), raw);
		List<Map<String, Object>> attachments = attachments(raw);
		Map<String, Map<String, Object>> queries = queries(attachments);
		Map<String, Map<String, Object>> visualizations = visualizations(attachments);
		Map<String, Object> report = report(attachments);
		List<Map<String, Object>> artifacts = new ArrayList<>();
		String markdown = markdown(raw, report, visualizations, queries, artifacts, result, reporter);

		Map<String, Object> structured = Map.of(
				"content", markdown,
				"visualizations", artifacts,
				"provider", Map.of(
						"provider_id", "GENIE",
						"conversation_id", result.conversationId(),
						"message_id", result.messageId())
		);
		return new GenieParsedResponse(structured, visuals(artifacts));
	}

	/** Genie's ready visualizations as neutral visuals: tables arrive as HTML, charts as Vega-Lite. */
	private List<Visual> visuals(List<Map<String, Object>> rendered) {
		List<Visual> visuals = new ArrayList<>();
		for (Map<String, Object> item : rendered) {
			if (!"READY".equals(item.get("status"))) {
				continue;
			}
			String id = String.valueOf(item.get("id"));
			String title = String.valueOf(item.getOrDefault("title", ""));
			if (item.get("html") instanceof String html && !html.isBlank()) {
				visuals.add(new Visual(id, title, VisualFormat.HTML, html));
			} else if (item.get("spec") instanceof String spec && !spec.isBlank()) {
				visuals.add(new Visual(id, title, VisualFormat.VEGA_LITE, spec));
			}
		}
		return visuals;
	}

	private String markdown(
			Map<String, Object> raw,
			Map<String, Object> report,
			Map<String, Map<String, Object>> visualizations,
			Map<String, Map<String, Object>> queries,
			List<Map<String, Object>> artifacts,
			GenieMessageResult result,
			ProgressReporter reporter
	) {
		List<Map<String, Object>> chunks = list(report.get("content_chunks"));
		if (chunks.isEmpty()) {
			return clean(String.valueOf(report.getOrDefault("report_summary", raw.getOrDefault("content", ""))));
		}

		StringBuilder markdown = new StringBuilder();
		for (Map<String, Object> chunk : chunks) {
			String text = String.valueOf(chunk.getOrDefault("text", ""));
			String embedId = embedId(chunk, text);
			if (embedId == null) {
				append(markdown, clean(text));
				continue;
			}

			Map<String, Object> visualization = visualizations.get(embedId);
			if (visualization == null) {
				artifacts.add(unavailableVisualization(embedId));
				continue;
			}
			Map<String, Object> artifact = renderVisualization(visualization, queries, raw, result, reporter);
			artifacts.add(artifact);
			if ("READY".equals(artifact.get("status"))) {
				append(markdown, visualizationMarker(String.valueOf(artifact.get("id"))));
			}
		}
		return clean(markdown.toString());
	}

	private Map<String, Object> renderVisualization(
			Map<String, Object> visualization,
			Map<String, Map<String, Object>> queries,
			Map<String, Object> raw,
			GenieMessageResult result,
			ProgressReporter reporter
	) {
		String title = String.valueOf(visualization.getOrDefault("title", "Visualizacao"));
		String queryAttachmentId = String.valueOf(visualization.getOrDefault("query_attachment_id", ""));
		Map<String, Object> query = queries.get(queryAttachmentId);
		Map<String, Object> artifact = artifact(visualization, "PENDING");
		if (query == null) {
			artifact.put("status", "DATA_UNAVAILABLE");
			artifact.put("error", "Query associada nao encontrada.");
			return artifact;
		}

		try {
			String spaceId = String.valueOf(raw.getOrDefault("space_id", raw.getOrDefault("data_room_id", "")));
			boolean table = "table".equalsIgnoreCase(String.valueOf(visualization.getOrDefault("type", "")));
			reporter.report("GENIE_QUERY_RESULT", "Preparando dados da visualizacao: " + title);
			GenieQueryResult queryResult = queryResults.fetch(spaceId, result.conversationId(), result.messageId(), queryAttachmentId);
			artifact.put("status", "READY");
			artifact.put("render_mode", table ? "HTML" : "VEGA_LITE");
			if (table) {
				artifact.put("html", tableRenderer.render(visualization.get("definition"), title, queryResult));
				reporter.report("GENIE_TABLE_READY", "Tabela pronta: " + title);
			} else {
				reporter.report("GENIE_VIZ_CONVERTING", "Convertendo grafico: " + title);
				artifact.put("spec", visualizationSpecConverter.convert(
						visualization.get("definition"), title,
						String.valueOf(visualization.getOrDefault("type", "bar")), queryResult));
				reporter.report("GENIE_VIZ_READY", "Grafico pronto: " + title);
			}
			artifact.put("query_result", Map.of(
					"row_count", queryResult.rowCount(),
					"truncated", queryResult.truncated(),
					"state", queryResult.state(),
					"columns", columns(queryResult)));
			return artifact;
		} catch (Exception error) {
			logger.warn("Could not prepare Genie visualization. title={}, queryAttachmentId={}", title, queryAttachmentId, error);
			artifact.put("status", "FAILED");
			artifact.put("error", error.getMessage());
			reporter.report("GENIE_VIZ_FAILED", "Nao foi possivel preparar o grafico: " + title);
			return artifact;
		}
	}

	private Map<String, Object> artifact(Map<String, Object> visualization, String status) {
		Map<String, Object> artifact = new LinkedHashMap<>();
		artifact.put("id", visualization.getOrDefault("asset_id", visualization.getOrDefault("id", "")));
		artifact.put("type", "visualization");
		artifact.put("title", visualization.getOrDefault("title", "Visualizacao"));
		artifact.put("visualization_type", visualization.getOrDefault("type", "unknown"));
		artifact.put("source_query_attachment_id", visualization.getOrDefault("query_attachment_id", ""));
		artifact.put("status", status);
		return artifact;
	}

	private Map<String, Object> unavailableVisualization(String embedId) {
		Map<String, Object> artifact = new LinkedHashMap<>();
		artifact.put("id", embedId);
		artifact.put("type", "visualization");
		artifact.put("status", "DATA_UNAVAILABLE");
		artifact.put("error", "Visualizacao referenciada pela resposta nao encontrada.");
		return artifact;
	}

	private Map<String, Map<String, Object>> queries(List<Map<String, Object>> attachments) {
		Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
		for (Map<String, Object> attachment : attachments) {
			Map<String, Object> query = map(attachment.get("query"));
			if (!query.isEmpty()) indexed.put(String.valueOf(query.get("id")), query);
		}
		return indexed;
	}

	private Map<String, Map<String, Object>> visualizations(List<Map<String, Object>> attachments) {
		Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
		for (Map<String, Object> attachment : attachments) {
			Map<String, Object> visualization = map(attachment.get("viz"));
			if (!visualization.isEmpty()) indexed.put(String.valueOf(visualization.get("asset_id")), visualization);
		}
		return indexed;
	}

	private Map<String, Object> report(List<Map<String, Object>> attachments) {
		Map<String, Object> result = Map.of();
		for (Map<String, Object> attachment : attachments) {
			Map<String, Object> report = map(attachment.get("deep_research_report"));
			if (!report.isEmpty()) result = report;
		}
		return result;
	}

	private String embedId(Map<String, Object> chunk, String text) {
		Map<String, Object> metadata = map(chunk.get("metadata"));
		Object value = metadata.get("embed_id");
		if (value != null) return String.valueOf(value);
		Matcher matcher = EMBED_ID.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}

	private void append(StringBuilder target, String value) {
		if (value == null || value.isBlank()) return;
		if (!target.isEmpty()) target.append("\n\n");
		target.append(value.trim());
	}

	private String visualizationMarker(String id) {
		return "[[visualization:" + id + "]]";
	}

	private String clean(String markdown) {
		return SQL_CITATION.matcher(markdown == null ? "" : markdown)
				.replaceAll("")
				.replaceAll("(?m)^\\s*:embedVisualization\\[[^]]+]\\s*$", "")
				.replaceAll("\\n{3,}", "\n\n")
				.trim();
	}

	private Map<String, Object> message(Map<String, Object> raw) {
		if (raw.containsKey("attachments")) return raw;
		List<Map<String, Object>> messages = list(raw.get("messages"));
		return messages.isEmpty() ? raw : messages.getLast();
	}

	private List<Map<String, Object>> attachments(Map<String, Object> raw) { return list(raw.get("attachments")); }

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> list(Object value) {
		if (!(value instanceof List<?> list)) return List.of();
		return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	private List<Map<String, String>> columns(GenieQueryResult result) {
		return result.columns().stream().map(column -> Map.of("name", column.name(), "type", column.type())).toList();
	}
}
