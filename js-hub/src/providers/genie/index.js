const { novaMensagemGenie, continuarConversaGenie } = require('./tools');
const { parseGenieResponse } = require('./response_parser');

const PROGRESS = {
    SUBMITTED: { status: 'RUNNING', title: 'Solicitacao recebida' },
    ASKING_AI: { status: 'RUNNING', title: 'Analisando os dados' },
    QUERY_RUNNING: { status: 'RUNNING', title: 'Rodando consulta nos dados' },
    REPORT_READY: { status: 'COMPLETED', title: 'Relatorio pronto' },
    COMPLETED: { status: 'COMPLETED', title: 'Consulta finalizada' },
    FAILED: { status: 'FAILED', title: 'Nao foi possivel concluir a consulta' }
};

module.exports = {
    id: 'GENIE',
    validateConfig(config) {
        if (typeof config.space_id !== 'string' || !config.space_id.trim()) {
            throw new Error('GENIE requer agent_provider_config.space_id.');
        }
    },
    tools: {
        enviarMensagemGenie: {
            schema: {
                type: 'function',
                function: {
                    name: 'enviarMensagemGenie',
                    description: 'Consulta os dados configurados para este agente e continua a conversa existente quando disponivel.',
                    strict: true,
                    parameters: {
                        type: 'object', additionalProperties: false,
                        properties: { message: { type: 'string', description: 'Pergunta ou resposta de negocio para a fonte de dados.' } },
                        required: ['message']
                    }
                }
            },
            async execute({ message }, { config, session, saveSession, report }) {
                const send = session?.conversation_id ? continuarConversaGenie : novaMensagemGenie;
                let latestSession = {
                    conversationId: session?.conversation_id || null,
                    messageId: session?.message_id || null
                };
                const result = await send({
                    message, spaceId: config.space_id, conversationId: session?.conversation_id,
                    rawResult: true,
                    _sessionReporter: ({ conversationId, messageId }) => saveSession({
                        conversation_id: conversationId, message_id: messageId
                    }).then(() => {
                        latestSession = { conversationId, messageId };
                    }),
                    _reporter: ({ status }) => PROGRESS[status] ? report(PROGRESS[status]) : undefined
                });
                return parseGenieResponse(result, {
                    spaceId: config.space_id,
                    conversationId: result.conversation_id || latestSession.conversationId,
                    messageId: result.message_id || latestSession.messageId,
                    report
                });
            }
        }
    }
};
