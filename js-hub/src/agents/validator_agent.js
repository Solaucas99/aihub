const { runAgent, structuredOutput } = require('../openai');

const VALIDATION_FORMAT = structuredOutput('task_validation', {
    type: 'object', additionalProperties: false,
    required: ['status', 'feedback', 'message_to_user'],
    properties: {
        status: { type: 'string', enum: ['COMPLETED', 'RETRY', 'ASK_HUMAN', 'CANCELLED'] },
        feedback: { type: 'string' },
        message_to_user: { type: 'string' }
    }
});

async function validatorAgent(payload) {
    const content = await runAgent({
        prompt: [
            'Valide a resposta de um agente contra o objetivo da tarefa e os esclarecimentos do usuario.',
            'COMPLETED: resposta substantiva e util que atende o objetivo.',
            'RETRY: falha temporaria, resposta vazia ou irrelevante.',
            'ASK_HUMAN: uma informacao ou decisao do usuario e indispensavel para continuar.',
            'CANCELLED: impedimento estrutural de acesso, permissao ou possibilidade.',
            'Em ASK_HUMAN, escreva uma pergunta objetiva em message_to_user.',
            'Nao peca esclarecimentos apenas para melhorar uma resposta que ja atende a tarefa.',
            'Retorne a validacao no formato JSON solicitado.'
        ].join('\n'),
        messages: [{ role: 'user', content: JSON.stringify(payload) }],
        responseFormat: VALIDATION_FORMAT
    });
    return JSON.parse(content);
}

module.exports = { validatorAgent };
