const { runWithExecutionLog } = require('../logger');
const { routeHitlResponse } = require('../journeys/hitl_response_router');
const { WORKFLOW_STATUS, HITL_ACTIONS } = require('../constants');
const { createTimelineEvent } = require('../events/ui_events');
const { createChatTurnPresenter } = require('./chat_turn_presenter');

const DEFAULT_MESSAGES = {
    followup: 'Vou abrir uma nova solicitacao dentro desta conversa.',
    superseded: 'Vou tratar sua nova pergunta como uma nova solicitacao, mantendo o contexto da conversa.',
    resume: 'Sua mensagem responde a pergunta pendente. Vou continuar a solicitacao.',
    newRequest: 'Sua mensagem parece uma nova solicitacao. Vou continuar no mesmo contexto.'
};

function journeyMessages(journey) {
    return { ...DEFAULT_MESSAGES, ...journey.messages };
}

function createChatTurnService({ workflowRepository, journeys, routeJourney }) {
    const sendTurnResult = createChatTurnPresenter({ workflowRepository, journeys });

    async function processChatTurn(request) {
        const context = { ...request, workflowRepository };
        console.log(`Mensagem recebida: ${context.message}`);
        await runWithExecutionLog(context.message, () => executeChatTurn(context));
    }

    async function executeChatTurn(context) {
        await resolvePendingWorkflow(context);

        const existingWorkflow = context.workflowId
            ? await context.workflowRepository.requireById(context.workflowId)
            : null;
        const journey = resolveJourney(context, existingWorkflow);
        const result = existingWorkflow
            ? await continueFromWorkflow(context, existingWorkflow, journey)
            : await startWorkflow(context, journey, context.parentConversationId);

        await sendTurnResult(context, result);
    }

    function resolveJourney(context, workflow = null) {
        return journeys.get(routeJourney({
            journeyHint: context.journeyHint || workflow?.journey_type
        }));
    }

    async function resolvePendingWorkflow(context) {
        if (context.workflowId || !context.parentConversationId) return;
        const conversation = await context.workflowRepository.getParentConversationView(context.parentConversationId);
        context.workflowId = conversation.pending_action?.workflow_id || null;
    }

    async function continueFromWorkflow(context, workflow, journey) {
        if (workflow.status === WORKFLOW_STATUS.WAITING_USER) {
            return continueWaitingWorkflow(context, workflow, journey);
        }

        await sendTimelineEvent(context, workflow, {
            type: 'FOLLOWUP_STARTED',
            status: 'RUNNING',
            title: 'Continuando no mesmo contexto',
            description: journeyMessages(journey).followup
        });

        return startWorkflow(context, journey, workflow.parent_conversation_id);
    }

    async function continueWaitingWorkflow(context, workflow, journey) {
        const hitlRoute = await routeHitlResponse({
            workflow,
            userMessage: context.message,
            replyMode: context.replyMode
        });

        await announceHitlRoute(context, workflow, hitlRoute, journey);

        if (hitlRoute.action === HITL_ACTIONS.RESUME_PENDING_TASK) {
            return journeys.get(workflow.journey_type).resume({
                workflowId: workflow.workflow_id,
                message: context.message,
                emit: context.emit
            });
        }

        if (hitlRoute.action === HITL_ACTIONS.CANCEL_PENDING_WORKFLOW) {
            return cancelWaitingWorkflow(context, workflow, hitlRoute.reason);
        }

        await sendTimelineEvent(context, workflow, {
            type: 'WORKFLOW_SUPERSEDED',
            status: 'COMPLETED',
            title: 'Fechei a solicitacao anterior',
            description: journeyMessages(journey).superseded
        });

        return journey.start({
            message: context.message,
            parentConversationId: workflow.parent_conversation_id,
            emit: context.emit,
            supersededWorkflowId: workflow.workflow_id,
            supersededReason: hitlRoute.reason
        });
    }

    async function announceHitlRoute(context, workflow, hitlRoute, journey) {
        await sendTimelineEvent(context, workflow, {
            type: 'USER_REPLY_CLASSIFIED',
            status: 'COMPLETED',
            title: 'Entendi como seguir',
            description: hitlRoute.action === HITL_ACTIONS.RESUME_PENDING_TASK
                ? journeyMessages(journey).resume
                : journeyMessages(journey).newRequest
        });
    }

    function startWorkflow(context, journey, parentConversationId) {
        return journey.start({
            message: context.message,
            parentConversationId,
            emit: context.emit
        });
    }

    async function cancelWaitingWorkflow(context, workflow, reason) {
        const cancelledWorkflow = await context.workflowRepository.updateStatus(workflow.workflow_id, WORKFLOW_STATUS.CANCELLED, {
            cancellation_reason: reason,
            waiting_task_id: null,
            waiting_message: null,
            parent_conversation_id: workflow.parent_conversation_id,
            workflow_id: workflow.workflow_id,
            message_to_user: 'Solicitacao cancelada.'
        });
        return {
            status: WORKFLOW_STATUS.CANCELLED,
            workflow_id: cancelledWorkflow.workflow_id,
            parent_conversation_id: cancelledWorkflow.parent_conversation_id,
            cancellation_reason: cancelledWorkflow.cancellation_reason,
            message_to_user: cancelledWorkflow.message_to_user
        };
    }

    async function sendTimelineEvent(context, workflow, timelineData) {
        const event = createTimelineEvent({ workflow, ...timelineData });
        context.emit('timeline_event', event);

        if (!workflow?.workflow_id) return event;

        try {
            await context.workflowRepository.appendTimelineEvent(workflow.workflow_id, event);
        } catch (error) {
            console.error('[Hub] Falha ao persistir evento de timeline:', error.message);
        }

        return event;
    }

    return processChatTurn;
}

module.exports = { createChatTurnService };
