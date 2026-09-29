const { randomUUID } = require('crypto');
const { WORKFLOW_STATUS } = require('../constants');

function createWorkflow({ parentConversationId = null, journeyType, message }) {
    const now = new Date().toISOString();
    const workflowId = randomUUID();
    return {
        workflow_id: workflowId,
        parent_conversation_id: parentConversationId || randomUUID(),
        journey_type: journeyType,
        status: WORKFLOW_STATUS.IN_PROGRESS,
        current_step: null,
        created_at: now,
        updated_at: now,
        messages: message ? [{ role: 'user', content: message, created_at: now }] : [],
        tasks: [],
        subagent_sessions: {},
        events: [],
        timeline_events: []
    };
}

module.exports = { createWorkflow };
