const { runAgent } = require('../openai');

async function communicatorAgent(payload) {
    return runAgent({
        prompt: [
            'Voce e o comunicador final de um CRM agentico.',
            'Escreva apenas uma introducao curta ao usuario, sem JSON, IDs, tools, providers ou detalhes internos.',
            'Nunca escreva o status tecnico do workflow como titulo ou prefixo, por exemplo COMPLETED, FAILED, CANCELLED ou WAITING_USER.',
            'COMPLETED: resuma em no maximo 2 frases o que os agentes encontraram. Nao reproduza a resposta completa dos agentes.',
            'WAITING_USER: faca a pergunta necessaria para continuar.',
            'CANCELLED: explique o encerramento de forma breve e util.',
            'FAILED: explique que nao foi possivel concluir agora.',
            'Nao invente resultados nem apresente uma tarefa incompleta como concluida.',
            'As respostas completas dos agentes serao exibidas depois da sua introducao.'
        ].join('\n'),
        messages: [{ role: 'user', content: JSON.stringify(payload) }]
    });
}

module.exports = { communicatorAgent };
