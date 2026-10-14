# 03 — Modelo de domínio, coleções, índices e repositórios MongoDB

> Spec do projeto **Hub de IA** (pacote base `com.lamorim.hubdeia`). Leia antes `00-LEIA-ME.md` e `02-projeto-build-e-infra.md`.

## Objetivo

Definir **todos** os *records*, *enums* e repositórios de `com.lamorim.hubdeia.domain`, o mapeamento para MongoDB (coleções, nomes de campo, índices) e as regras de normalização. Os arquivos de `domain/` são declarativos e pequenos, por isso aparecem **inteiros e literais** na seção 6: copie-os exatamente.

## Arquivos cobertos

Todos os arquivos de `src/main/java/com/lamorim/hubdeia/domain/model/` (27 arquivos) e de `src/main/java/com/lamorim/hubdeia/domain/repository/` (13 arquivos). A lista completa está na seção 6.

## Depende de

`02-projeto-build-e-infra.md` (dependências Maven e `auto-index-creation`).

## 1. Princípios do modelo

1. **Um agregado por coleção.** Dados que crescem sem limite (mensagens, execuções) nunca são embutidos no pai; cada atualização é uma operação atômica em um documento pequeno. Isso elimina perda de atualização entre tarefas paralelas e mensagens simultâneas.
2. **Tudo é `record` imutável.** Alterações no Mongo são feitas por `MongoTemplate` com `Update` (`$set`/`$push`) e não reescrevendo o documento. Para objetos que precisam de cópia modificada existem métodos `with...` (`ConversationWorkContext`).
3. **Listas e mapas nunca são `null`** depois de construídos: os *construtores compactos* convertem `null` em coleção vazia (`AgentRun`, `Task`, `SubagentExecution`, `Subagent`, `WorkContext`, `Artifact`).
4. **Enums** são persistidos como string pelo nome (padrão do Spring Data) e usados nas queries pelo valor do enum.
5. **Nomes de campo no Mongo em `snake_case`** através de `@Field("nome_exato")`. Campos sem `@Field` mantêm o nome Java (`status`, `tasks`, `logs`, `type`, `version`, `title`, `content`, `visuals`, `question`, `role`, `metadata`, `input`, `output`, `messages`, `at`, `level`, `message`, `id`).
6. **Identificadores.** `Conversation.id` é gerado pelo Mongo (`ObjectId` em hex, como `String`). Todos os demais ids são `UUID.randomUUID().toString()` gerados pela aplicação; as entidades de catálogo usam ids legíveis (`agent-mensuracao`, `skill-campaign-measurement`, …).
7. **Datas** são `java.time.Instant` (o Mongo guarda milissegundos; por isso existe `MonotonicTime`, ver `06-conversa-e-planejador.md`).

## 2. Mapa de coleções

| Coleção | Record | Observações |
|---|---|---|
| `conversations` | `Conversation` | Só metadados (dono, última mensagem, datas) |
| `messages` | `Message` | Uma mensagem de chat por documento |
| `conversation_work_contexts` | `ConversationWorkContext` | Instância de um work context do catálogo numa conversa |
| `agent_runs` | `AgentRun` | Contém `tasks` (`Task`) e `logs` (`AgentRunLog`) **embutidos** e `final_answer` (`FinalAnswerBlock`) |
| `task_executions` | `SubagentExecution` | Cada tentativa de um subagente (mensagens e tool calls brutos) |
| `human_input_requests` | `HumanInputRequest` | Perguntas pendentes ao usuário |
| `provider_sessions` | `ProviderSession` | Sessão do provider por conversa+subagente+provider |
| `artifacts` | `Artifact` | Resultados tipados e versionados |
| `providers`, `tools`, `skills`, `subagents`, `work_contexts` | `Provider`, `Tool`, `Skill`, `Subagent`, `WorkContext` | Catálogo (ver `04-catalogo-e-bootstrap.md`) |

Tipos **embutidos** (sem coleção própria): `Task`, `TaskValidation`, `AgentRunLog`, `FinalAnswerBlock`, `ExecutionMessage`, `ToolCall`, `Visual`, `SubagentProvider`. `Provider` declara um campo `tools` (`List<Tool>`) que o catálogo semente deixa vazio.

## 3. Índices (criados por `auto-index-creation`)

| Coleção | Índice | Único |
|---|---|---|
| `conversations` | `{user_id: 1, updated_at: -1}` | não |
| `messages` | `{conversation_id: 1, created_at: 1}` | não |
| `conversation_work_contexts` | `{conversation_id: 1, work_context_id: 1}` | **sim** |
| `conversation_work_contexts` | `{conversation_id: 1, status: 1}` | não |
| `agent_runs` | `{conversation_id: 1, created_at: 1}` | não |
| `task_executions` | `{conversation_id: 1, task_id: 1}` | não |
| `task_executions` | `{conversation_id: 1, subagent_id: 1, started_at: -1}` | não |
| `human_input_requests` | `{conversation_id: 1, status: 1}` | não |
| `provider_sessions` | `{conversation_id: 1, subagent_id: 1, provider_id: 1}` | **sim** |
| `artifacts` | `{conversation_id: 1, type: 1, version: 1}` | **sim** |
| `artifacts` | `{conversation_id: 1, type: 1, status: 1}` | não |
| `artifacts` | `{conversation_id: 1, created_at: -1}` | não |
| `skills` | `key` (`@Indexed(unique = true)`) | **sim** |
| `work_contexts` | `key` (`@Indexed(unique = true)`) | **sim** |
| `subagents` | `name` (`@Indexed`) e `enabled` (`@Indexed`) | não |

Nomes automáticos dos índices (ex.: `conversation_id_1_type_1_version_1`) são os do Mongo; nenhum nome customizado é usado.

## 4. Regras de mapeamento e armadilhas

1. **`MongoTemplate` com a classe da entidade usa nomes de PROPRIEDADE Java** nos `Query`/`Update` (por exemplo `conversationId`, `finalAnswer`, `agentRunLastMessage`); o mapeador traduz para os nomes de `@Field`. O literal `"_id"` também é aceito. Caminhos aninhados como `tasks.$.status`, `tasks.id`, `tasks.$.input.validator_feedback` funcionam com a mesma regra.
2. **A propriedade `id` de documentos embutidos é gravada como `_id`.** Por isso a query `where("tasks.id")` encontra a task.
3. **Enums em `Update`/`Query`** (ex.: `.is(AgentRunStatus.RUNNING)`, `.set("status", TaskStatus.RUNNING)`) são convertidos para string automaticamente.
4. **`@CompoundIndex` em record usado como tipo embutido cria o índice no documento pai.** Por isso as mensagens de execução do subagente usam o record separado `ExecutionMessage` e não `Message` (que tem `@Document` e índice próprios).
5. `Message.conversationId` tem `@JsonIgnore`: o id da conversa **não** aparece nas respostas JSON de mensagens.
6. `Task.isApproved()` consulta o **último** `TaskValidation` e compara `status` com a string `"CONTINUE"` (o domínio não depende do enum de `application.validation`).
7. `Task.create(...)` cria a task em `NEW`, `input = {"enriched_content": <texto ou "">}`, `output = {}`, `validations = []`, `attempts = 0`, `finishedAt = null`.
8. `AgentRunLog.info/error` fixam `level` em `"INFO"`/`"ERROR"` e `at = Instant.now()`.
9. `AgentRunStatus` tem `NEW` e `PLANNING` que **nunca são atribuídos** pelo código atual (a run nasce `RUNNING`); existem por compatibilidade com o front. Idem `HumanInputRequestStatus.DISMISSED` e `TaskStatus.FAILED` (este é usado só quando uma task lança exceção).

## 5. Consultas derivadas dos repositórios (contrato)

Cada repositório estende `MongoRepository<Entidade, String>`. As assinaturas exatas estão na seção 6; resumo do que cada método significa:

- `ConversationRepository.findByIdAndUserId`: checagem de dono. `findByUserIdOrderByUpdatedAtDesc`: lista do usuário, mais recentes primeiro.
- `MessageRepository`: ordem cronológica por `createdAt`; `...OrderByCreatedAtDesc(…, Pageable)` retorna as mais novas primeiro (chamadores usam `.reversed()`); `...CreatedAtBefore...` devolve as anteriores a um instante.
- `AgentRunRepository`: `findByIdAndConversationId` evita ler run de outra conversa; ordenações asc/desc; filtro por `triggeringMessageId`.
- `ConversationWorkContextRepository`: instâncias de uma conversa, filtro por status, `findFirst...Status` (a ativa).
- `HumanInputRequestRepository`: pendências por conversa ou por run.
- `ProviderSessionRepository`: uma sessão por conversa+subagente+provider.
- `SubagentExecutionRepository`: última execução de uma task; execuções recentes de um subagente numa conversa (mais novas primeiro, paginado).
- `ArtifactRepository`: por id+conversa; lista por data desc paginada; maior versão de um tipo; maior versão de um tipo com status.
- `SkillRepository.findByIdInAndEnabledTrue`, `ToolRepository.findByIdInAndEnabledTrue`: catálogo habilitado.
- `WorkContextRepository.findByEnabledTrue`, `findByKeyAndEnabledTrue`.
- `ProviderRepository`, `SubagentRepository`: somente CRUD herdado.

## 6. Código-fonte literal de `domain/`

### 6.1 `domain/model`

#### `src/main/java/com/lamorim/hubdeia/domain/model/AgentRun.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("agent_runs")
@CompoundIndex(def = "{'conversation_id': 1, 'created_at': 1}")
public record AgentRun(
		@Id
		String id,

		@Field("conversation_id")
		String conversationId,

		AgentRunStatus status,
		List<Task> tasks,
		List<AgentRunLog> logs,

		@Field("final_answer")
		List<FinalAnswerBlock> finalAnswer,

		@Field("triggering_message_id")
		String triggeringMessageId,

		@Field("conversation_work_context_id")
		String conversationWorkContextId,

		@Field("continued_human_input_request_id")
		String continuedHumanInputRequestId,

		@Field("agent_run_last_message")
		String agentRunLastMessage,

		@Field("created_at")
		Instant createdAt,

		@Field("finished_at")
		Instant finishedAt
) {

	public AgentRun {
		tasks = tasks == null ? List.of() : tasks;
		logs = logs == null ? List.of() : logs;
		finalAnswer = finalAnswer == null ? List.of() : finalAnswer;
	}
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/AgentRunLog.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

public record AgentRunLog(
		Instant at,
		String level,
		String message,
		AgentRunStatus status,

		@Field("execution_status")
		String executionStatus
) {

	public static AgentRunLog info(AgentRunStatus status, String executionStatus, String message) {
		return new AgentRunLog(Instant.now(), "INFO", message, status, executionStatus);
	}

	public static AgentRunLog error(AgentRunStatus status, String executionStatus, String message) {
		return new AgentRunLog(Instant.now(), "ERROR", message, status, executionStatus);
	}
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/AgentRunStatus.java`

```java
package com.lamorim.hubdeia.domain.model;

public enum AgentRunStatus {
	NEW,
	PLANNING,
	RUNNING,
	FINISHED,
	CANCELLED,
	FAILED
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Artifact.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Typed result produced by an approved task (for example an INSIGHT). Artifacts belong to the
 * conversation, so they survive a change of work context.
 */
@Document("artifacts")
@CompoundIndex(def = "{'conversation_id': 1, 'type': 1, 'version': 1}", unique = true)
@CompoundIndex(def = "{'conversation_id': 1, 'type': 1, 'status': 1}")
@CompoundIndex(def = "{'conversation_id': 1, 'created_at': -1}")
public record Artifact(
		@Id
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("work_context_id")
		String workContextId,

		String type,
		int version,
		ArtifactStatus status,

		@Field("derived_from")
		List<String> derivedFrom,

		String title,

		/** Text that may reference visuals with {@code [[visualization:id]]} markers. */
		String content,

		List<Visual> visuals,

		@Field("subagent_id")
		String subagentId,

		@Field("created_by_run_id")
		String createdByRunId,

		@Field("created_by_task_id")
		String createdByTaskId,

		@Field("created_at")
		Instant createdAt
) {

	public Artifact {
		derivedFrom = derivedFrom == null ? List.of() : derivedFrom;
		visuals = visuals == null ? List.of() : visuals;
	}
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/ArtifactStatus.java`

```java
package com.lamorim.hubdeia.domain.model;

public enum ArtifactStatus {
	ACTIVE,
	SUPERSEDED
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Conversation.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("conversations")
@CompoundIndex(def = "{'user_id': 1, 'updated_at': -1}")
public record Conversation(
		@Id
		String id,

		@Field("user_id")
		String userId,

		@Field("chat_last_message")
		String chatLastMessage,

		@Field("created_at")
		Instant createdAt,

		@Field("updated_at")
		Instant updatedAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/ConversationWorkContext.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Instance of a catalog {@link WorkContext} inside one conversation. */
@Document("conversation_work_contexts")
@CompoundIndex(def = "{'conversation_id': 1, 'work_context_id': 1}", unique = true)
@CompoundIndex(def = "{'conversation_id': 1, 'status': 1}")
public record ConversationWorkContext(
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("work_context_id")
		String workContextId,

		ConversationWorkContextStatus status,

		@Field("last_agent_run_id")
		String lastAgentRunId,

		@Field("created_at")
		Instant createdAt,

		@Field("updated_at")
		Instant updatedAt
) {

	public ConversationWorkContext withStatus(ConversationWorkContextStatus newStatus, Instant now) {
		return new ConversationWorkContext(id, conversationId, workContextId, newStatus, lastAgentRunId, createdAt, now);
	}

	public ConversationWorkContext withLastAgentRun(String runId, Instant now) {
		return new ConversationWorkContext(id, conversationId, workContextId, status, runId, createdAt, now);
	}
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/ConversationWorkContextStatus.java`

```java
package com.lamorim.hubdeia.domain.model;

public enum ConversationWorkContextStatus {
	ACTIVE,
	PAUSED
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/ExecutionMessage.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

/** Message exchanged between a subagent and its model or tools, stored inside a {@link SubagentExecution}. */
public record ExecutionMessage(
		String id,
		String role,
		String content,

		@Field("created_at")
		Instant createdAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/FinalAnswerBlock.java`

```java
package com.lamorim.hubdeia.domain.model;

import org.springframework.data.mongodb.core.mapping.Field;

public record FinalAnswerBlock(
		String type,
		String title,
		String markdown,

		@Field("task_id")
		String taskId,

		@Field("subagent_id")
		String subagentId
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/HumanInputRequest.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("human_input_requests")
@CompoundIndex(def = "{'conversation_id': 1, 'status': 1}")
public record HumanInputRequest(
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("agent_run_id")
		String agentRunId,

		@Field("task_id")
		String taskId,

		String question,
		HumanInputRequestStatus status,

		@Field("answer_message_id")
		String answerMessageId,

		@Field("created_at")
		Instant createdAt,

		@Field("answered_at")
		Instant answeredAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/HumanInputRequestStatus.java`

```java
package com.lamorim.hubdeia.domain.model;

public enum HumanInputRequestStatus {
	PENDING,
	ANSWERED,
	DISMISSED
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Message.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Chat message of a conversation. */
@Document("messages")
@CompoundIndex(def = "{'conversation_id': 1, 'created_at': 1}")
public record Message(
		String id,

		@JsonIgnore
		@Field("conversation_id")
		String conversationId,

		String role,
		String content,

		@Field("created_at")
		Instant createdAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Provider.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("providers")
public record Provider(
		@Id
		String id,
		String name,
		String category,

		@Field("external_agent_id")
		String externalAgentId,

		List<Tool> tools,
		Map<String, Object> config,
		boolean enabled
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/ProviderSession.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** External provider conversation reused 1:1 per conversation + subagent + provider. */
@Document("provider_sessions")
@CompoundIndex(def = "{'conversation_id': 1, 'subagent_id': 1, 'provider_id': 1}", unique = true)
public record ProviderSession(
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("subagent_id")
		String subagentId,

		@Field("provider_id")
		String providerId,

		@Field("external_session_id")
		String externalSessionId,

		String status,
		Map<String, Object> metadata,

		@Field("created_at")
		Instant createdAt,

		@Field("updated_at")
		Instant updatedAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Skill.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("skills")
public record Skill(
		@Id
		String id,

		@Indexed(unique = true)
		String key,

		String name,
		String description,
		String instructions,

		@Field("tool_ids")
		List<String> toolIds,

		boolean enabled
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Subagent.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("subagents")
public record Subagent(
		@Id
		String id,

		@Indexed
		String name,

		String description,

		@Field("system_prompt")
		String systemPrompt,

		@Field("skill_ids")
		List<String> skillIds,

		List<SubagentProvider> providers,

		/** Artifact types this subagent publishes when its task output is approved. */
		List<String> produces,

		/** Artifact types injected into this subagent's prompt when available. */
		List<String> consumes,

		@Indexed
		boolean enabled
) {

	public Subagent {
		skillIds = skillIds == null ? List.of() : skillIds;
		providers = providers == null ? List.of() : providers;
		produces = produces == null ? List.of() : produces;
		consumes = consumes == null ? List.of() : consumes;
	}
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/SubagentExecution.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** One attempt of a subagent on a task, including its raw messages and tool calls. */
@Document("task_executions")
@CompoundIndex(def = "{'conversation_id': 1, 'task_id': 1}")
@CompoundIndex(def = "{'conversation_id': 1, 'subagent_id': 1, 'started_at': -1}")
public record SubagentExecution(
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("agent_run_id")
		String agentRunId,

		@Field("task_id")
		String taskId,

		@Field("subagent_id")
		String subagentId,

		List<ExecutionMessage> messages,

		@Field("tool_calls")
		List<ToolCall> toolCalls,

		Map<String, Object> input,
		Map<String, Object> output,
		String status,
		int attempt,

		@Field("started_at")
		Instant startedAt,

		@Field("finished_at")
		Instant finishedAt
) {

	public SubagentExecution {
		messages = messages == null ? List.of() : messages;
		toolCalls = toolCalls == null ? List.of() : toolCalls;
	}
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/SubagentProvider.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

public record SubagentProvider(
		String id,

		@Field("provider_id")
		String providerId,

		Map<String, Object> config,

		boolean enabled
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Task.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

public record Task(
		String id,
		String description,

		@Field("subagent_selected")
		String subagentSelected,

		TaskStatus status,
		Map<String, Object> input,
		Map<String, Object> output,
		List<TaskValidation> validations,
		int attempts,

		@Field("max_attempts")
		int maxAttempts,

		@Field("created_at")
		Instant createdAt,

		@Field("finished_at")
		Instant finishedAt
) {

	public Task {
		input = input == null ? Map.of() : input;
		output = output == null ? Map.of() : output;
		validations = validations == null ? List.of() : validations;
	}

	public static Task create(String id, String description, String subagentId, String enrichedContent, int maxAttempts, Instant now) {
		return new Task(id, description, subagentId, TaskStatus.NEW, Map.of("enriched_content", enrichedContent == null ? "" : enrichedContent),
				Map.of(), List.of(), 0, maxAttempts, now, null);
	}

	public boolean isApproved() {
		return !validations.isEmpty() && "CONTINUE".equals(validations.getLast().status());
	}
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/TaskStatus.java`

```java
package com.lamorim.hubdeia.domain.model;

public enum TaskStatus {
	NEW,
	RUNNING,
	FINISHED,
	CANCELLED,
	FAILED
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/TaskValidation.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

public record TaskValidation(
		String status,
		String feedback,

		@Field("message_to_user")
		String messageToUser,

		int attempt,

		@Field("validated_at")
		Instant validatedAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Tool.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("tools")
public record Tool(
		@Id
		String id,

		@Field("provider_id")
		String providerId,

		String name,
		String type,

		@Field("function_name")
		String functionName,

		String description,

		@Field("input_schema")
		Map<String, Object> inputSchema,

		@Field("output_schema")
		Map<String, Object> outputSchema,

		Map<String, Object> config,
		boolean enabled
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/ToolCall.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

public record ToolCall(
		String id,

		@Field("tool_id")
		String toolId,

		@Field("provider_id")
		String providerId,

		Map<String, Object> input,
		Map<String, Object> output,

		@Field("output_text")
		String outputText,
		String status,

		@Field("started_at")
		Instant startedAt,

		@Field("finished_at")
		Instant finishedAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/Visual.java`

```java
package com.lamorim.hubdeia.domain.model;

/**
 * Provider-neutral visual content (table, chart) built by a tool. The core stores and displays it
 * without knowing which provider made it; text refers to it with a {@code [[visualization:id]]} marker.
 */
public record Visual(
		String id,
		String title,
		VisualFormat format,
		String payload
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/VisualFormat.java`

```java
package com.lamorim.hubdeia.domain.model;

/** How a {@link Visual} is displayed. The core only knows how to show these formats, never who produced them. */
public enum VisualFormat {
	/** Self-contained, sanitized-on-display HTML (for example a table). */
	HTML,
	/** A Vega-Lite specification, drawn by the interface. */
	VEGA_LITE
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/model/WorkContext.java`

```java
package com.lamorim.hubdeia.domain.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("work_contexts")
public record WorkContext(
		@Id
		String id,

		@Indexed(unique = true)
		String key,

		String name,
		String description,
		String instructions,

		@Field("agent_ids")
		List<String> agentIds,

		boolean enabled
) {

	public WorkContext {
		agentIds = agentIds == null ? List.of() : agentIds;
	}
}
```

### 6.2 `domain/repository`

#### `src/main/java/com/lamorim/hubdeia/domain/repository/AgentRunRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.AgentRun;

public interface AgentRunRepository extends MongoRepository<AgentRun, String> {

	Optional<AgentRun> findByIdAndConversationId(String id, String conversationId);

	List<AgentRun> findByConversationIdOrderByCreatedAtAsc(String conversationId);

	/** Newest first. */
	List<AgentRun> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable page);

	List<AgentRun> findByConversationIdAndTriggeringMessageIdOrderByCreatedAtAsc(String conversationId, String triggeringMessageId);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/ArtifactRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;

public interface ArtifactRepository extends MongoRepository<Artifact, String> {

	Optional<Artifact> findByIdAndConversationId(String id, String conversationId);

	List<Artifact> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable page);

	Optional<Artifact> findFirstByConversationIdAndTypeOrderByVersionDesc(String conversationId, String type);

	Optional<Artifact> findFirstByConversationIdAndTypeAndStatusOrderByVersionDesc(String conversationId, String type, ArtifactStatus status);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/ConversationRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Conversation;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

	List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);

	Optional<Conversation> findByIdAndUserId(String id, String userId);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/ConversationWorkContextRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.ConversationWorkContextStatus;

public interface ConversationWorkContextRepository extends MongoRepository<ConversationWorkContext, String> {

	List<ConversationWorkContext> findByConversationId(String conversationId);

	Optional<ConversationWorkContext> findFirstByConversationIdAndStatus(String conversationId, ConversationWorkContextStatus status);

	List<ConversationWorkContext> findByConversationIdAndStatus(String conversationId, ConversationWorkContextStatus status);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/HumanInputRequestRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.HumanInputRequestStatus;

public interface HumanInputRequestRepository extends MongoRepository<HumanInputRequest, String> {

	List<HumanInputRequest> findByConversationIdAndStatus(String conversationId, HumanInputRequestStatus status);

	List<HumanInputRequest> findByAgentRunIdAndStatus(String agentRunId, HumanInputRequestStatus status);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/MessageRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Message;

public interface MessageRepository extends MongoRepository<Message, String> {

	List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

	Optional<Message> findByIdAndConversationId(String id, String conversationId);

	/** Newest first; callers reverse when they need chronological order. */
	List<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable page);

	List<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(String conversationId, Instant before, Pageable page);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/ProviderRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Provider;

public interface ProviderRepository extends MongoRepository<Provider, String> {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/ProviderSessionRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.ProviderSession;

public interface ProviderSessionRepository extends MongoRepository<ProviderSession, String> {

	Optional<ProviderSession> findByConversationIdAndSubagentIdAndProviderId(String conversationId, String subagentId, String providerId);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/SkillRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Skill;

public interface SkillRepository extends MongoRepository<Skill, String> {
	List<Skill> findByIdInAndEnabledTrue(List<String> ids);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/SubagentExecutionRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.SubagentExecution;

public interface SubagentExecutionRepository extends MongoRepository<SubagentExecution, String> {

	Optional<SubagentExecution> findFirstByTaskIdOrderByStartedAtDesc(String taskId);

	List<SubagentExecution> findByConversationIdAndSubagentIdOrderByStartedAtDesc(String conversationId, String subagentId, Pageable page);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/SubagentRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Subagent;

public interface SubagentRepository extends MongoRepository<Subagent, String> {
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/ToolRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Tool;

public interface ToolRepository extends MongoRepository<Tool, String> {
	List<Tool> findByIdInAndEnabledTrue(List<String> ids);
}
```

#### `src/main/java/com/lamorim/hubdeia/domain/repository/WorkContextRepository.java`

```java
package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.WorkContext;

public interface WorkContextRepository extends MongoRepository<WorkContext, String> {
	List<WorkContext> findByEnabledTrue();
	Optional<WorkContext> findByKeyAndEnabledTrue(String key);
}
```

## Critérios de aceite

- A aplicação sobe contra um Mongo vazio e `db.getCollectionNames()` mostra as coleções à medida que são usadas, com os índices da tabela da seção 3 (inclusive os 4 únicos: `conversation_work_contexts`, `provider_sessions`, `artifacts`, `skills`/`work_contexts` por `key`).
- Gravar e ler cada record preserva todos os campos; um documento sem `tasks`/`logs`/`finalAnswer` lê como listas vazias.
- `Task.isApproved()` é falso sem validações e verdadeiro só se a última validação for `CONTINUE`.
- Os testes `AgentRunStoreTest` e `ArtifactServiceTest` (spec 11) passam, o que exercita o mapeamento posicional e os índices únicos.

## Ordem de implementação sugerida

1. Enums (`AgentRunStatus`, `TaskStatus`, `ArtifactStatus`, `ConversationWorkContextStatus`, `HumanInputRequestStatus`, `VisualFormat`).
2. Records embutidos (`TaskValidation`, `Task`, `AgentRunLog`, `FinalAnswerBlock`, `ExecutionMessage`, `ToolCall`, `Visual`, `SubagentProvider`).
3. Records de coleção e catálogo.
4. Repositórios.
