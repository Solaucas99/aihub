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
