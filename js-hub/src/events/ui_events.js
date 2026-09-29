const { randomUUID } = require('crypto');
const { WORKFLOW_STATUS } = require('../constants');
const { getSubAgentDisplayName } = require('../subagents/registry');

const AGENT_NAMES = {
    'planner-agent': 'Planejador',
    'validator-agent': 'Validador',
    'communicator-agent': 'Comunicador'
};

function agentName(agentId) {
    return AGENT_NAMES[agentId] || getSubAgentDisplayName(agentId) || agentId || 'Hub';
}

function createTimelineEvent({ workflow, type, status, title, description, agentId = null, taskId = null }) {
    return {
        id: randomUUID(),
        workflow_id: workflow?.workflow_id || null,
        parent_conversation_id: workflow?.parent_conversation_id || null,
        type,
        status,
        title,
        description,
        agent_id: agentId,
        agent_name: agentName(agentId),
        task_id: taskId,
        created_at: new Date().toISOString()
    };
}

function createWorkflowState(workflow, status, message = null) {
    return {
        workflow_id: workflow?.workflow_id || null,
        parent_conversation_id: workflow?.parent_conversation_id || null,
        journey_type: workflow?.journey_type || null,
        status,
        message,
        created_at: new Date().toISOString()
    };
}

function technicalEventToTimeline(event, payload = {}, context = {}) {
    const workflow = context.workflow;

    switch (event) {
        case 'planner_start':
            return createTimelineEvent({
                workflow,
                type: 'PLANNING_STARTED',
                status: 'RUNNING',
                title: 'Entendendo sua pergunta',
                description: 'O Hub esta planejando quais agentes devem ajudar.'
            });
        case 'planner_done':
            return createTimelineEvent({
                workflow,
                type: 'PLANNING_COMPLETED',
                status: 'COMPLETED',
                title: 'Plano de analise pronto',
                description: `O Hub organizou ${payload.tasks?.length || 0} tarefa(s) para responder.`
            });
        case 'task_start':
            return createTimelineEvent({
                workflow,
                type: 'AGENT_STARTED',
                status: 'RUNNING',
                title: `${agentName(payload.agent_id)} entrou na analise`,
                description: payload.task_description || 'Subagente iniciando a tarefa.',
                agentId: payload.agent_id,
                taskId: payload.task_id
            });
        case 'task_attempt_start':
            return createTimelineEvent({
                workflow,
                type: 'AGENT_WORKING',
                status: 'RUNNING',
                title: `${agentName(payload.agent_id)} esta trabalhando`,
                description: payload.max_attempts > 1
                    ? `Tentativa ${payload.attempt} de ${payload.max_attempts}.`
                    : 'Analisando a solicitacao.',
                agentId: payload.agent_id,
                taskId: payload.task_id
            });
        case 'task_tool_call':
            return createTimelineEvent({
                workflow,
                type: 'DATA_SOURCE_STARTED',
                status: 'RUNNING',
                title: `${agentName(payload.agent_id)} esta consultando dados`,
                description: 'O subagente esta acessando uma fonte de dados para buscar evidencias.',
                agentId: payload.agent_id,
                taskId: payload.task_id
            });
        case 'task_retrying':
            return createTimelineEvent({
                workflow,
                type: 'AGENT_RETRYING',
                status: 'RUNNING',
                title: `${agentName(payload.agent_id)} esta tentando novamente`,
                description: `A resposta anterior nao foi suficiente. Iniciando tentativa ${payload.next_attempt} de ${payload.max_attempts}.`,
                agentId: payload.agent_id,
                taskId: payload.task_id
            });
        case 'task_validated':
            return createTimelineEvent({
                workflow,
                type: 'VALIDATION_COMPLETED',
                status: payload.status === 'COMPLETED' ? 'COMPLETED' : 'ATTENTION',
                title: payload.status === 'COMPLETED' ? 'Resposta do agente validada' : 'Resposta precisa de atencao',
                description: payload.status === 'COMPLETED'
                    ? 'O Hub verificou que a resposta atende a tarefa.'
                    : 'O Hub verificou que ainda ha algo a resolver antes de concluir.',
                taskId: payload.task_id
            });
        case 'waiting_user':
        case 'workflow_paused':
            return createTimelineEvent({
                workflow,
                type: 'WAITING_USER',
                status: 'WAITING',
                title: 'Preciso de uma informacao sua',
                description: payload.message_to_user || 'O Hub precisa de um complemento para continuar.',
                agentId: payload.agent_id,
                taskId: payload.task_id
            });
        case 'communicating':
            return createTimelineEvent({
                workflow,
                type: 'COMMUNICATION_STARTED',
                status: 'RUNNING',
                title: 'Preparando mensagem',
                description: 'O Hub esta transformando os achados dos agentes em uma resposta clara.'
            });
        case 'workflow_completed':
            return createTimelineEvent({
                workflow,
                type: 'WORKFLOW_COMPLETED',
                status: 'COMPLETED',
                title: 'Resposta final pronta',
                description: 'A analise foi concluida.'
            });
        case 'workflow_failed':
            return createTimelineEvent({
                workflow,
                type: 'WORKFLOW_FAILED',
                status: 'FAILED',
                title: 'Nao consegui concluir esta solicitacao',
                description: payload.message_to_user || 'A execucao falhou apos novas tentativas.'
            });
        case 'workflow_cancelled':
            return createTimelineEvent({
                workflow,
                type: 'WORKFLOW_CANCELLED',
                status: 'CANCELLED',
                title: 'Solicitacao cancelada',
                description: payload.message_to_user || payload.reason || 'O workflow foi cancelado.'
            });
        default:
            return null;
    }
}

function isTerminalStatus(status) {
    return [
        WORKFLOW_STATUS.COMPLETED,
        WORKFLOW_STATUS.WAITING_USER,
        WORKFLOW_STATUS.CANCELLED,
        WORKFLOW_STATUS.FAILED,
        WORKFLOW_STATUS.SUPERSEDED
    ].includes(status);
}

module.exports = {
    createTimelineEvent,
    createWorkflowState,
    technicalEventToTimeline,
    agentName,
    isTerminalStatus
};
