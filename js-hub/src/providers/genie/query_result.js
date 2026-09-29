const { genieAuth } = require('./auth');

function requiredEnv(name) {
    const value = process.env[name];
    if (!value) throw new Error(`${name} is not set in environment variables.`);
    return value;
}

async function parseJsonResponse(response, label) {
    const text = await response.text();
    let body;
    try {
        body = JSON.parse(text);
    } catch {
        throw new Error(`${label} falhou (HTTP ${response.status}): resposta nao e JSON valido.`);
    }
    if (!response.ok) {
        throw new Error(`${label} falhou (HTTP ${response.status}): ${body.message || body.error_code || 'erro desconhecido'}`);
    }
    return body;
}

async function getGenieQueryResult({ spaceId, conversationId, messageId, attachmentId }) {
    const token = await genieAuth();
    const baseUrl = requiredEnv('GENIE_BASE_URL');
    const url = [
        baseUrl,
        '/api/2.0/genie/spaces/',
        encodeURIComponent(spaceId),
        '/conversations/',
        encodeURIComponent(conversationId),
        '/messages/',
        encodeURIComponent(messageId),
        '/attachments/',
        encodeURIComponent(attachmentId),
        '/query-result'
    ].join('');

    const response = await fetch(url, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
        }
    });

    return normalizeStatementResponse(await parseJsonResponse(response, 'Buscar resultado da query Genie'));
}

function columnName(column, index) {
    return column.name || column.column_name || column.label || `column_${index + 1}`;
}

function normalizeColumns(columns = []) {
    return columns.map((column, index) => ({
        name: columnName(column, index),
        type: column.type_name || column.type_text || column.type || column.type_name_json || 'string'
    }));
}

function coerceValue(value, type = '') {
    if (value === null || value === undefined) return null;
    const typeName = String(type).toUpperCase();
    if (/INT|LONG|FLOAT|DOUBLE|DECIMAL|NUMBER|NUMERIC/.test(typeName) && value !== '') {
        const numeric = Number(value);
        return Number.isFinite(numeric) ? numeric : value;
    }
    if (/BOOL/.test(typeName)) {
        if (value === true || value === false) return value;
        if (String(value).toLowerCase() === 'true') return true;
        if (String(value).toLowerCase() === 'false') return false;
    }
    return value;
}

function normalizeDataRow(row) {
    if (Array.isArray(row)) return row;
    if (Array.isArray(row?.values)) return row.values;
    return [];
}

function rowsFromDataArray(columns, dataArray = []) {
    return dataArray.map(row => {
        const values = normalizeDataRow(row);
        return Object.fromEntries(columns.map((column, index) => [
            column.name,
            coerceValue(values[index], column.type)
        ]));
    });
}

function normalizeStatementResponse(response = {}) {
    const statement = response.statement_response || response;
    const manifest = statement.manifest || {};
    const result = statement.result || {};
    const columns = normalizeColumns(manifest.schema?.columns || []);
    const rows = rowsFromDataArray(columns, result.data_array || []);
    return {
        statement_id: statement.statement_id || null,
        columns,
        rows,
        row_count: Number(result.row_count || manifest.total_row_count || rows.length || 0),
        truncated: Boolean(manifest.truncated),
        state: statement.status?.state || null
    };
}

module.exports = {
    getGenieQueryResult,
    normalizeStatementResponse,
    rowsFromDataArray
};
