# 05 — API REST, DTOs, autenticação por cabeçalho e CORS

> Spec do projeto **Hub de IA** (pacote base `com.lamorim.hubdeia`). Leia antes `00-LEIA-ME.md`.

## Objetivo

Definir a superfície HTTP do sistema: cada endpoint (método, caminho, cabeçalhos, corpo, status e formato JSON de resposta), os DTOs, a autenticação por cabeçalho e as regras de erro. O contrato é consumido pelo frontend (`10-frontend.md`).

## Arquivos cobertos

- `src/main/java/com/lamorim/hubdeia/api/ConversationController.java`
- `src/main/java/com/lamorim/hubdeia/api/AuthHeaders.java`
- `src/main/java/com/lamorim/hubdeia/api/dto/*.java` (8 arquivos)
- `src/main/java/com/lamorim/hubdeia/HelloController.java`
- `src/main/java/com/lamorim/hubdeia/config/CorsConfig.java` (código literal em `02-projeto-build-e-infra.md`)

## Depende de

`02-projeto-build-e-infra.md` (snake_case global, CORS), `03-dominio-e-mongodb.md` (`Message`, `FinalAnswerBlock`), `06-conversa-e-planejador.md` (`ConversationService`, `ConversationWorker`), `07-orquestracao-de-runs.md` (`AgentRunService`), `08-execucao-do-subagente-e-conteudo.md` (`ArtifactService`, `ArtifactView`).

## 1. Convenções gerais

- **Autenticação:** não existe login. O cabeçalho `Authorization` carrega o **id do usuário em texto puro** (por exemplo `user-test-1`). Todo endpoint de conversa o exige; sem ele a resposta é **401** (`ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required.")`). O valor é `trim`ado e usado como `userId`.
- **Posse:** uma conversa só é visível ao `userId` que a criou. Conversa inexistente **ou de outro usuário** → **404** com motivo `"Conversation not found."`.
- **JSON em `snake_case`:** aplicado globalmente por `spring.jackson.property-naming-strategy: SNAKE_CASE` (ver 02). Os DTOs de resposta também declaram `@JsonProperty` explícito nos campos compostos; as entidades serializadas diretamente (`Message`, `FinalAnswerBlock`) dependem **só** da estratégia global.
- **Datas** são `Instant` serializados em ISO-8601 UTC (por exemplo `2026-10-13T00:52:15.385Z`); onde o DTO usa `String` (`AgentRunLogEntry.at`, `AgentRunStatusResponse.updatedAt`) o valor é `Instant.toString()`.
- **Erros:** vêm de `ResponseStatusException` e seguem o corpo de erro padrão do Spring Boot (não há `@ControllerAdvice`; o projeto não configura `server.error.*`). Mensagens usadas: `"message is required."` (400), `"Authorization header is required."` (401), `"Conversation not found."`, `"Message not found."`, `"AgentRun not found."` (404).
- **CORS:** qualquer origem, métodos `GET`, `POST`, `OPTIONS`, qualquer cabeçalho (ver 02).
- **Assíncrono:** os `POST` respondem **202 Accepted** imediatamente; o resultado do processamento aparece depois, por *polling* (`GET`).

## 2. Endpoints

| # | Método e caminho | Cabeçalho | Corpo | Sucesso | Método de serviço |
|---|---|---|---|---|---|
| 1 | `POST /conversation` | `Authorization` | `{"message": "..."}` | **202** `MessageAcceptedResponse` | `ConversationService.create(userId, message)` e depois `ConversationWorker.dispatch(conversationId, messageId)` |
| 2 | `POST /conversations/{conversationId}` **e** `POST /conversation/{conversationId}` (alias) | `Authorization` | `{"message": "..."}` | **202** `MessageAcceptedResponse` | `ConversationService.appendUserMessage(userId, conversationId, message)` e depois `ConversationWorker.dispatch` |
| 3 | `GET /conversations` | `Authorization` | — | 200 lista de `ConversationSummaryResponse` | `ConversationService.summaries(userId)` |
| 4 | `GET /conversations/{conversationId}` | `Authorization` | — | 200 `ConversationDetailResponse` | `ConversationService.detail(userId, conversationId)` |
| 5 | `GET /conversation/{conversationId}/messages` (**singular**) | `Authorization` | — | 200 lista de `Message` | `ConversationService.messages(userId, conversationId)` |
| 6 | `GET /conversations/{conversationId}/agent-runs` | `Authorization` | query opcional `triggering_message_id` | 200 lista de `AgentRunSummaryResponse` | `requireOwned` e depois `AgentRunService.summaries(conversationId, messageId)` |
| 7 | `GET /conversations/{conversationId}/agent-runs/{agentRunId}` | `Authorization` | — | 200 `AgentRunStatusResponse` | `requireOwned` e depois `AgentRunService.status(conversationId, agentRunId)` |
| 8 | `GET /conversations/{conversationId}/artifacts` | `Authorization` | — | 200 lista de `ArtifactView` | `requireOwned` e depois `ArtifactService.list(conversationId)` |
| 9 | `GET /hello` | — | — | 200 texto `Hello World!` | `HelloController.hello()` |

Detalhes importantes:

- Nos `POST`, o `Authorization` é opcional **no binding** (`required = false`) mas, se ausente, `AuthHeaders.requireUserId` lança 401. A ordem é: validar o cabeçalho → chamar o serviço (que valida o texto e a posse) → despachar o worker → devolver a resposta.
- **Corpo vazio ou `message` em branco** → `ConversationService` lança `ResponseStatusException(BAD_REQUEST, "message is required.")` (400). Em `POST /conversations/{id}`, `appendUserMessage` valida o texto (`requireText`) **antes** de conferir a posse (`requireOwned`); portanto texto vazio numa conversa alheia responde 400, não 404.
- `GET /conversation/{id}/messages` usa `conversation` no **singular** (diferente dos demais, que usam `conversations`). O POST tem os dois formatos (`/conversations/{id}` e `/conversation/{id}`).
- Os endpoints 6, 7 e 8 conferem a posse com `ConversationService.requireOwned(userId, conversationId)` **antes** de qualquer leitura; uma run de outra conversa responde 404 `"AgentRun not found."` porque `AgentRunService.require` busca por `(runId, conversationId)`.
- `GET .../agent-runs` sem `triggering_message_id` devolve todas as runs da conversa em ordem de criação crescente; com o parâmetro, só as disparadas por aquela mensagem.

## 3. Formatos JSON

### 3.1 `MessageAcceptedResponse`
```json
{ "status": "ACCEPTED", "conversation_id": "6acd80bf533125e98d7e647d", "message_id": "3158c86b-744b-49ec-970d-a49376f43fec" }
```

### 3.2 `ConversationSummaryResponse` (item do endpoint 3)
```json
{ "id": "…", "chat_last_message": "texto (até 200 caracteres)", "created_at": "…Z", "updated_at": "…Z" }
```

### 3.3 `ConversationDetailResponse` (endpoint 4)
```json
{ "id": "…", "chat_last_message": "…", "messages": [ { "id": "…", "role": "user", "content": "…", "created_at": "…Z" } ], "created_at": "…Z", "updated_at": "…Z" }
```
`messages` em ordem cronológica crescente. Cada `Message` tem **exatamente** `id`, `role` (`user` | `assistant`), `content`, `created_at` (o `conversationId` é `@JsonIgnore`).

### 3.4 `AgentRunSummaryResponse` (endpoint 6)
```json
{ "id": "<runId>", "status": "FINISHED", "triggering_message_id": "…", "work_context_id": "work-context-analise-dados", "created_at": "…Z", "finished_at": "…Z" }
```
`work_context_id` é o id do **catálogo** (não o da instância); pode ser `null`. `finished_at` é `null` enquanto a run roda.

### 3.5 `AgentRunStatusResponse` (endpoint 7)
```json
{
  "conversation_id": "…", "agent_run_id": "…", "status": "RUNNING", "execution_status": "RUNNING",
  "current_message": "última mensagem de progresso",
  "logs": [ { "at": "…Z", "level": "INFO", "message": "…" } ],
  "final_answer": [ { "type": "communicator", "title": "Resumo", "markdown": "…", "task_id": null, "subagent_id": null } ],
  "updated_at": "…Z", "source": "database"
}
```
`status` e `execution_status` carregam hoje o **mesmo** valor (`RUNNING`/`FINISHED`/`CANCELLED`/`FAILED`); `source` é sempre `"database"` (campo mantido por compatibilidade). A semântica completa está em `07-orquestracao-de-runs.md`.

### 3.6 `ArtifactView` (endpoint 8; detalhado em 08)
Campos: `id`, `type`, `version`, `status` (`ACTIVE`/`SUPERSEDED`), `stale`, `title`, `content` (texto puro), `rendered_content` (markdown com os visuais), `derived_from`, `work_context_id`, `subagent_id`, `created_by_run_id`, `created_at`. Ordem: mais recentes primeiro (máx. 20).

## 4. Exemplos (`curl`)

```text
curl -s -X POST localhost:8080/conversation -H 'Authorization: user-test-1' -H 'Content-Type: application/json' -d '{"message":"Como foi a campanha?"}'
curl -s localhost:8080/conversations -H 'Authorization: user-test-1'
curl -s localhost:8080/conversations/<id> -H 'Authorization: user-test-1'
curl -s "localhost:8080/conversations/<id>/agent-runs?triggering_message_id=<messageId>" -H 'Authorization: user-test-1'
curl -s localhost:8080/conversations/<id>/agent-runs/<runId> -H 'Authorization: user-test-1'
curl -s localhost:8080/conversations/<id>/artifacts -H 'Authorization: user-test-1'
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/conversations/<id> -H 'Authorization: outro-usuario'   # 404
```

## 5. Código-fonte literal

**Arquivo `src/main/java/com/lamorim/hubdeia/api/ConversationController.java`** (literal):

```java
package com.lamorim.hubdeia.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lamorim.hubdeia.api.dto.AgentRunStatusResponse;
import com.lamorim.hubdeia.api.dto.AgentRunSummaryResponse;
import com.lamorim.hubdeia.api.dto.ConversationDetailResponse;
import com.lamorim.hubdeia.api.dto.ConversationSummaryResponse;
import com.lamorim.hubdeia.api.dto.CreateConversationRequest;
import com.lamorim.hubdeia.api.dto.MessageAcceptedResponse;
import com.lamorim.hubdeia.api.dto.SendConversationMessageRequest;
import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.ArtifactView;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.ConversationWorker;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.domain.model.Message;

@RestController
public class ConversationController {

	private static final String AUTHORIZATION = "Authorization";

	private final AuthHeaders authHeaders;
	private final ConversationService conversations;
	private final AgentRunService runs;
	private final ArtifactService artifacts;
	private final ConversationWorker worker;

	public ConversationController(AuthHeaders authHeaders, ConversationService conversations, AgentRunService runs,
			ArtifactService artifacts, ConversationWorker worker) {
		this.authHeaders = authHeaders;
		this.conversations = conversations;
		this.runs = runs;
		this.artifacts = artifacts;
		this.worker = worker;
	}

	@PostMapping("/conversation")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageAcceptedResponse create(@RequestHeader(value = AUTHORIZATION, required = false) String authorization, @RequestBody CreateConversationRequest request) {
		MessageAcceptedResponse response = conversations.create(authHeaders.requireUserId(authorization), request.message());
		worker.dispatch(response.conversationId(), response.messageId());
		return response;
	}

	@PostMapping({"/conversations/{conversationId}", "/conversation/{conversationId}"})
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageAcceptedResponse sendMessage(
			@RequestHeader(value = AUTHORIZATION, required = false) String authorization,
			@PathVariable String conversationId,
			@RequestBody SendConversationMessageRequest request
	) {
		MessageAcceptedResponse response = conversations.appendUserMessage(authHeaders.requireUserId(authorization), conversationId, request.message());
		worker.dispatch(response.conversationId(), response.messageId());
		return response;
	}

	@GetMapping("/conversations")
	public List<ConversationSummaryResponse> conversations(@RequestHeader(value = AUTHORIZATION, required = false) String authorization) {
		return conversations.summaries(authHeaders.requireUserId(authorization));
	}

	@GetMapping("/conversations/{conversationId}")
	public ConversationDetailResponse conversation(@RequestHeader(value = AUTHORIZATION, required = false) String authorization, @PathVariable String conversationId) {
		return conversations.detail(authHeaders.requireUserId(authorization), conversationId);
	}

	@GetMapping("/conversation/{conversationId}/messages")
	public List<Message> messages(@RequestHeader(value = AUTHORIZATION, required = false) String authorization, @PathVariable String conversationId) {
		return conversations.messages(authHeaders.requireUserId(authorization), conversationId);
	}

	@GetMapping("/conversations/{conversationId}/agent-runs")
	public List<AgentRunSummaryResponse> agentRuns(
			@RequestHeader(value = AUTHORIZATION, required = false) String authorization,
			@PathVariable String conversationId,
			@RequestParam(value = "triggering_message_id", required = false) String messageId
	) {
		conversations.requireOwned(authHeaders.requireUserId(authorization), conversationId);
		return runs.summaries(conversationId, messageId);
	}

	@GetMapping("/conversations/{conversationId}/agent-runs/{agentRunId}")
	public AgentRunStatusResponse agentRunStatus(
			@RequestHeader(value = AUTHORIZATION, required = false) String authorization,
			@PathVariable String conversationId,
			@PathVariable String agentRunId
	) {
		conversations.requireOwned(authHeaders.requireUserId(authorization), conversationId);
		return runs.status(conversationId, agentRunId);
	}

	@GetMapping("/conversations/{conversationId}/artifacts")
	public List<ArtifactView> artifacts(@RequestHeader(value = AUTHORIZATION, required = false) String authorization, @PathVariable String conversationId) {
		conversations.requireOwned(authHeaders.requireUserId(authorization), conversationId);
		return artifacts.list(conversationId);
	}
}
```

**Arquivo `src/main/java/com/lamorim/hubdeia/api/AuthHeaders.java`** (literal):

```java
package com.lamorim.hubdeia.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthHeaders {

	public String requireUserId(String authorization) {
		if (!StringUtils.hasText(authorization)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required.");
		}

		return authorization.trim();
	}
}
```

**Arquivo `src/main/java/com/lamorim/hubdeia/HelloController.java`** (literal):

```java
package com.lamorim.hubdeia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	@GetMapping("/hello")
	public String hello() {
		return "Hello World!";
	}
}
```

### DTOs

#### `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunLogEntry.java`

```java
package com.lamorim.hubdeia.api.dto;

public record AgentRunLogEntry(
		String at,
		String level,
		String message
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunStatusResponse.java`

```java
package com.lamorim.hubdeia.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;

public record AgentRunStatusResponse(
		@JsonProperty("conversation_id")
		String conversationId,

		@JsonProperty("agent_run_id")
		String agentRunId,

		String status,

		@JsonProperty("execution_status")
		String executionStatus,

		@JsonProperty("current_message")
		String currentMessage,

		List<AgentRunLogEntry> logs,

		@JsonProperty("final_answer")
		List<FinalAnswerBlock> finalAnswer,

		@JsonProperty("updated_at")
		String updatedAt,

		String source
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunSummaryResponse.java`

```java
package com.lamorim.hubdeia.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentRunSummaryResponse(
		String id,
		String status,

		@JsonProperty("triggering_message_id")
		String triggeringMessageId,

		@JsonProperty("work_context_id")
		String workContextId,

		@JsonProperty("created_at")
		Instant createdAt,

		@JsonProperty("finished_at")
		Instant finishedAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/api/dto/ConversationDetailResponse.java`

```java
package com.lamorim.hubdeia.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamorim.hubdeia.domain.model.Message;

public record ConversationDetailResponse(
		String id,

		@JsonProperty("chat_last_message")
		String chatLastMessage,

		List<Message> messages,

		@JsonProperty("created_at")
		Instant createdAt,

		@JsonProperty("updated_at")
		Instant updatedAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/api/dto/ConversationSummaryResponse.java`

```java
package com.lamorim.hubdeia.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConversationSummaryResponse(
		String id,

		@JsonProperty("chat_last_message")
		String chatLastMessage,

		@JsonProperty("created_at")
		Instant createdAt,

		@JsonProperty("updated_at")
		Instant updatedAt
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/api/dto/CreateConversationRequest.java`

```java
package com.lamorim.hubdeia.api.dto;

public record CreateConversationRequest(
		String message
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/api/dto/MessageAcceptedResponse.java`

```java
package com.lamorim.hubdeia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageAcceptedResponse(
		String status,

		@JsonProperty("conversation_id")
		String conversationId,

		@JsonProperty("message_id")
		String messageId
) {
}
```

#### `src/main/java/com/lamorim/hubdeia/api/dto/SendConversationMessageRequest.java`

```java
package com.lamorim.hubdeia.api.dto;

public record SendConversationMessageRequest(String message) {
}
```

## 6. Fluxo típico visto pelo cliente

1. `POST /conversation` → 202 com `conversation_id` e `message_id` (a mensagem do usuário já está gravada).
2. O cliente consulta `GET /conversations/{id}` periodicamente. Quando o planejador **responde direto**, aparece uma mensagem `assistant` depois da mensagem do usuário e **nenhuma run** é criada.
3. Quando o planejador **delega**, `GET /conversations/{id}/agent-runs?triggering_message_id=<messageId>` passa a devolver uma run; o cliente acompanha `GET .../agent-runs/{runId}` até `status` ∈ {`FINISHED`, `FAILED`, `CANCELLED`}.
4. O texto final da run é gravado como mensagens `assistant` **logo depois** de a run virar terminal; clientes devem reler as mensagens uma vez após ver o estado final (o frontend faz isso 700 ms depois).
5. `GET .../artifacts` lista os resultados produzidos.

## Armadilhas e decisões não óbvias

1. O endpoint de mensagens usa `/conversation/{id}/messages` (singular); não "corrija" para plural.
2. Os dois formatos de `POST` para uma conversa existente (`/conversations/{id}` e `/conversation/{id}`) precisam existir.
3. Não há paginação em nenhum endpoint (mensagens e conversas vêm inteiras; runs inteiras; artefatos limitados a 20).
4. `AuthHeaders` **não** valida formato nem existência do usuário: qualquer texto não vazio é aceito.

## Critérios de aceite

- `POST /conversation` sem `Authorization` → 401; com `message` vazio → 400; válido → 202 com o JSON de 3.1.
- `GET /conversations/{id}` com outro `Authorization` → 404.
- A resposta de `GET /conversations/{id}` contém `messages` ordenadas e **sem** o campo `conversation_id` dentro de cada mensagem.
- `GET /hello` → `Hello World!`.

## Ordem de implementação sugerida

Os **8 DTOs primeiro** (`ConversationService` e `AgentRunService` os importam, então precisam existir antes dos serviços de `06`/`07`); `AuthHeaders` → `HelloController` → `ConversationController` por último, depois que `ConversationService`, `ConversationWorker`, `AgentRunService` e `ArtifactService` existirem.
