const { WORKFLOW_STATUS } = require('../constants');
const { sanitizeStructuredOutputForClient } = require('../structured_output');
const { buildPayloadReference } = require('../services/workflow_result_service');

function messageFromResult(result = {}) {
    if (result.message) return result.message;
    if (result.message_to_user) return result.message_to_user;

    switch (result.status) {
        case WORKFLOW_STATUS.WAITING_USER:
            return 'Preciso de uma informacao sua para continuar.';
        case WORKFLOW_STATUS.CANCELLED:
            return 'Nao consegui continuar com esta solicitacao.';
        case WORKFLOW_STATUS.FAILED:
            return 'Nao consegui concluir esta solicitacao agora.';
        case WORKFLOW_STATUS.SUPERSEDED:
            return 'Fechei a solicitacao anterior e comecei uma nova analise no mesmo contexto.';
        case WORKFLOW_STATUS.COMPLETED:
            return 'Resposta pronta.';
        default:
            return 'Solicitacao encerrada.';
    }
}

function assistantMessageKind(status) {
    switch (status) {
        case WORKFLOW_STATUS.WAITING_USER:
            return 'user_input_required';
        case WORKFLOW_STATUS.CANCELLED:
            return 'cancelled';
        case WORKFLOW_STATUS.FAILED:
            return 'failed';
        case WORKFLOW_STATUS.SUPERSEDED:
            return 'superseded';
        case WORKFLOW_STATUS.COMPLETED:
            return 'final';
        default:
            return 'status';
    }
}

function buildAssistantMessagePayload(result = {}, contentOrOptions = messageFromResult(result), maybeOptions = {}) {
    const content = resolveContent(result, contentOrOptions);
    const options = resolveOptions(contentOrOptions, maybeOptions);
    const structured = sanitizeStructuredOutputForClient(result.structured_response || {});
    const base = {
        workflow_id: result.workflow_id,
        parent_conversation_id: result.parent_conversation_id,
        status: result.status,
        kind: assistantMessageKind(result.status),
        content,
        ...buildPayloadReference(result, structured),
        created_at: new Date().toISOString()
    };

    if (!options.includePayload) return base;
    return {
        ...base,
        ...(structured.blocks ? { blocks: structured.blocks } : {}),
        ...(structured.artifacts ? { artifacts: structured.artifacts } : {}),
        ...(structured.contract ? { contract: structured.contract } : {}),
        ...(structured.metadata ? { metadata: structured.metadata } : {})
    };
}

function resolveContent(result, contentOrOptions) {
    return typeof contentOrOptions === 'object' && contentOrOptions !== null
        ? messageFromResult(result)
        : contentOrOptions;
}

function resolveOptions(contentOrOptions, maybeOptions) {
    return typeof contentOrOptions === 'object' && contentOrOptions !== null
        ? contentOrOptions
        : maybeOptions;
}

module.exports = {
    messageFromResult,
    assistantMessageKind,
    buildAssistantMessagePayload
};
