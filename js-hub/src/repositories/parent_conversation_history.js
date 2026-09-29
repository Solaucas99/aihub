function buildParentConversationHistory(workflows = [], { limit = 20 } = {}) {
    const messages = workflows.flatMap(workflow => workflowHistoryMessages(workflow));
    return messages
        .sort((a, b) => new Date(a.created_at) - new Date(b.created_at))
        .slice(-limit);
}

function workflowHistoryMessages(workflow) {
    const messages = (workflow.messages || []).map(message => ({
        role: message.role,
        content: message.content,
        workflow_id: workflow.workflow_id,
        workflow_status: workflow.status,
        created_at: message.created_at || workflow.created_at
    }));

    const hasAssistantMessage = messages.some(message => message.role === 'assistant');
    if (workflow.final_response && !hasAssistantMessage) {
        messages.push({
            role: 'assistant',
            content: workflow.final_response,
            workflow_id: workflow.workflow_id,
            workflow_status: workflow.status,
            created_at: workflow.updated_at
        });
    }

    return messages;
}

module.exports = { buildParentConversationHistory };
