function normalizeValidationStatus(status) {
    const normalized = String(status || '').toUpperCase();
    if (['COMPLETED', 'ASK_HUMAN', 'RETRY', 'CANCELLED'].includes(normalized)) {
        return normalized;
    }

    return 'RETRY';
}

function deterministicValidationGuard(validationPayload, agenticValidation = {}) {
    const subAgentOutput = validationPayload.sub_agent_output || {};
    const content = String(subAgentOutput.content || '').trim();
    const error = subAgentOutput.error || {};

    if (!content) {
        return {
            ...agenticValidation,
            status: 'RETRY',
            feedback: agenticValidation.feedback || 'Subagente nao retornou conteudo util.',
            message_to_user: ''
        };
    }

    if (error.status === 429 || error.status >= 500 || subAgentOutput.status === 'FAILED') {
        return {
            ...agenticValidation,
            status: 'RETRY',
            feedback: agenticValidation.feedback || 'Subagente encontrou uma falha temporaria ou sistemica.',
            message_to_user: ''
        };
    }

    return {
        ...agenticValidation,
        status: normalizeValidationStatus(agenticValidation.status)
    };
}

module.exports = {
    deterministicValidationGuard,
    normalizeValidationStatus
};
