# Data Model

## Main conversation

```text
main_conversation_chat_history
  id: string
  user_id: string
  chat_last_message: string
  messages: Message[]
  agent_runs: AgentRun[]
  active_work_context_id: WorkContext.id | null
  work_contexts: ConversationWorkContext[]
  human_input_requests: HumanInputRequest[]
  subagents_executions: SubagentExecution[]
  provider_sessions: ProviderSession[]
  created_at: datetime
  updated_at: datetime
```

`MainConversationChatHistory` is the source of truth for the chat. Every user and assistant message belongs here, even when the planner decides an `AgentRun` is unnecessary.

## Message

```text
Message
  id: string
  role: user | assistant
  content: string
  created_at: datetime
```

The interface keeps the original markdown, including fenced `vega-lite` blocks. Planner memory strips those blocks before sending chat history to the planner model.

## Work context

```text
WorkContext
  id: string
  key: string
  name: string
  description: string
  instructions: string
  agent_ids: Subagent.id[]
  enabled: boolean

ConversationWorkContext
  id: string
  work_context_id: WorkContext.id
  status: ACTIVE | PAUSED
  last_agent_run_id: AgentRun.id | null
  created_at: datetime
  updated_at: datetime
```

Only one work context is active for a conversation. The planner may activate, retain, or reactivate a context; older contexts remain paused and recoverable. A work context selects eligible agents, but does not constrain the user's chat.

## Agent run

```text
AgentRun
  id: string
  status: NEW | PLANNING | RUNNING | FINISHED | CANCELLED | FAILED
  tasks: Task[]
  logs: AgentRunLog[]
  final_answer: FinalAnswerBlock[]
  triggering_message_id: Message.id
  conversation_work_context_id: ConversationWorkContext.id
  continued_human_input_request_id: HumanInputRequest.id | null
  agent_run_last_message: string
  created_at: datetime
  finished_at: datetime | null
```

An `AgentRun` is optional. It exists only when the planner decides that the user request needs one or more tasks.

```text
AgentRunLog
  at: datetime
  level: INFO | ERROR
  message: string
  status: AgentRunStatus
  execution_status: string

FinalAnswerBlock
  type: communicator | subagent
  title: string
  markdown: string
  task_id: Task.id | null
  subagent_id: Subagent.id | null
```

## Task and validation

```text
Task
  id: string
  description: string
  subagent_selected: Subagent.id
  status: NEW | RUNNING | FINISHED | CANCELLED | FAILED
  input: object
  output: object | null
  validations: TaskValidation[]
  attempts: number
  max_attempts: number
  created_at: datetime
  finished_at: datetime | null

TaskValidation
  status: RETRY | CONTINUE | CANCEL | HITL
  feedback: string
  message_to_user: string | null
  attempt: number
  validated_at: datetime
```

The task selects the subagent only. It never selects a tool. A `CONTINUE` validation finishes a task; `RETRY` reruns the same subagent with validator feedback; `CANCEL` cancels the run; and `HITL` creates a human input request.

## Human input request

```text
HumanInputRequest
  id: string
  agent_run_id: AgentRun.id
  task_id: Task.id
  question: string
  status: PENDING | ANSWERED | DISMISSED
  answer_message_id: Message.id | null
  created_at: datetime
  answered_at: datetime | null
```

HITL is context, not a conversation or agent-run status. A new message always returns to the planner, which can decide whether it answers a pending request or begins a new intent.

## Agent catalog

```text
Subagent
  id: string
  name: string
  description: string
  system_prompt: string
  skill_ids: Skill.id[]
  providers: SubagentProvider[]
  enabled: boolean

Skill
  id: string
  key: string
  name: string
  description: string
  instructions: string
  tool_ids: Tool.id[]
  enabled: boolean

Tool
  id: string
  provider_id: Provider.id
  name: string
  type: string
  function_name: string
  description: string
  input_schema: object
  output_schema: object
  config: object
  enabled: boolean

Provider
  id: string
  name: string
  category: string
  external_agent_id: string
  config: object
  enabled: boolean

SubagentProvider
  id: string
  provider_id: Provider.id
  config: object
  enabled: boolean
```

At runtime the allowed tools are calculated as the union of tools belonging to the subagent's enabled skills, intersected with its enabled provider configurations. This is enforced on the backend, not only described in prompts.

## Execution and provider session

```text
SubagentExecution
  id: string
  conversation_id: MainConversationChatHistory.id
  agent_run_id: AgentRun.id
  task_id: Task.id
  subagent_id: Subagent.id
  messages: Message[]
  provider_sessions: ProviderSession[]
  tool_calls: ToolCall[]
  input: object
  output: object
  status: string
  attempt: number
  started_at: datetime
  finished_at: datetime

ProviderSession
  id: string
  subagent_id: Subagent.id
  provider_id: Provider.id
  external_session_id: string
  status: string
  metadata: object
  created_at: datetime
  updated_at: datetime

ToolCall
  id: string
  tool_id: Tool.id
  provider_id: Provider.id
  input: object
  output: object
  status: string
  started_at: datetime
  finished_at: datetime
```

Provider sessions are canonical at conversation level and are reused 1:1 for `conversation + subagent + provider`. Subagent memory is isolated: each subagent receives its own latest task input/output history, never the planner's shared memory.
