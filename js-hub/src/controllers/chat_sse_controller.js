const { createSseStream } = require('../transport/sse_stream');

function createChatSseHandler({ processChatTurn }) {
    return async function handleChatSseRequest(params, res) {
        const stream = createSseStream(res);
        const context = createRequestContext(params, stream.send);

        if (!isValidMessage(context.message)) {
            stream.send('error', {
                message: 'Parametro "message" e obrigatorio (query string na GET, body JSON na POST).'
            });
            stream.close();
            return;
        }

        try {
            await processChatTurn(context);
        } catch (error) {
            console.error('[Hub] QUEBROU:', error.message);
            stream.send('error', {
                message: 'Nao consegui processar esta solicitacao agora. Verifique os dados enviados e tente novamente.'
            });
        } finally {
            stream.close();
        }
    };
}

function createRequestContext(params, emit) {
    return {
        message: params.message,
        workflowId: params.workflowId || null,
        parentConversationId: params.parentConversationId || null,
        journeyHint: params.journeyHint || null,
        replyMode: params.replyMode || null,
        includePayload: Boolean(params.includePayload),
        emit
    };
}

function isValidMessage(message) {
    return typeof message === 'string' && message.trim().length > 0;
}

module.exports = { createChatSseHandler };
