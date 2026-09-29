const { createHash } = require('crypto');

const MAX_QUERY_CHARS = () => Number(process.env.GENIE_QUERY_MAX_CHARS || 6000);

function providerMeta(contract, sourceIds = {}) {
    return {
        category: 'GENIE',
        contract,
        contract_version: '1.0',
        source_ids: Object.fromEntries(Object.entries(sourceIds).filter(([, value]) => value !== undefined && value !== null))
    };
}

function stableId(prefix, value) {
    return `${prefix}_${createHash('sha1').update(String(value)).digest('hex').slice(0, 12)}`;
}

function clipQueryText(value = '', max = MAX_QUERY_CHARS()) {
    const text = String(value || '');
    if (!Number.isFinite(max) || max <= 0 || text.length <= max) return { text, truncated: false };
    return { text: `${text.slice(0, max)}\n-- Query truncada para exibicao.`, truncated: true };
}

module.exports = {
    providerMeta,
    stableId,
    clipQueryText
};
