const { WORKFLOW_STATUS } = require('../constants');
const { buildAssistantMessagePayload } = require('../communication/user_facing_messages');
const { communicateWorkflowResult } = require('../communication/workflow_communication');

function createChatTurnPresenter({ workflowRepository, journeys }) {
    async function sendTurnResult(context, result) {
        if (result.status === WORKFLOW_STATUS.COMPLETED) {
            console.log('[Hub] Fluxo concluido com sucesso.');
            await sendAssistantMessage(context, result);
            sendWorkflowState(context, result, 'Resposta pronta.');
            return;
        }

        if (result.status === WORKFLOW_STATUS.WAITING_USER) {
            console.log('[Hub] Fluxo pausado aguardando usuario.');
            await sendAssistantMessage(context, result);
            sendUserInputRequired(context, result);
            sendWorkflowState(context, result, 'Preciso de uma informacao sua para continuar.');
            return;
        }

        console.log(`[Hub] Fluxo encerrado com status: ${result.status}`);
        await sendAssistantMessage(context, result);
        sendWorkflowState(context, result, result.message_to_user || 'Solicitacao encerrada.');
    }

    async function sendAssistantMessage(context, result) {
        const workflow = await loadWorkflowForCommunication(result);
        const communicatedResult = result.communicated === true
            ? result
            : await communicateWorkflowResult(result, context.emit, {
                workflow: workflow || result,
                workflowRepository,
                communicate: workflow ? journeys.get(workflow.journey_type).communicate : undefined
            });

        context.emit('assistant_message', buildAssistantMessagePayload(communicatedResult, {
            includePayload: context.includePayload
        }));
    }

    async function loadWorkflowForCommunication(result) {
        if (!result.workflow_id) return null;
        try {
            return await workflowRepository.requireById(result.workflow_id);
        } catch (error) {
            console.error('[Hub] Falha ao carregar workflow para comunicacao:', error.message);
            return null;
        }
    }

    function sendUserInputRequired(context, result) {
        context.emit('user_input_required', {
            workflow_id: result.workflow_id,
            parent_conversation_id: result.parent_conversation_id,
            content: result.message_to_user,
            created_at: new Date().toISOString()
        });
    }

    function sendWorkflowState(context, result, message) {
        context.emit('workflow_state', {
            workflow_id: result.workflow_id,
            parent_conversation_id: result.parent_conversation_id,
            status: result.status,
            message,
            created_at: new Date().toISOString()
        });
    }

    return sendTurnResult;
}

module.exports = { createChatTurnPresenter };
