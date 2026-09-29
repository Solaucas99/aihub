const { WORKFLOW_STATUS } = require('../constants');
const { createTimelineEvent, createWorkflowState } = require('../events/ui_events');
const { communicateWorkflowResult } = require('../communication/workflow_communication');

/** Operacoes comuns a jornadas de tarefas e jornadas personalizadas. */
function createWorkflowLifecycle(repository) {
    async function create({ journeyType, message, parentConversationId = null, supersededWorkflowId, supersededReason }) {
        const sessions = parentConversationId ? await repository.getLatestSubagentSessions(parentConversationId) : {};
        const workflow = await repository.create({ message, parentConversationId, journeyType });
        if (supersededWorkflowId) {
            await repository.updateStatus(supersededWorkflowId, WORKFLOW_STATUS.SUPERSEDED, {
                superseded_by_workflow_id: workflow.workflow_id, superseded_reason: supersededReason,
                waiting_task_id: null, waiting_message: null
            });
        }
        await repository.updateStatus(workflow.workflow_id, workflow.status, { subagent_sessions: sessions });
        workflow.subagent_sessions = sessions;
        return workflow;
    }

    async function announce(workflow, emit, resuming = false) {
        const title = resuming ? 'Continuando a solicitacao' : 'Comecei a trabalhar na sua solicitacao';
        emit('workflow_state', createWorkflowState(workflow, workflow.status, title));
        const event = createTimelineEvent({
            workflow, type: resuming ? 'WORKFLOW_RESUMING' : 'WORKFLOW_STARTED',
            status: 'RUNNING', title, description: title
        });
        await repository.appendTimelineEvent(workflow.workflow_id, event);
        emit('timeline_event', event);
    }

    function communicate(result, emit, context, details = {}) {
        return communicateWorkflowResult(result, emit, { ...context, workflowRepository: repository }, details);
    }

    return { create, announce, communicate };
}

module.exports = { createWorkflowLifecycle };
