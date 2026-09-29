const { runAgent, structuredOutput } = require('../openai');
const { buildParentConversationMessages } = require('../memory/parent_conversation_memory');
const { getPublicSubAgents } = require('../subagents/registry');
const { emitAndStore } = require('../events/workflow_events');

const PLAN_FORMAT = structuredOutput('task_plan', {
    type: 'object', additionalProperties: false, required: ['tasks'],
    properties: {
        tasks: {
            type: 'array',
            items: {
                type: 'object', additionalProperties: false,
                required: ['task_id', 'task_description', 'enriched_content', 'agent_id', 'depends_on'],
                properties: {
                    task_id: { type: 'string' },
                    task_description: { type: 'string' },
                    enriched_content: { type: 'string' },
                    agent_id: { type: 'string' },
                    depends_on: { type: 'array', items: { type: 'string' } }
                }
            }
        }
    }
});

async function plannerAgent(message, emit = () => {}, context = {}) {
    await emitAndStore(emit, 'planner_start', { message }, context);
    const content = await runAgent({
        prompt: [
            'Planeje tarefas para responder a solicitacao atual do usuario em um CRM agentico.',
            'Consulte getAllSubAgents e escolha agentes pelas descricoes e capacidades de negocio.',
            'Cada agente recebe no maximo uma tarefa. Agrupe seus objetivos em enriched_content.',
            'Use depends_on somente se precisar do resultado de outro agente; tarefas independentes usam [].',
            'O historico serve de contexto. Planeje sempre a ultima solicitacao do usuario.',
            'Retorne o plano no formato JSON solicitado.'
        ].join('\n'),
        messages: [...buildParentConversationMessages(context.parentConversationHistory), { role: 'user', content: message }],
        tools: {
            getAllSubAgents: {
                schema: {
                    type: 'function',
                    function: {
                        name: 'getAllSubAgents', description: 'Lista os agentes permitidos nesta jornada.',
                        strict: true,
                        parameters: { type: 'object', properties: {}, required: [], additionalProperties: false }
                    }
                },
                execute: () => getPublicSubAgents(context.agents)
            }
        },
        responseFormat: PLAN_FORMAT,
        onRequest: phase => emitAndStore(emit, 'openai_request_' + phase, { agent_id: 'planner-agent', phase: 'planner' }, context)
    });
    const tasks = JSON.parse(content).tasks.map(task => ({ ...task, original_question: message }));
    await emitAndStore(emit, 'planner_done', { tasks }, context);
    return tasks;
}

module.exports = { plannerAgent };
