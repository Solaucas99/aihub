# 08 — Execução do subagente, prompts, cliente OpenAI, conteúdo neutro (TaskOutput/Visual), artefatos e contrato de tools

> Spec do projeto **Hub de IA** (pacote base `com.lamorim.hubdeia`). Leia antes `00-LEIA-ME.md`.

## Objetivo

Especificar o que acontece **dentro de uma task**: como o prompt do subagente é montado, como o laço modelo↔tools roda, como o resultado vira um conteúdo neutro (`TaskOutput` = texto + `Visual`s), como os artefatos são publicados/consumidos, e qual é o **contrato** que qualquer *provider* precisa cumprir para ser plugado sem alterar o núcleo.

## Arquivos cobertos

`application/orchestration/SubagentTaskExecutor.java`, `SubagentPromptBuilder.java`, `AgentSkillAccessPolicy.java` (também descrito em 04); `application/llm/OpenAiChatClient.java`; `application/content/TaskOutput.java`; `application/ArtifactService.java`, `ArtifactView.java`; `application/provider/ProviderTool.java`, `ProviderToolContext.java`, `ProviderToolResult.java`, `ProviderToolRegistry.java`, `ProgressReporter.java`.

## Depende de

`03` (records), `04` (política de acesso e catálogo), `06` (`WorkContextService`, `Text`), `07` (`TaskExecutionService`, `AgentRunService`, `TaskExecutionResult`), `09` (provider Genie, a implementação concreta de `ProviderTool`).

## 1. `OpenAiChatClient` — ponto único de acesso ao modelo

**Arquivo `src/main/java/com/lamorim/hubdeia/application/llm/OpenAiChatClient.java`** (literal):

```java
package com.lamorim.hubdeia.application.llm;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.lamorim.hubdeia.config.HubRestClientFactory;

/** Single entry point for OpenAI chat completions, shared by every agent. */
@Component
public class OpenAiChatClient {

	private final RestClient restClient;
	private final String apiKey;

	public OpenAiChatClient(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			@Value("${hub.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${hub.openai.api-key:}") String apiKey,
			@Value("${hub.openai.timeout-ms:60000}") long timeoutMs
	) {
		this.restClient = restClients.create(restClientBuilder, baseUrl, timeoutMs);
		this.apiKey = apiKey;
	}

	public boolean isConfigured() {
		return StringUtils.hasText(apiKey);
	}

	/** Posts a chat completion request and returns the first choice's message. */
	public Document complete(Document request) {
		if (!isConfigured()) {
			throw new IllegalStateException("OPENAI_API_KEY nao configurada.");
		}
		String response = restClient.post().uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request.toJson())
				.retrieve()
				.body(String.class);
		return Document.parse(response).getList("choices", Document.class).getFirst().get("message", Document.class);
	}

	/** Same as {@link #complete(Document)} for responses constrained by a JSON schema. */
	public Document completeJson(Document request) {
		return Document.parse(content(complete(request)));
	}

	public static String content(Document message) {
		Object value = message.get("content");
		return value == null ? "" : value.toString();
	}
}
```

- Cria o `RestClient` com `HubRestClientFactory.create(builder, hub.openai.base-url, hub.openai.timeout-ms)`; guarda `hub.openai.api-key`.
- `isConfigured()` é verdadeiro se a chave tem texto.
- `complete(request)`: sem chave → `IllegalStateException("OPENAI_API_KEY nao configurada.")`; senão `POST /chat/completions` com `Authorization: Bearer <chave>`, `Content-Type: application/json`, corpo `request.toJson()`; lê a resposta como `String`, faz `Document.parse`, pega `choices[0].message` e devolve esse `Document`.
- `completeJson(request)`: `complete` + `Document.parse(content(message))` (para respostas com `json_schema`).
- `content(message)` (estático): `message.get("content")` como texto, `""` se nulo.
- Todos os agentes de LLM (planejador, validador, comunicador, subagente, conversão de gráficos do Genie) usam **este** cliente; ninguém mais monta chamadas à OpenAI.

## 2. Contrato de tools (`application/provider`)

**Arquivo `src/main/java/com/lamorim/hubdeia/application/provider/ProviderTool.java`** (literal):

```java
package com.lamorim.hubdeia.application.provider;

import org.bson.Document;

public interface ProviderTool {

	String name();

	Document schema();

	ProviderToolResult execute(ProviderToolContext context, Document arguments);
}
```

**Arquivo `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolContext.java`** (literal):

```java
package com.lamorim.hubdeia.application.provider;

import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.SubagentProvider;

public record ProviderToolContext(
		String conversationId,
	String agentRunId,
		String taskId,
		String subagentId,
		SubagentProvider subagentProvider,
		ProviderSession existingSession,
		ProgressReporter progressReporter
) {
}
```

**Arquivo `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolResult.java`** (literal):

```java
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
```

**Arquivo `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolRegistry.java`** (literal):

```java
package com.lamorim.hubdeia.application.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ProviderToolRegistry {

	private final Map<String, ProviderTool> tools;

	public ProviderToolRegistry(List<ProviderTool> tools) {
		this.tools = tools.stream().collect(Collectors.toMap(ProviderTool::name, Function.identity()));
	}

	public ProviderTool get(String name) {
		ProviderTool tool = tools.get(name);
		if (tool == null) {
			throw new IllegalArgumentException("Provider tool not registered: " + name);
		}
		return tool;
	}
}
```

**Arquivo `src/main/java/com/lamorim/hubdeia/application/provider/ProgressReporter.java`** (literal):

```java
package com.lamorim.hubdeia.application.provider;

public interface ProgressReporter {

	void report(String executionStatus, String message);
}
```

- `ProviderTool.name()` é o **nome da função** exposto ao modelo **e** a chave do `ProviderToolRegistry`; precisa ser igual a `Tool.functionName` do catálogo (`tool-enviar-mensagem-genie` na semente). `schema()` devolve o JSON de função no formato de *tools* da OpenAI; `execute(context, arguments)` roda a tool.
- `ProviderToolContext`: `conversationId`, `agentRunId`, `taskId`, `subagentId`, `subagentProvider` (a configuração do provider **para este subagente**, com `config`), `existingSession` (sessão do provider a reaproveitar, pode ser `null`) e `progressReporter`.
- `ProviderToolResult`: `content` (texto lido pelo modelo, com marcadores `[[visualization:id]]`), `structuredOutput` (detalhes do provider, **só para auditoria**, o núcleo nunca o interpreta), `providerSession` (para reuso) e `visuals` (lista de `Visual`, vazia se o provider não tem visual; `null` vira lista vazia).
- `ProviderToolRegistry`: recebe **todas** as implementações `ProviderTool` (injeção de lista) e as indexa por `name()`; `get(name)` lança `IllegalArgumentException("Provider tool not registered: <name>")` se não existir.
- **Como plugar um provider novo (Salesforce, HubSpot…)**: criar uma classe `@Component` que implementa `ProviderTool` (nome da função igual ao do catálogo), devolver `ProviderToolResult` com o texto e, se quiser gráficos/tabelas, `Visual`s nos formatos `HTML` ou `VEGA_LITE`; cadastrar `Provider`, `Tool`, `Skill` e habilitar o provider no `Subagent.providers`. Nenhuma outra classe muda (planejador, validador, executor, artefatos e frontend permanecem iguais).

## 3. Conteúdo neutro: `TaskOutput`

**Arquivo `src/main/java/com/lamorim/hubdeia/application/content/TaskOutput.java`** (literal):

````java
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
````

Contrato:

- O resultado de uma task é **texto + visuais**, guardados **separados**. A saída da task é o `Map` `{"content": <texto com marcadores>, "visuals": [ {id, title, format, payload}, ... ]}` (`toMap()`), e `from(Map)` o reconstrói (ignora visuais sem `id`/`format`/`payload` ou com `format` desconhecido; `null` vira saída vazia).
- **`markdown()`** — para pessoas (chat e painel de artefatos): cada marcador `[[visualization:<id>]]` é trocado pelo visual de mesmo `id` **uma única vez** (repetições somem; id sem visual some); visuais que o texto não referenciou são **acrescentados no fim**, separados por linha em branco; `HTML` vira `VISUAL_START` + `\n\n` + payload + `\n\n` + `VISUAL_END` (os delimitadores são comentários HTML `<!--visual:start-->` / `<!--visual:end-->`); `VEGA_LITE` vira um bloco ```` ```vega-lite ````…```` ``` ````.
- **`plainText()`** — para modelos (prompt do subagente consumidor, memória, planejador, validador): cada marcador vira `[visual: <título>]` (ou `[visual]` se sem título), só na primeira ocorrência; visuais não referenciados **não** aparecem; três ou mais quebras de linha consecutivas viram duas; nenhum HTML/spec de gráfico chega ao modelo.
- O núcleo **não conhece** o formato do Genie nem de nenhum provider; só os dois formatos de exibição (`HTML`, `VEGA_LITE`).

## 4. `SubagentPromptBuilder` — o prompt do subagente

**Arquivo `src/main/java/com/lamorim/hubdeia/application/orchestration/SubagentPromptBuilder.java`** (literal):

```java
package com.lamorim.hubdeia.application.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.TaskExecutionService;
import com.lamorim.hubdeia.application.Text;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.WorkContext;

/** Assembles the system prompt, individual memory and consumed artifacts handed to a subagent. */
@Component
public class SubagentPromptBuilder {

	private static final int MEMORY_CHARS = 1200;
	private static final int ARTIFACT_CHARS = 6000;

	private final AgentSkillAccessPolicy accessPolicy;
	private final AgentRunService runs;
	private final WorkContextService contexts;
	private final TaskExecutionService executions;
	private final ArtifactService artifacts;

	public SubagentPromptBuilder(AgentSkillAccessPolicy accessPolicy, AgentRunService runs, WorkContextService contexts,
			TaskExecutionService executions, ArtifactService artifacts) {
		this.accessPolicy = accessPolicy;
		this.runs = runs;
		this.contexts = contexts;
		this.executions = executions;
		this.artifacts = artifacts;
	}

	public record SubagentPrompt(List<Document> messages, List<String> consumedArtifactIds) {
	}

	public SubagentPrompt build(String conversationId, String runId, Subagent subagent, Task task) {
		List<Artifact> consumed = artifacts.consumedBy(conversationId, subagent);
		List<Document> messages = new ArrayList<>();
		messages.add(system(instructions(runId, subagent, consumed)));
		String memory = memory(conversationId, subagent.id());
		if (StringUtils.hasText(memory)) {
			messages.add(system("Memoria individual do subagente:\n" + memory));
		}
		messages.add(new Document("role", "user").append("content", "Objetivo da tarefa: %s\nInput: %s".formatted(task.description(), task.input())));
		return new SubagentPrompt(messages, consumed.stream().map(Artifact::id).toList());
	}

	private String instructions(String runId, Subagent subagent, List<Artifact> consumed) {
		List<String> sections = new ArrayList<>();
		sections.add(subagent.systemPrompt());
		contextInstructions(runId).ifPresent(sections::add);
		List<Skill> skills = accessPolicy.skillsFor(subagent);
		if (!skills.isEmpty()) {
			sections.add("Skills permitidas:\n" + String.join("\n", skills.stream().map(skill -> "- " + skill.name() + ": " + skill.instructions()).toList()));
		}
		if (!consumed.isEmpty()) {
			sections.add("Resultados anteriores desta conversa que voce pode usar como base:\n"
					+ String.join("\n\n", consumed.stream().map(this::artifactSection).toList()));
		}
		sections.add("Use somente as tools disponibilizadas para voce. Nao alegue consultas que nao realizou.");
		sections.add("Quando uma tool retornar marcadores no formato [[visualization:id]], preserve todos exatamente uma vez na resposta final. Posicione cada marcador logo apos o paragrafo que explica seus dados ou insight. Nao altere o texto do marcador, nao o exponha ao usuario e nao escreva JSON ou HTML para representa-lo.");
		return String.join("\n\n", sections);
	}

	private Optional<String> contextInstructions(String runId) {
		return contexts.find(runs.get(runId).conversationWorkContextId())
				.flatMap(context -> contexts.definition(context.workContextId()))
				.map(WorkContext::instructions)
				.filter(StringUtils::hasText);
	}

	private String artifactSection(Artifact artifact) {
		return "[%s v%d] %s\n%s".formatted(artifact.type(), artifact.version(), artifact.title(), Text.truncate(new TaskOutput(artifact.content(), artifact.visuals()).plainText(), ARTIFACT_CHARS));
	}

	private String memory(String conversationId, String subagentId) {
		return String.join("\n", executions.recentMemory(conversationId, subagentId).stream().map(this::memoryLine).toList());
	}

	private String memoryLine(SubagentExecution execution) {
		String input = execution.input() == null ? "{}" : new Document(execution.input()).toJson();
		String output = TaskOutput.from(execution.output()).plainText();
		return "- task_id=%s input=%s output=%s".formatted(execution.taskId(), Text.truncate(input, MEMORY_CHARS), Text.truncate(output, MEMORY_CHARS));
	}

	private static Document system(String content) {
		return new Document("role", "system").append("content", content);
	}
}
```

Ordem exata das mensagens devolvidas em `SubagentPrompt.messages`:

1. **`system` de instruções**, com as seções unidas por `\n\n`, nesta ordem: (a) `subagent.systemPrompt()`; (b) as `instructions` do **work context da run** (instância → catálogo), só se existirem; (c) `"Skills permitidas:\n- <nome da skill>: <instructions>"` para cada skill habilitada do subagente (omitida se não há skills); (d) se há artefatos consumidos: `"Resultados anteriores desta conversa que voce pode usar como base:\n"` + as seções `"[<TIPO> v<versão>] <título>\n<texto puro truncado a 6000>"` separadas por linha em branco; (e) `"Use somente as tools disponibilizadas para voce. Nao alegue consultas que nao realizou."`; (f) a instrução dos marcadores `[[visualization:id]]` (texto literal no código acima).
2. **`system` de memória** `"Memoria individual do subagente:\n" + linhas`, **somente se** houver memória (linha: `- task_id=<id> input=<JSON do input, truncado a 1200> output=<texto puro, truncado a 1200>`), vinda de `TaskExecutionService.recentMemory` (5 últimas tasks do subagente na conversa, ordem cronológica).
3. **`user`**: `"Objetivo da tarefa: <description>\nInput: <task.input()>"` (o `toString` do mapa Java do input).

`SubagentPrompt.consumedArtifactIds` lista os ids dos artefatos injetados (vão para a linhagem `derived_from`).

## 5. `SubagentTaskExecutor` — o laço do subagente

**Arquivo `src/main/java/com/lamorim/hubdeia/application/orchestration/SubagentTaskExecutor.java`** (literal):

```java
package com.lamorim.hubdeia.application.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.lamorim.hubdeia.application.TaskExecutionService;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.llm.OpenAiChatClient;
import com.lamorim.hubdeia.application.orchestration.AgentSkillAccessPolicy.AllowedTool;
import com.lamorim.hubdeia.application.orchestration.SubagentPromptBuilder.SubagentPrompt;
import com.lamorim.hubdeia.application.provider.ProgressReporter;
import com.lamorim.hubdeia.application.provider.ProviderTool;
import com.lamorim.hubdeia.application.provider.ProviderToolContext;
import com.lamorim.hubdeia.application.provider.ProviderToolRegistry;
import com.lamorim.hubdeia.application.provider.ProviderToolResult;
import com.lamorim.hubdeia.domain.model.ExecutionMessage;
import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.ToolCall;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

/** Runs one subagent attempt: the model/tool loop, restricted to the tools its skills grant. */
@Component
public class SubagentTaskExecutor {

	private final OpenAiChatClient chat;
	private final String model;
	private final int maxToolRounds;
	private final SubagentRepository subagents;
	private final AgentSkillAccessPolicy accessPolicy;
	private final ProviderToolRegistry providerTools;
	private final SubagentPromptBuilder prompts;
	private final TaskExecutionService executions;

	public SubagentTaskExecutor(
			OpenAiChatClient chat,
			@Value("${hub.openai.subagent-model:gpt-5}") String model,
			@Value("${hub.openai.max-tool-rounds:8}") int maxToolRounds,
			SubagentRepository subagents,
			AgentSkillAccessPolicy accessPolicy,
			ProviderToolRegistry providerTools,
			SubagentPromptBuilder prompts,
			TaskExecutionService executions
	) {
		this.chat = chat;
		this.model = model;
		this.maxToolRounds = Math.max(1, maxToolRounds);
		this.subagents = subagents;
		this.accessPolicy = accessPolicy;
		this.providerTools = providerTools;
		this.prompts = prompts;
		this.executions = executions;
	}

	public TaskExecutionResult execute(String conversationId, String runId, Task task, ProgressReporter reporter) {
		Subagent subagent = subagents.findById(task.subagentSelected())
				.orElseThrow(() -> new IllegalStateException("Subagente nao encontrado: " + task.subagentSelected()));
		Map<String, AllowedTool> allowedTools = accessPolicy.toolsFor(subagent);
		List<Document> toolSchemas = allowedTools.keySet().stream().map(providerTools::get).map(ProviderTool::schema).toList();
		SubagentPrompt prompt = prompts.build(conversationId, runId, subagent, task);
		Attempt attempt = new Attempt(conversationId, runId, task, subagent, reporter, prompt.messages());

		String answer = "";
		String lastToolText = "";
		for (int round = 0; round < maxToolRounds; round++) {
			Document assistant = chat.complete(request(attempt.messages, toolSchemas));
			attempt.messages.add(assistant);
			String text = OpenAiChatClient.content(assistant);
			attempt.persisted.add(new ExecutionMessage(UUID.randomUUID().toString(), "assistant", text, Instant.now()));
			List<Document> toolCalls = assistant.getList("tool_calls", Document.class);
			if (toolCalls == null || toolCalls.isEmpty()) {
				answer = text;
				break;
			}
			for (Document call : toolCalls) {
				lastToolText = callTool(attempt, allowedTools, call);
			}
		}
		reporter.report("AWAITING_VALIDATION", "Output recebido, aguardando validacao");
		TaskOutput output = new TaskOutput(StringUtils.hasText(answer) ? answer : lastToolText, List.copyOf(attempt.visuals.values()));
		return new TaskExecutionResult(task.id(), subagent.id(), output.toMap(), attempt.persisted, List.copyOf(attempt.sessions.values()),
				attempt.calls, prompt.consumedArtifactIds());
	}

	/** Runs one tool call and returns the text the tool answered with. */
	private String callTool(Attempt attempt, Map<String, AllowedTool> allowedTools, Document call) {
		Document function = call.get("function", Document.class);
		String functionName = function.getString("name");
		AllowedTool allowed = allowedTools.get(functionName);
		if (allowed == null) {
			throw new IllegalStateException("Tool nao autorizada para o subagente: " + functionName);
		}
		String providerId = allowed.provider().providerId();
		Document arguments = Document.parse(function.getString("arguments"));
		Instant startedAt = Instant.now();
		attempt.reporter.report("TOOL_CALLING", "Consultando " + allowed.tool().name());
		ProviderToolResult result = providerTools.get(functionName).execute(new ProviderToolContext(attempt.conversationId, attempt.runId,
				attempt.task.id(), attempt.subagent.id(), allowed.provider(), sessionFor(attempt, providerId), attempt.reporter), arguments);
		if (result.providerSession() != null) {
			attempt.sessions.put(providerId, result.providerSession());
		}
		attempt.messages.add(new Document("role", "tool").append("tool_call_id", call.getString("id")).append("content", result.content()));
		attempt.persisted.add(new ExecutionMessage(UUID.randomUUID().toString(), "tool", result.content(), Instant.now()));
		result.visuals().forEach(visual -> attempt.visuals.put(visual.id(), visual));
		attempt.calls.add(new ToolCall(UUID.randomUUID().toString(), allowed.tool().id(), providerId, arguments, result.structuredOutput(),
				result.content(), "FINISHED", startedAt, Instant.now()));
		return result.content() == null ? "" : result.content();
	}

	/** Reuses the session created earlier in this attempt, otherwise the one stored for the conversation. */
	private ProviderSession sessionFor(Attempt attempt, String providerId) {
		ProviderSession current = attempt.sessions.get(providerId);
		return current != null ? current : executions.session(attempt.conversationId, attempt.subagent.id(), providerId).orElse(null);
	}

	private Document request(List<Document> messages, List<Document> toolSchemas) {
		Document request = new Document("model", model).append("messages", messages);
		if (!toolSchemas.isEmpty()) {
			request.append("tools", toolSchemas).append("parallel_tool_calls", false);
		}
		return request;
	}

	/** Mutable state of a single attempt. */
	private static final class Attempt {
		final String conversationId;
		final String runId;
		final Task task;
		final Subagent subagent;
		final ProgressReporter reporter;
		final List<Document> messages;
		final List<ExecutionMessage> persisted = new ArrayList<>();
		final List<ToolCall> calls = new ArrayList<>();
		final Map<String, ProviderSession> sessions = new LinkedHashMap<>();
		final Map<String, Visual> visuals = new LinkedHashMap<>();

		Attempt(String conversationId, String runId, Task task, Subagent subagent, ProgressReporter reporter, List<Document> prompt) {
			this.conversationId = conversationId;
			this.runId = runId;
			this.task = task;
			this.subagent = subagent;
			this.reporter = reporter;
			this.messages = new ArrayList<>(prompt);
		}
	}
}
```

Algoritmo de `execute(conversationId, runId, task, reporter)`:

1. Carrega o subagente (`IllegalStateException("Subagente nao encontrado: <id>")` se falta); `AgentSkillAccessPolicy.toolsFor` define as tools **permitidas** (mapa functionName → `AllowedTool`); os *schemas* são os de `ProviderTool.schema()` dessas tools.
2. Monta o prompt (seção 4) e um `Attempt` (estado mutável da tentativa).
3. Repete até `maxToolRounds` (`hub.openai.max-tool-rounds`, mínimo 1, default 8): chama o modelo (`hub.openai.subagent-model`) com `{"model", "messages"}` e, **se houver tools**, `"tools"` e `"parallel_tool_calls": false`; acrescenta a resposta às mensagens e grava `ExecutionMessage("assistant")`. Sem `tool_calls` → essa é a **resposta final** e o laço encerra. Com `tool_calls` → executa cada uma em ordem (`callTool`).
4. `callTool`: tool fora das permitidas → `IllegalStateException("Tool nao autorizada para o subagente: <nome>")`; reporta `TOOL_CALLING` (`"Consultando <nome da Tool>"`); executa a tool com um `ProviderToolContext` (sessão = a criada antes **nesta tentativa** para o provider, senão a salva da conversa); guarda a nova sessão; acrescenta a mensagem `tool` (com `tool_call_id`) e `ExecutionMessage("tool")`; acumula os `Visual`s por `id` (o último vence); grava o `ToolCall` (com `output` = detalhes do provider e `outputText` = `content` da tool, status `FINISHED`); devolve o texto da tool.
5. Ao fim reporta `AWAITING_VALIDATION` (`"Output recebido, aguardando validacao"`) e monta o `TaskOutput`: texto = resposta final **se tiver texto**, senão o texto da **última** tool; visuais = os acumulados. Devolve `TaskExecutionResult(taskId, subagentId, output.toMap(), mensagens, sessões, toolCalls, consumedArtifactIds)`.
6. Se o modelo esgotar as rodadas sem resposta final, o texto é o da última tool (ou vazio).

## 6. `ArtifactService` e `ArtifactView`

**Arquivo `src/main/java/com/lamorim/hubdeia/application/ArtifactService.java`** (literal):

```java
package com.lamorim.hubdeia.application;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.repository.ArtifactRepository;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

/**
 * Artifacts are published when a task output is approved. A new artifact supersedes the previous
 * active one of the same type; staleness is derived on read, never stored.
 */
@Service
public class ArtifactService {

	private static final int LISTED = 20;
	/** Every collision has a winner, so N concurrent publishes of one type need at most N attempts. */
	private static final int MAX_VERSION_ATTEMPTS = 16;

	private final ArtifactRepository artifacts;
	private final SubagentRepository subagents;
	private final WorkContextService contexts;
	private final MongoTemplate mongo;

	public ArtifactService(ArtifactRepository artifacts, SubagentRepository subagents, WorkContextService contexts, MongoTemplate mongo) {
		this.artifacts = artifacts;
		this.subagents = subagents;
		this.contexts = contexts;
		this.mongo = mongo;
	}

	/** Publishes the approved task output when its subagent declares an artifact type to produce. */
	public Optional<Artifact> publish(AgentRun run, Task task, TaskExecutionResult result) {
		Optional<Subagent> subagent = subagents.findById(task.subagentSelected()).filter(value -> !value.produces().isEmpty());
		TaskOutput output = TaskOutput.from(result.output());
		if (subagent.isEmpty() || output.content().isBlank()) {
			return Optional.empty();
		}
		String type = subagent.get().produces().getFirst();
		String workContextId = contexts.find(run.conversationWorkContextId()).map(ConversationWorkContext::workContextId).orElse(null);
		Artifact artifact = insertNextVersion(run.conversationId(), type, version -> new Artifact(UUID.randomUUID().toString(), run.conversationId(),
				workContextId, type, version, ArtifactStatus.ACTIVE, result.consumedArtifactIds(), task.description(), output.content(), output.visuals(), subagent.get().id(),
				run.id(), task.id(), Instant.now()));
		supersedeOlderVersions(artifact);
		return Optional.of(artifact);
	}

	/** Makes an existing artifact of this conversation the active one of its type again. */
	public Optional<Artifact> reactivate(String conversationId, String artifactId) {
		Optional<Artifact> target = artifacts.findByIdAndConversationId(artifactId, conversationId);
		target.filter(artifact -> artifact.status() != ArtifactStatus.ACTIVE).ifPresent(artifact -> {
			supersedeOthers(artifact);
			mongo.updateFirst(Query.query(where("_id").is(artifact.id())), new Update().set("status", ArtifactStatus.ACTIVE), Artifact.class);
		});
		return target.flatMap(artifact -> artifacts.findById(artifact.id()));
	}

	/** Most recent artifacts of the conversation (newest first) with their derived staleness. */
	public List<ArtifactView> list(String conversationId) {
		List<Artifact> recent = artifacts.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, LISTED));
		List<String> parentIds = recent.stream().flatMap(artifact -> artifact.derivedFrom().stream()).distinct().toList();
		Map<String, ArtifactStatus> parentStatus = artifacts.findAllById(parentIds).stream().collect(Collectors.toMap(Artifact::id, Artifact::status));
		return recent.stream()
				.map(artifact -> ArtifactView.of(artifact, artifact.derivedFrom().stream().anyMatch(id -> parentStatus.get(id) == ArtifactStatus.SUPERSEDED)))
				.toList();
	}

	/** Latest active artifact of every type the subagent consumes. */
	public List<Artifact> consumedBy(String conversationId, Subagent subagent) {
		return subagent.consumes().stream()
				.map(type -> artifacts.findFirstByConversationIdAndTypeAndStatusOrderByVersionDesc(conversationId, type, ArtifactStatus.ACTIVE))
				.flatMap(Optional::stream)
				.toList();
	}

	/** Versions are unique per conversation + type; a parallel publish that took the same number retries with the next one. */
	private Artifact insertNextVersion(String conversationId, String type, IntFunction<Artifact> factory) {
		for (int attempt = 0; attempt < MAX_VERSION_ATTEMPTS; attempt++) {
			int version = artifacts.findFirstByConversationIdAndTypeOrderByVersionDesc(conversationId, type).map(Artifact::version).orElse(0) + 1;
			try {
				return artifacts.insert(factory.apply(version));
			} catch (DuplicateKeyException concurrentPublish) {
				// another task published this version first
			}
		}
		throw new IllegalStateException("Nao foi possivel publicar o artefato %s da conversa %s.".formatted(type, conversationId));
	}

	/** The highest version always wins, whatever order concurrent publishes finish in. */
	private void supersedeOlderVersions(Artifact keep) {
		Query query = Query.query(where("conversationId").is(keep.conversationId()).and("type").is(keep.type())
				.and("status").is(ArtifactStatus.ACTIVE).and("version").lt(keep.version()));
		mongo.updateMulti(query, new Update().set("status", ArtifactStatus.SUPERSEDED), Artifact.class);
	}

	private void supersedeOthers(Artifact keep) {
		Query query = Query.query(where("conversationId").is(keep.conversationId()).and("type").is(keep.type())
				.and("status").is(ArtifactStatus.ACTIVE).and("_id").ne(keep.id()));
		mongo.updateMulti(query, new Update().set("status", ArtifactStatus.SUPERSEDED), Artifact.class);
	}
}
```

**Arquivo `src/main/java/com/lamorim/hubdeia/application/ArtifactView.java`** (literal):

```java
package com.lamorim.hubdeia.application;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;

/**
 * Artifact as served to clients, assembled when asked for. {@code stale} is true when a parent it was
 * built from is no longer active. {@code content} is plain text (visuals shown as placeholders);
 * {@code renderedContent} is the markdown with the visuals put back, for display.
 */
public record ArtifactView(
		String id,
		String type,
		int version,
		ArtifactStatus status,
		boolean stale,
		String title,
		String content,

		@JsonProperty("rendered_content")
		String renderedContent,

		@JsonProperty("derived_from")
		List<String> derivedFrom,

		@JsonProperty("work_context_id")
		String workContextId,

		@JsonProperty("subagent_id")
		String subagentId,

		@JsonProperty("created_by_run_id")
		String createdByRunId,

		@JsonProperty("created_at")
		Instant createdAt
) {

	public static ArtifactView of(Artifact artifact, boolean stale) {
		TaskOutput output = new TaskOutput(artifact.content(), artifact.visuals());
		return new ArtifactView(artifact.id(), artifact.type(), artifact.version(), artifact.status(), stale, artifact.title(),
				output.plainText(), output.markdown(), artifact.derivedFrom(), artifact.workContextId(), artifact.subagentId(),
				artifact.createdByRunId(), artifact.createdAt());
	}
}
```

Regras:

- `publish(run, task, result)`: só publica se o subagente da task **declara `produces`** e o texto da saída não é vazio; o tipo é o **primeiro** de `produces`; `workContextId` = id do catálogo da instância da run; `derivedFrom` = `result.consumedArtifactIds()`; título = `task.description()`; conteúdo = texto com marcadores; `visuals` = visuais da saída. A versão é `(maior versão do tipo na conversa) + 1`; se outra publicação paralela tomou o número (`DuplicateKeyException`, índice único), **tenta de novo** recalculando, até 16 vezes. Depois, os artefatos `ACTIVE` do mesmo tipo com versão **menor** viram `SUPERSEDED` (a maior versão sempre vence, qualquer que seja a ordem de término).
- `reactivate(conversationId, artifactId)`: só enxerga artefatos **da conversa**; se não estava ativo, os demais `ACTIVE` do tipo viram `SUPERSEDED` e este vira `ACTIVE`; devolve o artefato atualizado (ou vazio).
- `list(conversationId)`: até **20** mais recentes (data desc); `stale` = algum pai em `derivedFrom` está `SUPERSEDED` (calculado na leitura, nunca guardado).
- `consumedBy(conversationId, subagent)`: para cada tipo em `consumes`, o **ativo de maior versão**.
- `ArtifactView.of(artifact, stale)`: `content` = `plainText()`, `renderedContent` = `markdown()` (montagem em runtime).

## 7. `AgentSkillAccessPolicy`

**Arquivo `src/main/java/com/lamorim/hubdeia/application/orchestration/AgentSkillAccessPolicy.java`** (literal):

```java
package com.lamorim.hubdeia.application.orchestration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentProvider;
import com.lamorim.hubdeia.domain.model.Tool;
import com.lamorim.hubdeia.domain.repository.SkillRepository;
import com.lamorim.hubdeia.domain.repository.ToolRepository;

@Component
public class AgentSkillAccessPolicy {

	private final SkillRepository skills;
	private final ToolRepository tools;

	public AgentSkillAccessPolicy(SkillRepository skills, ToolRepository tools) {
		this.skills = skills;
		this.tools = tools;
	}

	public List<Skill> skillsFor(Subagent subagent) {
		List<String> ids = subagent.skillIds();
		return ids.isEmpty() ? List.of() : skills.findByIdInAndEnabledTrue(ids);
	}

	public Map<String, AllowedTool> toolsFor(Subagent subagent) {
		List<String> ids = skillsFor(subagent).stream().flatMap(skill -> safeIds(skill.toolIds()).stream()).distinct().toList();
		Map<String, AllowedTool> allowed = new LinkedHashMap<>();
		for (Tool tool : ids.isEmpty() ? List.<Tool>of() : tools.findByIdInAndEnabledTrue(ids)) {
			providerFor(subagent, tool.providerId()).ifPresent(provider -> allowed.put(tool.functionName(), new AllowedTool(tool, provider)));
		}
		return allowed;
	}

	private Optional<SubagentProvider> providerFor(Subagent subagent, String providerId) {
		return subagent.providers().stream()
				.filter(SubagentProvider::enabled)
				.filter(provider -> providerId.equals(provider.providerId()))
				.findFirst();
	}

	private List<String> safeIds(List<String> ids) { return ids == null ? List.of() : ids; }

	public record AllowedTool(Tool tool, SubagentProvider provider) {}
}
```

Permissões aplicadas **no backend**: as tools de um subagente são a **união** das `toolIds` das suas skills habilitadas, filtradas por tools habilitadas, **e** só entram se o `Subagent.providers` tem um provider **habilitado** com o mesmo `providerId` da tool. A chave do mapa é `Tool.functionName`.

## Armadilhas e decisões não óbvias

1. O modelo só vê as tools que o backend permite; mesmo que ele peça outra, `callTool` aborta com erro.
2. `parallel_tool_calls: false` garante uma tool por vez, mantendo a sessão do provider coerente.
3. A sessão do provider é reaproveitada **dentro** da tentativa (várias chamadas na mesma tentativa continuam a mesma conversa do provider) e **entre** tentativas/tasks da conversa (tabela `provider_sessions`).
4. Visuais e texto ficam separados em todo o armazenamento (task, execução, artefato); só se juntam em `markdown()` (para pessoas) ou viram placeholders em `plainText()` (para modelos).
5. O texto final do subagente é preferido ao da tool; se vier vazio usa-se a tool (o que preserva os marcadores).
6. A memória individual vem de **tasks anteriores do mesmo subagente na conversa**, nunca do chat nem do planejador.

## Critérios de aceite

(`SubagentMemoryTest`, `TaskOutputTest`, `ArtifactServiceTest`, `AgentRunOrchestratorTest`, spec 11.)

- Memória: 5 últimas tasks do subagente, última tentativa de cada, ordem cronológica, só do próprio subagente/conversa, texto puro e truncado.
- Prompt: sem seção de memória quando não há histórico; artefatos consumidos entram como texto puro; ids retornados para a linhagem.
- `TaskOutput`: marcadores resolvidos uma vez, restantes no fim, `plainText` sem HTML/spec, ida e volta por `toMap/from`.
- Artefatos: novo supersede o anterior; versões únicas sob publicação paralela e exatamente um `ACTIVE`; `stale` derivado; reativar troca o ativo; escopo por conversa.

## Ordem de implementação sugerida

`OpenAiChatClient` → `TaskOutput` → contrato `provider` (5 arquivos) → `AgentSkillAccessPolicy` → `ArtifactView` → `ArtifactService` (que exige, já compilados, `WorkContextService` e `ConversationMemorySanitizer` de `06` e `TaskExecutionResult` de `07`) → `SubagentPromptBuilder` → `SubagentTaskExecutor` (estes dois só compilam junto com o resto da fase de conversa/runs).
