# 12 — Documentação do repositório (literal) e anexos fora de escopo

> Spec do projeto **Hub de IA**. Leia antes `00-LEIA-ME.md`.

## Objetivo

Reproduzir **literalmente** os dois documentos Markdown do repositório (`docs/data-model.md` e `docs/implementation-summary.md`) e registrar, sem reproduzi-los, os arquivos que existem no repositório original mas **não** fazem parte do projeto replicado (imagens, script legado e a pasta `js-hub/`).

## Arquivos cobertos

`docs/data-model.md`, `docs/implementation-summary.md` (recriar exatamente), `docs/hub-ia.png` e `docs/mermaid-diagram.png` (binários: copiar à parte), `scripts/seed-subagents.js` (legado: **não** recriar), `js-hub/` (legado: **não** recriar).

## Depende de

`00-LEIA-ME.md`. Os dois `.md` são a documentação "oficial" curta do projeto e **devem coincidir** com o comportamento descrito nas specs 03 a 09.

## 1. `docs/data-model.md` (recriar exatamente)

**Arquivo `docs/data-model.md`** (literal):

````markdown
# Data Model

Each aggregate lives in its own Mongo collection. Documents that grow without bound (messages, executions) are never embedded in their parent, and every write is a single atomic update on one small document, so parallel tasks and concurrent user messages never overwrite each other.

| Collection | Document | Notes |
|---|---|---|
| `conversations` | `Conversation` | Metadata only. |
| `messages` | `Message` | One document per chat message. |
| `conversation_work_contexts` | `ConversationWorkContext` | Instance of a catalog work context in a conversation. |
| `agent_runs` | `AgentRun` | Tasks and logs are embedded: bounded by the run. |
| `task_executions` | `SubagentExecution` | Raw subagent attempts (messages, tool calls). |
| `human_input_requests` | `HumanInputRequest` | |
| `provider_sessions` | `ProviderSession` | Unique per conversation + subagent + provider. |
| `artifacts` | `Artifact` | Typed results produced by approved tasks. |
| `work_contexts`, `subagents`, `skills`, `tools`, `providers` | catalog | Seeded from `resources/bootstrap`. |

## Conversation and messages

```text
conversations
  id: string
  user_id: string
  chat_last_message: string        # first 200 chars of the latest message
  created_at, updated_at: datetime

messages
  id: string
  conversation_id: Conversation.id
  role: user | assistant
  content: string
  created_at: datetime             # strictly increasing, defines the chat order
```

Every user and assistant message belongs here, even when the planner decides an `AgentRun` is unnecessary. The interface keeps the original markdown, including fenced `vega-lite` blocks; planner memory strips them. Ownership (`user_id`) is checked once, in `ConversationService`, at the API boundary.

## Work context

```text
work_contexts (catalog)
  id, key, name, description, instructions
  agent_ids: Subagent.id[]
  enabled: boolean

conversation_work_contexts
  id: string
  conversation_id: Conversation.id
  work_context_id: WorkContext.id
  status: ACTIVE | PAUSED
  last_agent_run_id: AgentRun.id | null
  created_at, updated_at: datetime
```

A work context only selects which agents are eligible; it never restricts the chat. A conversation may have no active context, and that is a normal state (direct answers). At most one instance is `ACTIVE`; switching pauses the previous one, and returning to a catalog context reuses its existing instance.

## Agent run

```text
agent_runs
  id: string
  conversation_id: Conversation.id
  status: RUNNING | FINISHED | CANCELLED | FAILED   # NEW and PLANNING are reserved
  tasks: Task[]
  logs: AgentRunLog[]
  final_answer: FinalAnswerBlock[]
  triggering_message_id: Message.id
  conversation_work_context_id: ConversationWorkContext.id
  continued_human_input_request_id: HumanInputRequest.id | null
  agent_run_last_message: string
  created_at, finished_at: datetime | null
```

An `AgentRun` exists only when the planner decides the request needs tasks. A run leaves `RUNNING` exactly once: the transition is a conditional update, so a cancelled or failed run can never be resurrected by a task that finishes late. Failures and cancellations also write an assistant message to the chat.

```text
Task (embedded)
  id, description, subagent_selected
  status: NEW | RUNNING | FINISHED | CANCELLED | FAILED
  input, output: object
  validations: TaskValidation[]
  attempts, max_attempts: number
  created_at, finished_at

TaskValidation
  status: RETRY | CONTINUE | CANCEL | HITL
  feedback, message_to_user: string
  attempt: number
  validated_at: datetime

AgentRunLog:       at, level (INFO | ERROR), message, status, execution_status
FinalAnswerBlock:  type (communicator | subagent), title, markdown, task_id, subagent_id
```

The task selects the subagent only, never a tool. `CONTINUE` finishes a task; `RETRY` reruns the same subagent with validator feedback until `max_attempts`, then cancels the task; `CANCEL` cancels the run; `HITL` creates a human input request. Tasks run in parallel and are independent.

## Human input request

```text
human_input_requests
  id, conversation_id, agent_run_id, task_id
  question: string
  status: PENDING | ANSWERED | DISMISSED
  answer_message_id: Message.id | null
  created_at, answered_at
```

HITL is context, not a conversation or run status. A new message always returns to the planner, which can answer a pending request or start a new line of work.

## Artifacts

```text
artifacts
  id: string
  conversation_id: Conversation.id
  work_context_id: WorkContext.id | null     # where it was produced
  type: string                               # e.g. INSIGHT, BRIEFING
  version: number                            # per conversation + type
  status: ACTIVE | SUPERSEDED
  derived_from: Artifact.id[]
  title: string
  content: string                            # text; may reference visuals with [[visualization:id]] markers
  visuals: Visual[]                          # id, title, format (HTML | VEGA_LITE), payload
  subagent_id, created_by_run_id, created_by_task_id
  created_at: datetime
```

Artifacts belong to the conversation, not to a work context, so they survive a context switch.

- Text and visuals are stored apart and put together at runtime. `GET /conversations/{id}/artifacts` returns `content` (plain text, visuals shown as `[visual: title]` placeholders) and `rendered_content` (markdown with the visuals back in place, for display). Models only ever receive the plain text.
- A subagent declares `produces` / `consumes` artifact types in the catalog. When a task is approved (`CONTINUE`) and its subagent produces a type, the output is published as a new `ACTIVE` artifact and the previous active one of the same type becomes `SUPERSEDED`.
- Inside one run, ordering follows the catalog: a task whose subagent consumes a type waits for the tasks of other subagents that produce it, then builds on the artifact they just published. If a task it depends on is not approved, it is skipped (cancelled) instead of building on an older result.
- Before running, a subagent receives the latest active artifact of every type it consumes; their ids become `derived_from` of what it publishes.
- `stale` is never stored: an artifact is stale when any parent in `derived_from` is no longer active (`GET /conversations/{id}/artifacts`).
- The planner sees the recent artifacts (id, type, version, status, stale, title, summary) and can reuse them or ask to reactivate an earlier one by id. The backend only accepts ids of the same conversation.

## Agent catalog

```text
Subagent:  id, name, description, system_prompt, skill_ids, providers[], produces[], consumes[], enabled
Skill:     id, key, name, description, instructions, tool_ids, enabled
Tool:      id, provider_id, name, type, function_name, description, input_schema, output_schema, config, enabled
Provider:  id, name, category, external_agent_id, config, enabled
SubagentProvider (embedded): id, provider_id, config, enabled
```

At runtime the allowed tools are the union of tools of the subagent's enabled skills, intersected with its enabled provider configurations. This is enforced on the backend, not only described in prompts. MongoDB is the source of truth for the catalog. `resources/bootstrap` is only a first-run seed: a collection is filled while it is empty, and nothing there ever updates or recreates entries afterwards. To add or change an agent, skill, tool or work context, edit the collection.

## Execution and provider session

```text
task_executions
  id, conversation_id, agent_run_id, task_id, subagent_id
  messages: ExecutionMessage[]   # id, role, content, created_at
  tool_calls: ToolCall[]         # id, tool_id, provider_id, input, output (provider details, audit only), output_text, status, started_at, finished_at
  input, output: object
  status: string
  attempt: number
  started_at, finished_at

provider_sessions
  id, conversation_id, subagent_id, provider_id
  external_session_id, status, metadata
  created_at, updated_at
```

Provider sessions are reused 1:1 for `conversation + subagent + provider` (unique index, atomic upsert). Subagent memory is isolated: each subagent receives its own latest task input/output history, never the planner's shared memory.
````

## 2. `docs/implementation-summary.md` (recriar exatamente)

**Arquivo `docs/implementation-summary.md`** (literal):

````markdown
# Implementation Summary

## Current architecture

The Hub is a Java 21 and Spring Boot service with MongoDB as its only data store.

- Each aggregate has its own Mongo collection (`conversations`, `messages`, `agent_runs`, `task_executions`, `artifacts`, ...; see `data-model.md`). Run state is updated with atomic single-document operations, never by rewriting the conversation.
- The planner decides whether a message needs tasks. Simple requests can receive a direct assistant response without an `AgentRun`.
- `WorkContext` selects agents eligible for a particular mode of work. It is not a restriction on the user's conversation, and a conversation may have none active.
- `Artifact` is a typed, versioned result (for example an `INSIGHT`) published when a task is approved. Artifacts belong to the conversation; a new one supersedes the previous active one of its type.
- `AgentRun` records one asynchronous execution created by the planner.
- A task selects a subagent. Skills define which tools that subagent may use.
- Providers remain behind a tool interface. Genie is the initial provider implementation and supports persisted sessions plus polling.

## Provider tools and visual content

The core never depends on a provider. A `ProviderTool` returns a `ProviderToolResult`:

- `content`: the text the model reads, which may point to visuals with `[[visualization:id]]` markers;
- `visuals`: neutral tables or charts the tool built itself (`Visual`: id, title, format `HTML` or `VEGA_LITE`, payload). A provider without visual output simply returns none;
- `structuredOutput`: the provider's own details, kept for audit and never interpreted by the core;
- `providerSession`: the provider conversation to reuse, if it has one.

Adding Salesforce, HubSpot or any other source is a new `ProviderTool` that fills this contract; the executor, validator, artifacts and interface stay untouched. Visuals are stored apart from the text (task output, artifacts) and assembled only when needed: markdown with the visuals in place for people, and plain text with `[visual: title]` placeholders for models, so no HTML or chart spec ever reaches a prompt.

## Initial declarative catalog

The application seeds JSON catalog files on startup when `hub.bootstrap.enabled=true`:

- one `GENIE` provider;
- one shared `tool-enviar-mensagem-genie` tool;
- four skills: campaign measurement, campaign performance, customer profile, and customer segmentation;
- two existing subagents: `agent-mensuracao` and `agent-mapa-clientes`;
- one `ANALISE_DE_DADOS` work context containing both agents.

`agent-mensuracao` declares `produces: ["INSIGHT"]`. MongoDB is the source of truth: bootstrap is only a first-run seed, applied to a collection while it is empty and never again, so anything changed or removed in Mongo stays that way. A new agent is added by inserting it in the catalog collections, not by editing the JSON.

The catalog is intentionally data-driven. Adding a skill, associating an existing tool, or making an agent eligible for a work context is a Mongo configuration change. A new external provider still needs its Java adapter (a `ProviderTool`).

## Main flow

1. A client sends a user message with `Authorization: <user_id>`.
2. The API persists the message and returns `202 Accepted` with `conversation_id` and `message_id`.
3. A background worker (virtual threads) calls the planner with the previous messages, contexts, pending human input requests, recent artifacts, and the available agents for each context.
4. The planner either writes a direct assistant response (including clarifying questions), reactivates an earlier artifact, or creates an `AgentRun` with tasks.
5. Tasks run in parallel, except that a task whose subagent consumes an artifact type waits for the tasks of the same run that produce it (ordering comes from the catalog, never from the planner). The selected subagent only sees its own task execution memory and the artifacts it consumes, and has access only to tools granted by its skills.
6. The validator returns `CONTINUE`, `RETRY`, `CANCEL`, or `HITL`.
7. A completed run persists markdown blocks and mirrors them into the chat. Vega-Lite stays in the interface history but is removed from planner memory. A cancelled or failed run also tells the user in the chat, and a run can never leave a terminal status.

## HITL behavior

HITL produces a `HumanInputRequest`, not a blocking conversation status. The communicator includes pending questions in a partial answer. Later user messages always go to the planner, which can answer a pending request or start a new line of work.

## API contract

All endpoints require the raw user id in the `Authorization` header.

```text
POST /conversation
POST /conversations/{conversationId}
GET  /conversations
GET  /conversations/{conversationId}
GET  /conversation/{conversationId}/messages
GET  /conversations/{conversationId}/agent-runs?triggering_message_id={messageId}
GET  /conversations/{conversationId}/agent-runs/{agentRunId}
GET  /conversations/{conversationId}/artifacts
```

The POST endpoints accept `{ "message": "..." }` and return:

```json
{
  "status": "ACCEPTED",
  "conversation_id": "...",
  "message_id": "..."
}
```

The AgentRun polling endpoint returns execution status, readable logs, optional `final_answer` blocks, update time, and `source` (always `database`: polling reads the run document directly). Technical task outputs are not exposed there before finalization.

## Local run

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Open `frontend/index.html` in a browser or use the API directly. The service reads the provider/OpenAI variables from the same `.env` naming used by `jsHub`.

## Execution logs

Application logs are written to `logs/hubdeia.log`. Files rotate at 10 MB and the service retains 14 days of history. Genie failures include the HTTP status, provider response body, execution identifiers, and stack trace, without writing access tokens or client secrets.

## Verification

`./mvnw.cmd test` runs against the local MongoDB container, using the separate `appdb_test` database. It covers atomic run updates under concurrency, terminal run states, the orchestrator (approval, retry, cancel, HITL, failure), artifact lifecycle, planner decision handling and message ordering.
````

## 3. Ativos binários: `docs/hub-ia.png` e `docs/mermaid-diagram.png`

São duas imagens (PNG) que **não cabem em Markdown**. Copie-as à parte para a outra máquina, em `docs/`. Nenhum código nem documento as referencia.

| Arquivo | Tamanho | Dimensões | O que mostra |
|---|---|---|---|
| `docs/hub-ia.png` | 185.298 bytes | 1500 × 2242 px | Fluxograma desenhado à mão (estilo draw.io, linhas pretas sobre fundo branco) da jornada do "Usuário Agêntico": envia mensagem → há `conversation_id`? (não: checa/seleciona Jornada `journey_hint` e cria conversa; sim: há workflow aguardando resposta HITL?) → cria novo Workflow → agente planejador recebe a requisição e cria tasks (com *MongoDB sub-agents collection* e *Short-Term Memory Chat*) → seleciona tarefas prontas → envia input ao subagente + tool calling (com *Short-Term Memory Subagent–Task*) → agente validador (LLM-as-a-judge) → decisão Retry/Continue/Cancel/HITL (retry com limite = 3; cancelar task; cancelar workflow e abortar; HITL atualiza Workflow+Task como `WAITING_USER`, coleta o output do subagente e estrutura a pergunta) → todas as tasks concluídas? (não: continua o workflow) → agente comunicador consolida a resposta final → responde ao usuário e atualiza o estado do Workflow. |
| `docs/mermaid-diagram.png` | 692.275 bytes | 2332 × 4680 px | O **mesmo fluxo** renderizado a partir de um diagrama Mermaid, com tema escuro (nós azul-marinho sobre fundo branco). |

**Importante:** os dois diagramas descrevem um **desenho anterior** do sistema (vocabulário "Workflow", "Jornada/`journey_hint`", estado `WAITING_USER`), da época da implementação Node.js. O sistema **atual** usa "AgentRun", "WorkContext" e `HumanInputRequest` (não há estado `WAITING_USER`), conforme as specs 01 e 03 a 09. As imagens são mantidas no repositório como histórico; **não** as use como referência de comportamento.

## 4. `scripts/seed-subagents.js` (legado — NÃO recriar)

Script de `mongosh` com 85 linhas, do tempo do `js-hub`: faz *upsert* da coleção `journeys` (jornada `journey-insights`, `hint: 'INSIGHTS'`) e de dois agentes na coleção `subagents` (`agent-mensuracao` e `agent-mapa-clientes`) **no formato antigo**, com campos `journey`, `capabilities` e `providers[].tools_for_usage`/`provider_id: provider-genie-mensuracao|provider-genie-mapa-clientes`, que **não existem** no modelo atual (ver `03-dominio-e-mongodb.md`). Os mesmos agentes, no formato atual, estão nos JSONs de `04-catalogo-e-bootstrap.md`. O script **não faz parte do projeto Java** e não deve ser recriado nem executado contra o banco atual.

## 5. `js-hub/` (legado — NÃO recriar)

Pasta com **73 arquivos** (~13 mil linhas de JavaScript): uma implementação anterior, em Node.js, do mesmo Hub ("API Node.js para orquestrar jornadas, agentes e ferramentas, com chat via SSE", segundo o `README.md` dela). Tem `package.json`, `src/`, `data/` (inclui `workflows.json` e `subagents.json`), `scripts/` e documentação própria. O `.gitignore` a trata como *Legacy JS Hub* (ignora `js-hub/data/workflows/` e `js-hub/src/logs/`). **Está fora do escopo de replicação.** O único vínculo com o projeto Java é que `DotenvEnvironmentPostProcessor` lê `js-hub/.env` (se existir) para reaproveitar `OPENAI_API_KEY` e credenciais Genie (ver `02-projeto-build-e-infra.md`); na máquina nova basta um `.env` na raiz.

## Armadilhas e decisões não óbvias

1. Os dois `.md` de `docs/` são **parte do projeto** e vão literais; as imagens são só histórico e vão copiadas à parte.
2. Não importe nada de `js-hub/` nem de `scripts/` para o projeto Java: os formatos de dados são incompatíveis.
3. Os `.md` literais contêm exemplos de endpoints e do modelo que devem permanecer coerentes com a API real (`05-api-rest.md`).

## Critérios de aceite

- `docs/data-model.md` e `docs/implementation-summary.md` existem e são idênticos (byte a byte) aos blocos literais acima.
- `docs/hub-ia.png` e `docs/mermaid-diagram.png` foram copiados com os tamanhos indicados.
- Não existem `js-hub/` nem `scripts/` na cópia replicada (a menos que o dono decida levá-los por fora).

## Ordem de implementação sugerida

Criar os dois `.md` literais por último (depois de o sistema estar pronto) e conferir contra o comportamento real.
