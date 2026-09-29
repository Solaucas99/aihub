const { genieAuth } = require('./auth');

function positiveMilliseconds(value, fallback) {
    const number = Number(value);
    return Number.isSafeInteger(number) && number > 0 ? number : fallback;
}

async function requestGenie(resource, { method = 'GET', body, signal, token, label = 'Consulta Genie' } = {}) {
    const baseUrl = process.env.GENIE_BASE_URL;
    if (!baseUrl) throw new Error('GENIE_BASE_URL is not set in environment variables.');
    const timeout = AbortSignal.timeout(positiveMilliseconds(process.env.GENIE_REQUEST_TIMEOUT_MS, 60000));
    const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
    const accessToken = token || await genieAuth({ signal: requestSignal });
    const response = await fetch(`${baseUrl.replace(/\/$/, '')}/api/2.0/data-rooms${resource}`, {
        method,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
        ...(body ? { body: JSON.stringify(body) } : {}),
        signal: requestSignal
    });
    let data;
    try {
        data = await response.json();
    } catch {
        throw new Error(`${label} falhou (HTTP ${response.status}): resposta JSON invalida.`);
    }
    if (!response.ok) {
        const error = new Error(`${label} falhou (HTTP ${response.status}).`);
        error.status = response.status;
        throw error;
    }
    return data;
}

module.exports = { requestGenie, positiveMilliseconds };
