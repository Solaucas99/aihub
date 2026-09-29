const { technicalEventToTimeline, createTimelineEvent } = require('./ui_events');

async function emitAndStore(emit, event, payload = {}, context = {}) {
    const enrichedPayload = context.workflow
        ? { workflow_id: context.workflow.workflow_id, ...payload }
        : payload;

    if (context.workflowRepository && context.workflow) {
        await context.workflowRepository.appendEvent(context.workflow.workflow_id, event, enrichedPayload);
    }

    const timelineEvent = technicalEventToTimeline(event, enrichedPayload, context);
    if (!timelineEvent) return;

    emit('timeline_event', timelineEvent);

    if (context.workflowRepository && context.workflow) {
        await context.workflowRepository.appendTimelineEvent(context.workflow.workflow_id, timelineEvent);
    }
}

async function emitProviderTimeline(emit, payload = {}, context = {}) {
    if (!isUiFriendlyTimeline(payload)) return;
    const timelineEvent = createTimelineEvent({
        workflow: context.workflow,
        type: payload.type || 'AGENT_PROGRESS',
        status: payload.status,
        title: payload.title,
        description: payload.description || '',
        agentId: payload.agent_id,
        taskId: payload.task_id
    });

    emit('timeline_event', timelineEvent);

    if (context.workflowRepository && context.workflow) {
        await context.workflowRepository.appendTimelineEvent(context.workflow.workflow_id, timelineEvent);
    }
}

function isUiFriendlyTimeline(payload = {}) {
    return Boolean(payload.title && ['RUNNING', 'COMPLETED', 'WAITING', 'FAILED', 'ATTENTION'].includes(payload.status));
}

module.exports = {
    emitAndStore,
    emitProviderTimeline
};
