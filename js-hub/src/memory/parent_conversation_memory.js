function formatParentConversationHistory(history = []) {
    if (!Array.isArray(history) || history.length === 0) return '';

    const lines = history.map(item => {
        const role = item.role === 'assistant' ? 'Hub' : 'Usuario';
        return `${role}: ${item.content}`;
    });

    return [
        'Historico recente da conversa parent do Hub:',
        ...lines,
        '',
        'Use esse historico apenas quando a pergunta atual depender de contexto anterior. A tarefa planejada deve responder a mensagem atual do usuario.'
    ].join('\n');
}

function buildParentConversationMessages(history = []) {
    if (!Array.isArray(history) || history.length === 0) return [];

    return history
        .filter(item => ['user', 'assistant'].includes(item.role))
        .filter(item => typeof item.content === 'string' && item.content.trim())
        .map(item => ({
            role: item.role,
            content: item.content
        }));
}

module.exports = {
    formatParentConversationHistory,
    buildParentConversationMessages
};
