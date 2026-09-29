const { HUB_CONTRACT, artifactBuckets, normalizeAgentOutput } = require('./hub_response_contract');

function prefixId(prefix, id) {
    return `${prefix}.${String(id)}`;
}

function cloneWithPrefixedIds(output, prefix) {
    const idMap = new Map();
    const artifacts = {};
    for (const [bucket, items] of Object.entries(artifactBuckets(output.artifacts))) {
        artifacts[bucket] = items.map(item => {
            const nextId = prefixId(prefix, item.id);
            idMap.set(item.id, nextId);
            const localId = String(item.id || '').split('.').pop();
            if (localId && localId !== item.id) idMap.set(localId, nextId);
            return { ...item, id: nextId };
        });
    }

    const blocks = (output.blocks || []).map((block, index) => ({
        ...block,
        id: block.id ? prefixId(prefix, block.id) : prefixId(prefix, `block_${index + 1}`),
        artifact_id: idMap.get(block.artifact_id) || block.artifact_id,
        artifact_refs: (block.artifact_refs || []).map(id => idMap.get(id) || id),
        annotations: (block.annotations || []).map(annotation => ({
            ...annotation,
            query_id: idMap.get(annotation.query_id) || annotation.query_id
        }))
    }));

    artifacts.visualizations = artifacts.visualizations.map(visualization => ({
        ...visualization,
        source_query_id: idMap.get(visualization.source_query_id) || visualization.source_query_id
    }));

    return { ...output, blocks, artifacts };
}

function mergeStructuredOutputs(outputs = []) {
    const normalized = outputs.map((item, index) => cloneWithPrefixedIds(normalizeAgentOutput(item), `out${index + 1}`));
    return {
        content: normalized.map(item => item.content).filter(Boolean).join('\n\n'),
        contract: HUB_CONTRACT,
        blocks: normalized.flatMap(item => item.blocks || []),
        artifacts: {
            visualizations: normalized.flatMap(item => item.artifacts.visualizations),
            queries: normalized.flatMap(item => item.artifacts.queries),
            notes: normalized.flatMap(item => item.artifacts.notes)
        },
        metadata: { merged_outputs: normalized.length }
    };
}

function allArtifactIds(output) {
    const artifacts = artifactBuckets(output.artifacts);
    return new Set(Object.values(artifacts).flat().map(item => item.id).filter(Boolean));
}

function validateArtifactReferences(output) {
    const ids = allArtifactIds(output);
    const missing = [];
    for (const block of output.blocks || []) {
        if (block.artifact_id && !ids.has(block.artifact_id)) missing.push(block.artifact_id);
        for (const ref of block.artifact_refs || []) {
            if (!ids.has(ref)) missing.push(ref);
        }
    }
    return { valid: missing.length === 0, missing };
}

function extractArtifactManifest(output) {
    const structured = normalizeAgentOutput(output);
    return Object.entries(structured.artifacts).flatMap(([bucket, items]) => items.map(item => ({
        id: item.id,
        type: item.type || bucket.replace(/s$/, ''),
        title: item.title || '',
        visualization_type: item.visualization_type || item.type || null,
        provider_category: item.provider?.category || structured.metadata?.provider_category || null
    })));
}

module.exports = {
    cloneWithPrefixedIds,
    mergeStructuredOutputs,
    validateArtifactReferences,
    extractArtifactManifest
};
