package com.lamorim.hubdeia.application.provider.genie;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;

import com.lamorim.hubdeia.config.HubRestClientFactory;

@Component
public class VisualizationSpecConverter {

	private static final Logger logger = LoggerFactory.getLogger(VisualizationSpecConverter.class);

	private final RestClient restClient;
	private final String apiKey;
	private final String model;
	private final long conversionTimeoutMs;
	private final ExecutorService conversionExecutor = Executors.newVirtualThreadPerTaskExecutor();

	public VisualizationSpecConverter(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			@Value("${hub.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${hub.openai.api-key:}") String apiKey,
			@Value("${hub.genie.viz-conversion-model:gpt-5}") String model,
			@Value("${hub.openai.timeout-ms:60000}") long timeoutMs,
			@Value("${hub.genie.viz-conversion-timeout-ms:15000}") long conversionTimeoutMs
	) {
		this.restClient = restClients.create(restClientBuilder, baseUrl, timeoutMs);
		this.apiKey = apiKey;
		this.model = model;
		this.conversionTimeoutMs = Math.max(1_000, conversionTimeoutMs);
	}

	public String convert(Object providerDefinition, String title, String chartType, GenieQueryResult queryResult) {
		if (!StringUtils.hasText(apiKey)) {
			return fallbackSpec(title, chartType, queryResult);
		}

		Document payload = new Document()
				.append("model", model)
				.append("messages", List.of(
						new Document()
								.append("role", "system")
								.append("content", "Converta uma especificacao de GRAFICO Genie/Helios para Vega-Lite v5. Tabelas Genie sao renderizadas pelo backend como HTML e nunca devem ser convertidas para Vega-Lite. Retorne obrigatoriamente um JSON Vega-Lite valido e nao vazio no campo spec_json, sem markdown. Nunca retorne string vazia, null, tabela, mark text ou texto livre. Use somente os campos recebidos e nao inclua data.values; o backend inserira os dados. Defina mark e encoding com campos existentes. Inclua titulo descritivo, titulo nos eixos quando houver x/y, legenda nomeada quando houver color e tooltip com as metricas relevantes. Para pie/donut use mark arc, theta com a medida quantitativa, color com a categoria e tooltip com categoria e medida. Para line use x temporal/categorico e y quantitativo. Para bar use uma categoria e uma medida quantitativa. Se a definicao Helios estiver incompleta, escolha o grafico simples mais adequado pelos tipos das colunas. A resposta precisa ser renderizavel pelo Vega-Lite sem ajustes adicionais."),
						new Document()
								.append("role", "user")
								.append("content", new Document()
										.append("title", title)
										.append("chart_type", chartType)
										.append("columns", columns(queryResult))
										.append("sample_rows", queryResult.rows().stream().limit(5).toList())
										.append("provider_definition", providerDefinition)
										.toJson())))
				.append("response_format", responseFormat());

		logger.info("Starting Genie visualization conversion. model={}, timeoutMs={}, title={}", model, conversionTimeoutMs, title);
		long startedAt = System.nanoTime();
		CompletableFuture<String> conversion = CompletableFuture.supplyAsync(() -> requestConversion(payload, title, chartType, queryResult), conversionExecutor);
		try {
			String spec = conversion.get(conversionTimeoutMs, TimeUnit.MILLISECONDS);
			logger.info("Genie visualization conversion completed. elapsedMs={}, title={}", elapsedMs(startedAt), title);
			return spec;
		} catch (TimeoutException error) {
			conversion.cancel(true);
			logger.warn("Genie visualization conversion timed out. timeoutMs={}, elapsedMs={}, title={}", conversionTimeoutMs, elapsedMs(startedAt), title);
			return fallbackSpec(title, chartType, queryResult);
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			logger.warn("Genie visualization conversion interrupted. elapsedMs={}, title={}", elapsedMs(startedAt), title, error);
			return fallbackSpec(title, chartType, queryResult);
		} catch (ExecutionException error) {
			Throwable cause = error.getCause() == null ? error : error.getCause();
			logger.warn("Genie visualization conversion failed. elapsedMs={}, title={}", elapsedMs(startedAt), title, cause);
			return fallbackSpec(title, chartType, queryResult);
		}
	}

	private String requestConversion(Document payload, String title, String chartType, GenieQueryResult queryResult) {
		String response = restClient.post()
				.uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload.toJson())
				.retrieve()
				.body(String.class);
		Document body = Document.parse(response);
		List<Document> choices = body.getList("choices", Document.class, List.of());
		if (choices.isEmpty()) return fallbackWithReason(title, chartType, queryResult, "OpenAI nao retornou choices.");
		Document message = choices.getFirst().get("message", Document.class);
		if (message == null) return fallbackWithReason(title, chartType, queryResult, "OpenAI nao retornou message.");

		String content = message.getString("content");
		if (!StringUtils.hasText(content)) {
			String refusal = message.getString("refusal");
			return fallbackWithReason(title, chartType, queryResult, StringUtils.hasText(refusal) ? "OpenAI recusou a conversao." : "OpenAI retornou content vazio.");
		}

		Document responseContent;
		try {
			responseContent = Document.parse(content);
		} catch (RuntimeException error) {
			return fallbackWithReason(title, chartType, queryResult, "OpenAI retornou content fora do JSON esperado.");
		}
		Object serializedSpec = responseContent.get("spec_json");
		if (!(serializedSpec instanceof String value) || !StringUtils.hasText(value)) {
			return fallbackWithReason(title, chartType, queryResult, "OpenAI retornou spec_json vazio.");
		}

		Document spec;
		try {
			spec = Document.parse(value);
		} catch (RuntimeException error) {
			return fallbackWithReason(title, chartType, queryResult, "OpenAI retornou spec_json invalido.");
		}
		if (!isRenderableChart(spec, queryResult)) {
			return fallbackWithReason(title, chartType, queryResult, "OpenAI retornou uma visualizacao nao renderizavel.");
		}
		spec.put("$schema", "https://vega.github.io/schema/vega-lite/v5.json");
		spec.put("data", new Document("values", queryResult.rows().stream().limit(500).toList()));
		return enrichInteractivity(spec, queryResult).toJson();
	}

	private String fallbackWithReason(String title, String chartType, GenieQueryResult queryResult, String reason) {
		logger.info("Using data-driven Vega-Lite fallback. title={}, reason={}", title, reason);
		return fallbackSpec(title, chartType, queryResult);
	}

	private long elapsedMs(long startedAt) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
	}

	private boolean isRenderableChart(Document spec, GenieQueryResult queryResult) {
		String mark = markType(spec.get("mark"));
		Document encoding = spec.get("encoding", Document.class);
		if (!StringUtils.hasText(mark) || "text".equalsIgnoreCase(mark) || encoding == null || encoding.isEmpty()) {
			return false;
		}

		Set<String> fields = queryResult.columns().stream().map(GenieQueryColumn::name).collect(java.util.stream.Collectors.toSet());
		return List.of("x", "y", "theta", "size", "color", "detail", "row", "column").stream()
				.map(encoding::get)
				.anyMatch(channel -> referencesKnownField(channel, fields));
	}

	private String markType(Object mark) {
		if (mark instanceof String value) return value;
		if (mark instanceof Document document) return document.getString("type");
		return "";
	}

	private boolean referencesKnownField(Object channel, Set<String> fields) {
		if (!(channel instanceof Document document)) return false;
		String field = document.getString("field");
		return StringUtils.hasText(field) && fields.contains(field);
	}

	private Document responseFormat() {
		Document schema = new Document()
				.append("type", "object")
				.append("additionalProperties", false)
				.append("required", List.of("spec_json"))
				.append("properties", new Document("spec_json", new Document("type", "string").append("minLength", 2)));
		return new Document()
				.append("type", "json_schema")
				.append("json_schema", new Document()
						.append("name", "vega_lite_conversion")
						.append("strict", true)
						.append("schema", schema));
	}

	@PreDestroy
	void shutdown() {
		conversionExecutor.shutdownNow();
	}

	private String fallbackSpec(String title, String chartType, GenieQueryResult queryResult) {
		String category = categoryField(queryResult);
		String numeric = numericField(queryResult);
		String temporal = temporalField(queryResult);
		Document encoding = new Document();
		String mark = "bar";
		if ("pie".equalsIgnoreCase(chartType) && StringUtils.hasText(category) && StringUtils.hasText(numeric)) {
			mark = "arc";
			encoding.append("theta", quantitative(numeric)).append("color", nominal(category));
		} else if ("line".equalsIgnoreCase(chartType) && StringUtils.hasText(numeric)) {
			mark = "line";
			encoding.append("x", StringUtils.hasText(temporal) ? temporal(temporal) : nominal(category))
					.append("y", quantitative(numeric));
		} else {
			encoding.append("x", quantitative(numeric)).append("y", nominal(category));
		}
		Document spec = new Document()
				.append("$schema", "https://vega.github.io/schema/vega-lite/v5.json")
				.append("title", title == null ? "Visualizacao" : title)
				.append("mark", mark)
				.append("data", new Document("values", queryResult.rows().stream().limit(500).toList()))
				.append("encoding", encoding);
		return enrichInteractivity(spec, queryResult).toJson();
	}

	private Document enrichInteractivity(Document spec, GenieQueryResult queryResult) {
		Document encoding = spec.get("encoding", Document.class);
		if (encoding == null) return spec;

		axisTitle(encoding, "x");
		axisTitle(encoding, "y");
		legendTitle(encoding);
		if (!encoding.containsKey("tooltip")) {
			encoding.put("tooltip", queryResult.columns().stream()
					.map(column -> new Document("field", column.name()).append("type", vegaType(column)).append("title", label(column.name())))
					.toList());
		}
		return spec;
	}

	private void axisTitle(Document encoding, String channelName) {
		Document channel = encoding.get(channelName, Document.class);
		if (channel == null) return;
		Document axis = channel.get("axis", Document.class);
		if (axis == null) axis = new Document();
		axis.putIfAbsent("title", label(channel.getString("field")));
		channel.put("axis", axis);
	}

	private void legendTitle(Document encoding) {
		Document color = encoding.get("color", Document.class);
		if (color == null) return;
		Document legend = color.get("legend", Document.class);
		if (legend == null) legend = new Document();
		legend.putIfAbsent("title", label(color.getString("field")));
		color.put("legend", legend);
	}

	private String vegaType(GenieQueryColumn column) {
		return isNumeric(column) ? "quantitative" : isTemporal(column) ? "temporal" : "nominal";
	}

	private String label(String field) {
		return field == null ? "" : field.replace('_', ' ');
	}

	private String categoryField(GenieQueryResult result) {
		return result.columns().stream().filter(column -> !isNumeric(column) && !isTemporal(column)).map(GenieQueryColumn::name).findFirst()
				.orElse(result.columns().isEmpty() ? "label" : result.columns().getFirst().name());
	}

	private String numericField(GenieQueryResult result) {
		return result.columns().stream().filter(this::isNumeric).map(GenieQueryColumn::name).findFirst()
				.orElse(result.columns().size() > 1 ? result.columns().get(1).name() : "value");
	}

	private String temporalField(GenieQueryResult result) {
		return result.columns().stream().filter(this::isTemporal).map(GenieQueryColumn::name).findFirst().orElse("");
	}

	private boolean isNumeric(GenieQueryColumn column) { return column.type().toUpperCase().matches(".*(INT|LONG|FLOAT|DOUBLE|DECIMAL|NUMBER|NUMERIC).*"); }
	private boolean isTemporal(GenieQueryColumn column) { return column.type().toUpperCase().matches(".*(DATE|TIME|TIMESTAMP).*"); }
	private Document quantitative(String field) { return new Document("field", field).append("type", "quantitative"); }
	private Document nominal(String field) { return new Document("field", field).append("type", "nominal"); }
	private Document temporal(String field) { return new Document("field", field).append("type", "temporal"); }
	private List<Map<String, String>> columns(GenieQueryResult result) { return result.columns().stream().map(column -> Map.of("name", column.name(), "type", column.type())).toList(); }
}
