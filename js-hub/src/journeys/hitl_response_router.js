const { runAgent, structuredOutput } = require('../openai');
const { REPLY_MODES, HITL_ACTIONS } = require('../constants');

const ACTION_BY_MODE = {
    [REPLY_MODES.RESUME_WORKFLOW]: HITL_ACTIONS.RESUME_PENDING_TASK,
    [REPLY_MODES.NEW_WORKFLOW]: HITL_ACTIONS.START_NEW_WORKFLOW_IN_PARENT,
    [REPLY_MODES.CANCEL_WORKFLOW]: HITL_ACTIONS.CANCEL_PENDING_WORKFLOW
};

async function routeHitlResponse({ workflow, userMessage, replyMode = REPLY_MODES.AUTO }) {
    if (replyMode && replyMode !== REPLY_MODES.AUTO) {
        const action = ACTION_BY_MODE[replyMode];
        if (!action) throw new Error('reply_mode invalido.');
        return { action, reason: 'Acao explicita do usuario.' };
    }
    const waitingTask = workflow.tasks.find(task => task.task_id === workflow.waiting_task_id);
    const content = await runAgent({
        prompt: [
            'Classifique a nova mensagem frente a pergunta pendente de uma conversa.',
            'RESUME_PENDING_TASK: responde a pendencia e mantem o objetivo.',
            'START_NEW_WORKFLOW_IN_PARENT: solicita outro objetivo.',
            'CANCEL_PENDING_WORKFLOW: pede cancelamento.',
            'Em caso de ambiguidade, prefira START_NEW_WORKFLOW_IN_PARENT.',
            'Retorne JSON.'
        ].join('\n'),
        messages: [{ role: 'user', content: JSON.stringify({
            waiting_message: workflow.waiting_message,
            task: waitingTask?.task_description,
            original_question: waitingTask?.original_question,
            user_message: userMessage
        }) }],
        responseFormat: structuredOutput('hitl_action', {
            type: 'object', additionalProperties: false, required: ['action', 'reason'],
            properties: {
                action: { type: 'string', enum: Object.values(HITL_ACTIONS) },
                reason: { type: 'string' }
            }
        })
    });
    return JSON.parse(content);
}

module.exports = { routeHitlResponse };
