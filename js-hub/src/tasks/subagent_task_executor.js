const { runAgent } = require('../openai');
const { bindAgentTools } = require('../providers');
const { findSubAgentConfig } = require('../subagents/registry');
const { emitAndStore, emitProviderTimeline } = require('../events/workflow_events');
const { normalizeAgentOutput, mergeStructuredOutputs } = require('../structured_output');
const { buildSubAgentMessages, buildSubAgentPrompt } = require('./subagent_task_messages');

async function executeSubAgentTask(task, emit = () => {}, context = {}) {
    const agent = resolveAgent(task, context);
    const { workflow, workflowRepository } = context;
    const tools = bindToolsForTask(agent, task, emit, context);
    const content = await runAgent({
        prompt: buildSubAgentPrompt(agent),
        model: process.env.OPENAI_SUBAGENT_MODEL || process.env.OPENAI_MODEL || 'gpt-5',
        messages: buildSubAgentMessages(task, context),
        tools,
        onRequest: phase => emitAndStore(emit, `openai_request_${phase}`, {
            agent_id: agent.id, task_id: task.task_id, phase: 'subagent'
        }, context),
        onMessages: async history => {
            task.agent_messages = history;
            if (workflowRepository && workflow) {
                await workflowRepository.updateTask(workflow.workflow_id, task.task_id, { agent_messages: history });
            }
        }
    });

    return taskExecutionPayload(task, content, context);
}

function resolveAgent(task, context) {
    const agent = context.agents
        ? context.agents.find(item => item.id === task.agent_id)
        : findSubAgentConfig(task.agent_id);
    if (!agent) throw new Error(`Subagente nao permitido ou inexistente: ${task.agent_id}`);
    return agent;
}

function bindToolsForTask(agent, task, emit, context) {
    const { workflow, workflowRepository } = context;
    const sessions = context.providerSessions ||= {};
    return bindAgentTools(agent, {
        sessions,
        saveSession: (agentId, session) => workflowRepository?.upsertSubagentSession(workflow.workflow_id, agentId, session),
        report: payload => emitProviderTimeline(emit, { ...payload, agent_id: agent.id, task_id: task.task_id }, context),
        onToolCall: tool => emitAndStore(emit, 'task_tool_call', { task_id: task.task_id, agent_id: agent.id, tool }, context)
    });
}

function taskExecutionPayload(task, content, context) {
    const structuredOutput = buildStructuredTaskOutput(content, task.agent_messages || []);
    return {
        task_description: task.task_description,
        enriched_content: task.enriched_content,
        original_question: task.original_question,
        user_clarification: context.resumeTaskId === task.task_id ? context.resumeMessage : null,
        sub_agent_output: { content, structured: structuredOutput, status: 'COMPLETED', error: {} }
    };
}

function buildStructuredTaskOutput(content, history = []) {
    const toolOutputs = history
        .filter(message => message.role === 'tool')
        .map(message => normalizeAgentOutput(message.content))
        .filter(output => output.blocks.length || output.content);

    if (!toolOutputs.length) return normalizeAgentOutput(content);
    if (toolOutputs.length === 1) return { ...toolOutputs[0], content: content || toolOutputs[0].content };

    const merged = mergeStructuredOutputs(toolOutputs);
    return { ...merged, content: content || merged.content };
}

module.exports = { executeSubAgentTask };
