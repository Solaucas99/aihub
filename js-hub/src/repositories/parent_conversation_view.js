const { WORKFLOW_STATUS, TASK_STATUS, REPLY_MODES } = require('../constants');
const { buildPayloadReference } = require('../services/workflow_result_service');

const ACTIVE_STATUSES = [
    WORKFLOW_STATUS.IN_PROGRESS,
    WORKFLOW_STATUS.WAITING_USER,
    WORKFLOW_STATUS.WAITING_APPROVAL,
    WORKFLOW_STATUS.RESUMING
];

function buildParentConversationView(parentConversationId, workflows = []) {
    const messages = [];
    const timelineEvents = [];
    let activeWorkflow = null;

    for (const workflow of workflows) {
        if (isActiveWorkflow(workflow)) activeWorkflow = workflow;
        messages.push(...workflowMessagesForView(workflow));
        timelineEvents.push(...timelineEventsForView(workflow));
    }

    const pendingAction = createPendingAction(activeWorkflow);
    return {
        parent_conversation_id: parentConversationId,
        active_workflow_id: activeWorkflow?.workflow_id || null,
        active_status: activeWorkflow?.status || null,
        active_workflow: activeWorkflowForView(activeWorkflow, pendingAction),
        pending_workflow: Boolean(activeWorkflow),
        pending_action: pendingAction,
        next_message: nextMessageInstructions(parentConversationId, activeWorkflow, pendingAction),
        messages: sortByCreatedAt(messages),
        timeline_events: sortByCreatedAt(timelineEvents),
        workflows: workflows.map(workflowSummary)
    };
}

function workflowMessagesForView(workflow) {
    const messages = persistedMessagesForView(workflow);
    const assistantMessages = workflow.messages?.filter(message => message.role === 'assistant') || [];

    if (shouldAddWaitingMessage(workflow, assistantMessages)) {
        messages.push(waitingMessageForView(workflow));
    }

    if (shouldAddFinalMessage(workflow, assistantMessages)) {
        messages.push(finalMessageForView(workflow));
    }

    return messages;
}

function persistedMessagesForView(workflow) {
    return (workflow.messages || []).map((message, index) => ({
        id: `${workflow.workflow_id}:${message.role}:${message.created_at || index}`,
        role: message.role,
        kind: message.kind || null,
        content: message.content,
        ...assistantPayloadReference(workflow, message),
        workflow_id: workflow.workflow_id,
        workflow_status: workflow.status,
        created_at: message.created_at || workflow.created_at
    }));
}

function shouldAddWaitingMessage(workflow, assistantMessages) {
    return workflow.status === WORKFLOW_STATUS.WAITING_USER
        && workflow.waiting_message
        && !assistantMessages.some(message => {
            return message.kind === 'user_input_required' || message.content === workflow.waiting_message;
        });
}

function waitingMessageForView(workflow) {
    return {
        id: `${workflow.workflow_id}:assistant:waiting`,
        role: 'assistant',
        kind: 'user_input_required',
        content: workflow.waiting_message,
        payload_ready: false,
        workflow_id: workflow.workflow_id,
        workflow_status: WORKFLOW_STATUS.WAITING_USER,
        created_at: workflow.updated_at
    };
}

function shouldAddFinalMessage(workflow, assistantMessages) {
    return finalResponseContent(workflow)
        && !assistantMessages.some(message => {
            return message.kind === 'final' || message.content === finalResponseContent(workflow);
        });
}

function finalMessageForView(workflow) {
    return {
        id: `${workflow.workflow_id}:assistant:final`,
        role: 'assistant',
        kind: 'final',
        content: finalResponseContent(workflow),
        ...buildPayloadReference({ workflow_id: workflow.workflow_id }, workflow.final_response_structured || {}),
        workflow_id: workflow.workflow_id,
        workflow_status: workflow.status,
        created_at: workflow.updated_at
    };
}

function assistantPayloadReference(workflow, message) {
    if (message.role !== 'assistant') return {};
    if (message.payload_ref || message.payload_ready !== undefined) {
        return {
            payload_ready: Boolean(message.payload_ready),
            payload_ref: message.payload_ref || null
        };
    }
    if (!['final', 'cancelled', 'failed', 'superseded', 'status'].includes(message.kind)) {
        return { payload_ready: false };
    }
    return buildPayloadReference({ workflow_id: workflow.workflow_id }, workflow.final_response_structured || {});
}

function finalResponseContent(workflow) {
    return workflow.final_response_structured?.content || workflow.final_response || null;
}

function timelineEventsForView(workflow) {
    return (workflow.timeline_events || []).map(event => ({
        ...event,
        workflow_id: event.workflow_id || workflow.workflow_id,
        parent_conversation_id: event.parent_conversation_id || workflow.parent_conversation_id,
        workflow_status: workflow.status
    }));
}

function createPendingAction(workflow) {
    if (workflow?.status !== WORKFLOW_STATUS.WAITING_USER) return null;
    return {
        type: 'USER_INPUT_REQUIRED',
        workflow_id: workflow.workflow_id,
        message: workflow.waiting_message || null,
        waiting_task_id: workflow.waiting_task_id || null,
        reply_modes: [
            REPLY_MODES.AUTO,
            REPLY_MODES.RESUME_WORKFLOW,
            REPLY_MODES.NEW_WORKFLOW,
            REPLY_MODES.CANCEL_WORKFLOW
        ]
    };
}

function activeWorkflowForView(workflow, pendingAction) {
    if (!workflow) return null;
    return {
        workflow_id: workflow.workflow_id,
        journey_type: workflow.journey_type,
        status: workflow.status,
        created_at: workflow.created_at,
        updated_at: workflow.updated_at,
        pending_action: pendingAction
    };
}

function nextMessageInstructions(parentConversationId, activeWorkflow, pendingAction) {
    return {
        parent_conversation_id: parentConversationId,
        workflow_id: pendingAction ? activeWorkflow.workflow_id : null,
        send_workflow_id: Boolean(pendingAction),
        reason: pendingAction
            ? 'Existe um workflow aguardando resposta do usuario.'
            : 'Nao existe workflow aguardando resposta; envie apenas o parent_conversation_id para manter o contexto.'
    };
}

function workflowSummary(workflow) {
    return {
        workflow_id: workflow.workflow_id,
        journey_type: workflow.journey_type,
        status: workflow.status,
        created_at: workflow.created_at,
        updated_at: workflow.updated_at,
        waiting_message: workflow.waiting_message || null,
        superseded_by_workflow_id: workflow.superseded_by_workflow_id || null,
        task_summary: taskSummary(workflow.tasks || [])
    };
}

function taskSummary(tasks) {
    return {
        total: tasks.length,
        completed: tasks.filter(task => task.status === TASK_STATUS.COMPLETED).length,
        waiting: tasks.filter(task => task.status === TASK_STATUS.WAITING_USER).length,
        failed: tasks.filter(task => task.status === TASK_STATUS.FAILED).length,
        cancelled: tasks.filter(task => task.status === TASK_STATUS.CANCELLED).length
    };
}

function isActiveWorkflow(workflow) {
    return ACTIVE_STATUSES.includes(workflow.status);
}

function sortByCreatedAt(items) {
    return items.sort((a, b) => new Date(a.created_at) - new Date(b.created_at));
}

module.exports = { buildParentConversationView };
