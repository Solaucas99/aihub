const { setTimeout: delay } = require('node:timers/promises');
const { requestGenie, positiveMilliseconds } = require('./client');
const { genieAuth } = require('./auth');

const STATUS_LABELS = {
    SUBMITTED: 'Pensando',
    ASKING_AI: 'Perguntando a IA',
    COMPLETED: 'Resposta pronta',
    FAILED: 'Falha na Genie'
};

async function pollForResult(spaceId, conversationId, messageId, reportStatus = () => {}) {
    const interval = positiveMilliseconds(process.env.GENIE_POLL_INTERVAL_MS, 8000);
    const timeout = positiveMilliseconds(process.env.GENIE_POLL_TIMEOUT_MS, 1800000);
    const signal = AbortSignal.timeout(timeout);
    const progress = { lastStatus: null, attachments: new Set() };
    const identity = { messageId, conversationId };
    const resource = `/${spaceId}/conversations/${conversationId}/messages/${messageId}`;

    try {
        const authTimeout = AbortSignal.timeout(positiveMilliseconds(process.env.GENIE_REQUEST_TIMEOUT_MS, 60000));
        const token = await genieAuth({ signal: AbortSignal.any([signal, authTimeout]) });
        while (true) {
            await delay(interval, undefined, { signal });
            const result = await requestGenie(resource, { signal, token, label: 'Polling mensagem' });
            await reportProgress(result, progress, identity, reportStatus);
            if (result.status === 'COMPLETED') return result;
            if (result.status === 'FAILED') throw new Error('A consulta Genie falhou.');
        }
    } catch (error) {
        if (signal.aborted) throw new Error(`A consulta Genie excedeu o limite de ${timeout}ms.`);
        throw error;
    }
}

async function reportProgress(result, progress, identity, report) {
    if (result.status && result.status !== progress.lastStatus) {
        progress.lastStatus = result.status;
        await report({ ...identity, status: result.status, label: STATUS_LABELS[result.status] || result.status });
    }
    for (const attachment of result.attachments || []) {
        for (const [field, status, label] of [
            ['query', 'QUERY_RUNNING', 'Rodando consulta'],
            ['deep_research_report', 'REPORT_READY', 'Relatorio pronto']
        ]) {
            const id = attachment[field]?.id;
            if (!id || progress.attachments.has(id)) continue;
            progress.attachments.add(id);
            await report({ ...identity, status, label });
        }
    }
}

module.exports = { pollForResult };
