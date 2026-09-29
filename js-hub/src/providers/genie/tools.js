const { requestGenie } = require('./client');
const { pollForResult } = require('./polling');

async function iniciarConversaGenie(message, spaceId) {
    if (!message || typeof message !== 'string') {
        throw new Error('message is required to start a Genie conversation.');
    }

    if (!spaceId || typeof spaceId !== 'string') {
        throw new Error('spaceId is required to start a Genie conversation.');
    }

    const reqBody = {
        title: message,
        model: 'SMART_AI',
        visibility: 'PRIVATE',
        conversation_type: 'DEEP_RESEARCH',
        utm_metadata: {}
    };

    const jsonRequest = await requestGenie(`/${spaceId}/conversations`, {
        method: 'POST', body: reqBody, label: 'Iniciar conversa'
    });
    console.log(`[Genie] Conversa iniciada: ${jsonRequest.conversation_id}`);

    return {
        title: jsonRequest.title,
        conversationId: jsonRequest.conversation_id,
        id: jsonRequest.id
    };
}

function extractGenieOutput(result) {
    const deepResearchAttachment = (result.attachments || []).find(attachment => attachment.deep_research_report);
    return deepResearchAttachment ? deepResearchAttachment.deep_research_report.report_summary : result.content;
}

async function enviarMensagemEmConversaGenie({
    message,
    spaceId,
    conversationId,
    label,
    rawResult = false,
    _reporter = () => {},
    _sessionReporter = async () => {}
}) {
    if (!message || typeof message !== 'string') {
        throw new Error('message is required to send a Genie message.');
    }

    if (!spaceId || typeof spaceId !== 'string') {
        throw new Error('spaceId is required to send a Genie message.');
    }

    if (!conversationId || typeof conversationId !== 'string') {
        throw new Error('conversationId is required to send a Genie message.');
    }


    const reqBody = {
        content: message,
        client_context: {
            genie_app_context: {
                force_deep_research_planning: true
            }
        }
    };

    const jsonRequest = await requestGenie(`/${spaceId}/conversations/${conversationId}/messages`, {
        method: 'POST', body: reqBody, label
    });
    console.log(`[Genie] Mensagem enviada, aguardando resposta (messageId: ${jsonRequest.message_id})...`);

    await _sessionReporter({
        provider: 'GENIE',
        conversationId,
        spaceId,
        sessionId: conversationId,
        messageId: jsonRequest.message_id,
        providerContext: {
            space_id: spaceId
        }
    });

    const result = await pollForResult(spaceId, conversationId, jsonRequest.message_id, _reporter);

    if (!result) {
        console.log('[Genie] QUEBROU: pollForResult nao retornou nada.');
        throw new Error('Failed to get result');
    }

    if (result.is_final !== true) {
        console.log('[Genie] QUEBROU: resultado nao e final.');
        throw new Error('Result is not final');
    }

    return rawResult ? result : extractGenieOutput(result);
}

async function novaMensagemGenie({ message, spaceId = null, rawResult = false, _reporter = () => {}, _sessionReporter = async () => {} } = {}) {
    if (!message || typeof message !== 'string') {
        throw new Error('message is required to send a new Genie message.');
    }

    console.log(`[Genie] novaMensagemGenie chamada (spaceId: ${spaceId}): "${message}"`);
    const newConversation = await iniciarConversaGenie(message, spaceId);
    const output = await enviarMensagemEmConversaGenie({
        message,
        spaceId,
        conversationId: newConversation.conversationId,
        label: 'Enviar mensagem',
        rawResult,
        _reporter,
        _sessionReporter
    });

    console.log('[Genie] novaMensagemGenie concluida com sucesso.');
    return output;
}

async function continuarConversaGenie({ message, spaceId = null, conversationId = null, rawResult = false, _reporter = () => {}, _sessionReporter = async () => {} } = {}) {
    console.log(`[Genie] continuarConversaGenie chamada (spaceId: ${spaceId}, conversationId: ${conversationId}): "${message}"`);
    const output = await enviarMensagemEmConversaGenie({
        message,
        spaceId,
        conversationId,
        label: 'Continuar conversa',
        rawResult,
        _reporter,
        _sessionReporter
    });

    console.log('[Genie] continuarConversaGenie concluida com sucesso.');
    return output;
}

module.exports = {
    novaMensagemGenie,
    continuarConversaGenie
};
