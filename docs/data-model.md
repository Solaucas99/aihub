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
