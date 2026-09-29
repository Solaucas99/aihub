const { WORKFLOW_STATUS } = require('../constants');
const { messageFromResult, assistantMessageKind } = require('./user_facing_messages');
const { buildPayloadReference } = require('../services/workflow_result_service');
const { emitAndStore } = require('../events/workflow_events');
const { communicatorAgent } = require('../agents/communicator_agent');
const { buildCommunicationDetails } = require('./communication_context');
const { buildStructuredCommunicationResponse } = require('./communication_response_builder');

async function communicateWorkflowResult(result, emit = () => {}, context = {}, details = {}) {
    if (result.communicated) return result;
    const { workflow, workflowRepository } = context;
    await emitAndStore(emit, 'communicating', {
        status: result.status, task_id: result.task_id, agent_id: result.agent_id
    }, context);

    const message = await createUserMessage(result, context, details);
    const structuredResponse = buildStructuredCommunicationResponse(message, details);
    const communicated = {
        ...result,
        message,
        message_to_user: message,
        structured_response: structuredResponse,
        communicated: true
    };

    if (workflowRepository && workflow?.workflow_id) {
        await persistAssistantTurn(workflowRepository, workflow, result, message, structuredResponse);
    }

    return communicated;
}

async function createUserMessage(result, context, details) {
    try {
        const communicate = context.communicate || communicatorAgent;
        const message = await communicate({
            workflow_status: result.status,
            journey_type: context.workflow?.journey_type,
            original_user_message: context.workflow?.messages?.find(item => item.role === 'user')?.content,
            latest_user_message: context.workflow?.messages?.findLast(item => item.role === 'user')?.content,
            result,
            details: buildCommunicationDetails(details)
        });
        if (typeof message !== 'string' || !message.trim()) {
            throw new Error('Comunicador retornou resposta vazia.');
        }
        return stripTechnicalStatusPrefix(message);
    } catch (error) {
        console.error('[Communicator] Fallback:', error.message);
        return messageFromResult(result);
    }
}

function persistAssistantTurn(workflowRepository, workflow, result, message, structuredResponse) {
    const waiting = result.status === WORKFLOW_STATUS.WAITING_USER;
    return workflowRepository.finishTurn(workflow.workflow_id, result.status, {
        ...(waiting
            ? { waiting_task_id: result.task_id, waiting_message: message }
            : {
                waiting_task_id: null,
                waiting_message: null,
                final_response_structured: structuredResponse
            })
    }, lightweightAssistantMessage(result, message, structuredResponse, { payloadReady: !waiting }));
}

function lightweightAssistantMessage(result, message, structuredResponse, { payloadReady }) {
    return {
        role: 'assistant',
        kind: assistantMessageKind(result.status),
        content: message,
        ...(payloadReady ? buildPayloadReference(result, structuredResponse) : { payload_ready: false })
    };
}

function stripTechnicalStatusPrefix(message) {
    return String(message || '').replace(/^\s*(COMPLETED|FAILED|CANCELLED|WAITING_USER|WAITING_APPROVAL|RESUMING)\s*\n+/i, '').trim();
}

module.exports = { communicateWorkflowResult };
