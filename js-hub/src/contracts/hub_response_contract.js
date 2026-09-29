const HUB_CONTRACT = { category: 'HUB_STRUCTURED_RESPONSE', version: '1.0' };

function safeJson(value) {
    if (typeof value !== 'string') return value;
    try {
        return JSON.parse(value);
    } catch {
        return value;
    }
}

function artifactBuckets(artifacts = {}) {
    return {
        visualizations: Array.isArray(artifacts.visualizations) ? artifacts.visualizations : [],
        queries: Array.isArray(artifacts.queries) ? artifacts.queries : [],
        notes: Array.isArray(artifacts.notes) ? artifacts.notes : []
    };
}

function createMarkdownOutput(content = '', metadata = {}) {
    const text = String(content || '');
    return {
        content: text,
        contract: HUB_CONTRACT,
        blocks: text ? [{ id: 'block_1', type: 'markdown', content: text, annotations: [], artifact_refs: [] }] : [],
        artifacts: artifactBuckets(),
        metadata
    };
}

function isStructuredOutput(value) {
    return Boolean(value && typeof value === 'object' && !Array.isArray(value)
        && (value.contract?.category === HUB_CONTRACT.category || value.blocks || value.artifacts));
}

function normalizeAgentOutput(value, metadata = {}) {
    const parsed = safeJson(value);
    if (!isStructuredOutput(parsed)) return createMarkdownOutput(String(value || ''), metadata);
    const content = String(parsed.content || '');
    return {
        ...parsed,
        content,
        contract: {
            category: parsed.contract?.category || HUB_CONTRACT.category,
            version: parsed.contract?.version || HUB_CONTRACT.version
        },
        blocks: Array.isArray(parsed.blocks) ? parsed.blocks : createMarkdownOutput(content).blocks,
        artifacts: artifactBuckets(parsed.artifacts),
        metadata: { ...(parsed.metadata || {}), ...metadata }
    };
}

module.exports = {
    HUB_CONTRACT,
    artifactBuckets,
    createMarkdownOutput,
    normalizeAgentOutput
};
