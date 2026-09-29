package com.lamorim.hubdeia.application.provider.genie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GenieTableRenderer {

	private static final int MAX_ROWS = 100;

	public String render(Object providerDefinition, String title, GenieQueryResult queryResult) {
		List<TableColumn> columns = columns(providerDefinition, queryResult);
		StringBuilder html = new StringBuilder("<section class=\"genie-table\">");
		if (StringUtils.hasText(title)) {
			html.append("<h3>").append(escape(title)).append("</h3>");
		}
		html.append("<div class=\"table-scroll\"><table><thead><tr>");
		for (TableColumn column : columns) {
			html.append("<th>").append(escape(column.label())).append("</th>");
		}
		html.append("</tr></thead><tbody>");
		for (Map<String, Object> row : queryResult.rows().stream().limit(MAX_ROWS).toList()) {
			html.append("<tr>");
			for (TableColumn column : columns) {
				html.append("<td>").append(escape(String.valueOf(row.getOrDefault(column.field(), "")))).append("</td>");
			}
			html.append("</tr>");
		}
		html.append("</tbody></table></div></section>");
		return html.toString();
	}

	private List<TableColumn> columns(Object providerDefinition, GenieQueryResult queryResult) {
		List<TableColumn> result = new ArrayList<>();
		Document definition = definition(providerDefinition);
		Document renderSpec = definition.get("renderSpec", Document.class);
		Document encodings = renderSpec == null ? null : renderSpec.get("encodings", Document.class);
		List<Document> configured = encodings == null ? List.of() : encodings.getList("columns", Document.class, List.of());
		for (Document column : configured) {
			String field = column.getString("fieldName");
			if (StringUtils.hasText(field)) {
				result.add(new TableColumn(field, column.getString("displayName") == null ? field : column.getString("displayName")));
			}
		}
		if (!result.isEmpty()) return result;
		return queryResult.columns().stream().map(column -> new TableColumn(column.name(), column.name())).toList();
	}

	@SuppressWarnings("unchecked")
	private Document definition(Object value) {
		if (value instanceof String text && StringUtils.hasText(text)) return Document.parse(text);
		if (value instanceof Map<?, ?> map) return new Document((Map<String, Object>) map);
		return new Document();
	}

	private String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#39;");
	}

	private record TableColumn(String field, String label) { }
}
