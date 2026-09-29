const { artifactBuckets } = require('./hub_response_contract');

function clipClientText(text = '', max = Number(process.env.CLIENT_ARTIFACT_TEXT_MAX_CHARS || 6000)) {
    const value = String(text || '');
    if (!Number.isFinite(max) || max <= 0 || value.length <= max) return { value, truncated: false };
    return { value: `${value.slice(0, max)}\n-- Conteudo truncado para exibicao.`, truncated: true };
}

function publicProvider(provider = {}) {
    if (!provider || typeof provider !== 'object') return null;
    return {
        category: provider.category || null,
        contract: provider.contract || null,
        contract_version: provider.contract_version || null
    };
}

function referencedQueryIds(blocks = [], visualizations = []) {
    const ids = new Set();
    for (const block of blocks) {
        for (const id of block.artifact_refs || []) ids.add(id);
    }
    for (const visualization of visualizations) {
        if (visualization.source_query_id) ids.add(visualization.source_query_id);
    }
    return ids;
}

function sanitizeBlockForClient(block = {}) {
    const publicMetadata = ['visualization_missing', 'agent_result'].includes(block.type) ? block.metadata : undefined;
    return Object.fromEntries(Object.entries({
        id: block.id,
        type: block.type,
        content: block.content,
        artifact_id: block.artifact_id,
        artifact_refs: block.artifact_refs?.length ? block.artifact_refs : undefined,
        annotations: block.annotations?.length ? block.annotations.map(annotation => ({
            title: annotation.title || '',
            query_id: annotation.query_id || null,
            index: annotation.index || null
        })) : undefined,
        metadata: publicMetadata
    }).filter(([, value]) => value !== undefined));
}

function sanitizeArtifactsForClient(artifacts = {}) {
    const buckets = artifactBuckets(artifacts);
    return {
        visualizations: buckets.visualizations.map(sanitizeVisualizationArtifact),
        queries: buckets.queries.map(sanitizeQueryArtifact),
        notes: shouldIncludeProviderNotes()
            ? buckets.notes.slice(0, Number(process.env.CLIENT_PROVIDER_NOTES_MAX || 6)).map(sanitizeNoteArtifact)
            : []
    };
}

function sanitizeVisualizationArtifact(item) {
    return Object.fromEntries(Object.entries({
        id: item.id,
        type: item.type,
        title: item.title,
        visualization_type: item.visualization_type,
        render_provider: item.render_provider,
        status: item.status,
        spec: item.status === 'READY' ? item.spec : null,
        table: item.status === 'READY' ? item.table : null,
        source_query_id: item.source_query_id || null,
        data_policy: item.data_policy,
        provider: publicProvider(item.provider)
    }).filter(([, value]) => value !== undefined));
}

function sanitizeQueryArtifact(item) {
    const clipped = clipClientText(item.query || '');
    return Object.fromEntries(Object.entries({
        id: item.id,
        type: item.type,
        title: item.title,
        query: clipped.value,
        truncated: item.truncated || clipped.truncated,
        provider: publicProvider(item.provider)
    }).filter(([, value]) => value !== undefined));
}

function sanitizeNoteArtifact(item) {
    const clipped = clipClientText(item.content || '', 1200);
    return Object.fromEntries(Object.entries({
        id: item.id,
        type: item.type,
        title: item.title,
        content: clipped.value,
        truncated: item.truncated || clipped.truncated,
        provider: publicProvider(item.provider)
    }).filter(([, value]) => value !== undefined));
}

function shouldIncludeProviderNotes() {
    return String(process.env.CLIENT_INCLUDE_PROVIDER_NOTES ?? 'true').toLowerCase() !== 'false';
}

function sanitizeStructuredOutputForClient(output = {}) {
    if (!output || typeof output !== 'object') return output;
    const blocks = (output.blocks || []).map(sanitizeBlockForClient);
    const artifacts = sanitizeArtifactsForClient(output.artifacts);
    const queryIds = referencedQueryIds(blocks, artifacts.visualizations);
    return {
        content: String(output.content || ''),
        contract: output.contract,
        blocks,
        artifacts: {
            ...artifacts,
            queries: artifacts.queries.filter(query => queryIds.has(query.id))
        },
        metadata: output.metadata?.artifact_reference_validation
            ? { artifact_reference_validation: output.metadata.artifact_reference_validation }
            : undefined
    };
}

module.exports = {
    sanitizeArtifactsForClient,
    sanitizeStructuredOutputForClient
};
