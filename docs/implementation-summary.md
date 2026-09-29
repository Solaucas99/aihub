# Implementation Summary

## Current architecture

The Hub is a Java 21 and Spring Boot service with MongoDB as the persistent source of truth and Redis as a cache for polling progress.

- `MainConversationChatHistory` is the main chat and persistent shared history.
- The planner decides whether a message needs tasks. Simple requests can receive a direct assistant response without an `AgentRun`.
- `WorkContext` selects agents eligible for a particular mode of work. It is not a restriction on the user's conversation.
- `AgentRun` records one asynchronous execution created by the planner.
- A task selects a subagent. Skills define which tools that subagent may use.
- Providers remain behind a tool interface. Genie is the initial provider implementation and supports persisted sessions plus polling.

## Initial declarative catalog

The application seeds JSON catalog files on startup when `hub.bootstrap.enabled=true`:

- one `GENIE` provider;
- one shared `enviarMensagemGenie` tool;
- four skills: campaign measurement, campaign performance, customer profile, and customer segmentation;
- two existing subagents: `agent-mensuracao` and `agent-mapa-clientes`;
- one `ANALISE_DE_DADOS` work context containing both agents.

The catalog is intentionally data-driven. Adding a skill, associating an existing tool, or making an agent eligible for a work context is a Mongo configuration change. A new external provider still needs its Java adapter.

## Main flow

1. A client sends a user message with `Authorization: <user_id>`.
2. The API persists the message and returns `202 Accepted` with `conversation_id` and `message_id`.
3. A background worker calls the planner with the main chat history, contexts, pending human input requests, and the available agents for each context.
4. The planner either writes a direct assistant response or creates an `AgentRun` with tasks.
5. New tasks run in parallel. The selected subagent only sees its own task execution memory and has access only to tools granted by its skills.
6. The validator returns `CONTINUE`, `RETRY`, `CANCEL`, or `HITL`.
7. A completed run persists markdown blocks and mirrors assistant-safe text into the main chat. Vega-Lite stays in the interface history but is removed from planner memory.

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
```

The POST endpoints accept `{ "message": "..." }` and return:

```json
{
  "status": "ACCEPTED",
  "conversation_id": "...",
  "message_id": "..."
}
```

The AgentRun polling endpoint returns execution status, readable logs, optional `final_answer` blocks, update time, and its source (`cache` or `database`). Technical task outputs are not exposed there before finalization.

## Local run

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Open `frontend/index.html` in a browser or use the API directly. The service reads the provider/OpenAI variables from the same `.env` naming used by `jsHub`.

## Execution logs

Application logs are written to `logs/hubdeia.log`. Files rotate at 10 MB and the service retains 14 days of history. Genie failures include the HTTP status, provider response body, execution identifiers, and stack trace, without writing access tokens or client secrets.

## Verification

`./mvnw.cmd test` loads the full Spring application context against the local MongoDB and Redis containers.
