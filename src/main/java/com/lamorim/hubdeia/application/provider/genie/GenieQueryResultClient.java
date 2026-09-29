package com.lamorim.hubdeia.application.provider.genie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.lamorim.hubdeia.config.HubRestClientFactory;

@Component
public class GenieQueryResultClient {

	private final RestClient restClient;
	private final GenieAuthClient authClient;

	public GenieQueryResultClient(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			GenieAuthClient authClient,
			@Value("${hub.genie.base-url:}") String baseUrl,
			@Value("${hub.genie.request-timeout-ms:60000}") long requestTimeoutMs
	) {
		this.restClient = restClients.create(restClientBuilder, baseUrl, requestTimeoutMs);
		this.authClient = authClient;
	}

	@SuppressWarnings("unchecked")
	public GenieQueryResult fetch(String spaceId, String conversationId, String messageId, String attachmentId) {
		Map<String, Object> response = restClient.get()
				.uri("/api/2.0/genie/spaces/{spaceId}/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentId}/query-result",
						spaceId, conversationId, messageId, attachmentId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.accessToken())
				.retrieve()
				.body(Map.class);

		Map<String, Object> statement = map(response == null ? null : response.get("statement_response"));
		if (statement.isEmpty()) {
			statement = response == null ? Map.of() : response;
		}
		Map<String, Object> manifest = map(statement.get("manifest"));
		Map<String, Object> schema = map(manifest.get("schema"));
		List<GenieQueryColumn> columns = columns((List<Map<String, Object>>) schema.getOrDefault("columns", List.of()));
		Map<String, Object> result = map(statement.get("result"));
		List<Map<String, Object>> rows = rows(columns, (List<?>) result.getOrDefault("data_array", List.of()));
		long rowCount = number(result.get("row_count"), number(manifest.get("total_row_count"), rows.size()));
		String state = String.valueOf(map(statement.get("status")).getOrDefault("state", ""));
		return new GenieQueryResult(columns, rows, rowCount, Boolean.TRUE.equals(manifest.get("truncated")), state);
	}

	private List<GenieQueryColumn> columns(List<Map<String, Object>> rawColumns) {
		List<GenieQueryColumn> columns = new ArrayList<>();
		for (Map<String, Object> column : rawColumns) {
			String name = String.valueOf(column.getOrDefault("name", column.getOrDefault("column_name", column.getOrDefault("label", "column"))));
			String type = String.valueOf(column.getOrDefault("type_name", column.getOrDefault("type_text", column.getOrDefault("type", "string"))));
			columns.add(new GenieQueryColumn(name, type));
		}
		return columns;
	}

	private List<Map<String, Object>> rows(List<GenieQueryColumn> columns, List<?> rawRows) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Object rawRow : rawRows) {
			List<?> values = values(rawRow);
			java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<>();
			for (int index = 0; index < columns.size(); index++) {
				Object value = index < values.size() ? values.get(index) : null;
				row.put(columns.get(index).name(), coerce(value, columns.get(index).type()));
			}
			rows.add(row);
		}
		return rows;
	}

	private List<?> values(Object row) {
		if (row instanceof List<?> values) return values;
		if (row instanceof Map<?, ?> map && map.get("values") instanceof List<?> values) return values;
		return List.of();
	}

	private Object coerce(Object value, String type) {
		if (value == null || !(value instanceof String text)) return value;
		if (type.toUpperCase().matches(".*(INT|LONG|FLOAT|DOUBLE|DECIMAL|NUMBER|NUMERIC).*") && !text.isBlank()) {
			try { return Double.valueOf(text); } catch (NumberFormatException ignored) { return value; }
		}
		return value;
	}

	private long number(Object value, long fallback) {
		if (value instanceof Number number) return number.longValue();
		try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}
}
