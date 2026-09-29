const { formatParentConversationHistory } = require('../memory/parent_conversation_memory');

function buildSubAgentPrompt(agent) {
    return [
        agent.prompt,
        'Quando uma tool retornar JSON estruturado com blocks/artifacts, preserve os fatos e referencie graficos/queries sem inventar novos artifacts.',
        'A resposta final textual deve continuar clara para o usuario mesmo quando houver artifacts.'
    ].join('\n');
}

function buildSubAgentMessages(task, context) {
    return [
        ...(task.agent_messages || []),
        { role: 'user', content: buildTaskMessage(task, context) }
    ];
}

function buildTaskMessage(task, context) {
    return [
        task.agent_messages?.length ? '' : formatParentConversationHistory(context.parentConversationHistory),
        `Objetivo da tarefa: ${task.enriched_content}`,
        context.resumeTaskId === task.task_id ? `Resposta do usuario: ${context.resumeMessage}` : '',
        context.retryFeedback ? `Ajuste necessario na nova tentativa: ${context.retryFeedback}` : '',
        context.dependencyResults?.length ? `Resultados das tarefas anteriores: ${JSON.stringify(context.dependencyResults)}` : ''
    ].filter(Boolean).join('\n\n');
}

module.exports = {
    buildSubAgentPrompt,
    buildSubAgentMessages
};
