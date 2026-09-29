const { createMarkdownOutput, HUB_CONTRACT } = require('../../structured_output');
const {
    indexGenieAttachments,
    cleanFallbackMarkdown,
    matchVizAttachmentByEmbedId
} = require('./attachment_index');
const {
    normalizeAnnotations,
    queryArtifact,
    noteArtifact,
    createVizArtifact
} = require('./artifact_builder');
const { stableId } = require('./provider_metadata');
const { enrichVisualization } = require('./visualization_enrichment');

async function parseGenieResponse(result, context = {}) {
    const indexed = indexGenieAttachments(Array.isArray(result.attachments) ? result.attachments : []);
    const report = indexed.reports.at(-1);
    const output = baseOutput(result, indexed, report);
    const queryArtifacts = indexed.queries.map(query => queryArtifact(query, context));
    const queryByAttachmentId = new Map(indexed.queries.map(query => [query.id, query]));
    const queryByStatementId = new Map(indexed.queries.map((query, index) => [query.statement_id, queryArtifacts[index]]));
    const visualizations = new Map();

    output.artifacts.queries = queryArtifacts;
    output.artifacts.notes = indexed.notes.map(note => noteArtifact(note, context)).filter(note => note.content);
    output.blocks = report?.content_chunks?.length
        ? blocksFromContentChunks(report.content_chunks, indexed.visualizations, queryByAttachmentId, queryByStatementId, context, visualizations)
        : fallbackBlocks(output.content);

    includeUnreferencedVisualizations(indexed.visualizations, queryByAttachmentId, context, visualizations, output.blocks);
    output.artifacts.visualizations = await enrichVisualizations(visualizations, context);

    return output;
}

function baseOutput(result, indexed, report) {
    const content = cleanFallbackMarkdown(report?.report_summary || indexed.texts.at(-1)?.content || result.content);
    return {
        ...createMarkdownOutput(content, { provider_category: 'GENIE' }),
        contract: HUB_CONTRACT,
        blocks: []
    };
}

function blocksFromContentChunks(chunks, visualizations, queryByAttachmentId, queryByStatementId, context, vizById) {
    return chunks.flatMap((chunk, index) => {
        if (String(chunk.text || '').trim() === '*(Visualization)*') {
            return visualizationBlock(chunk, visualizations, queryByAttachmentId, context, vizById);
        }

        const content = String(chunk.text || '').trim();
        if (!content) return [];
        const annotations = normalizeAnnotations(chunk.annotations || [], queryByStatementId);
        return [{
            id: stableId('block', `${index}:${content}`),
            type: 'markdown',
            content,
            annotations,
            artifact_refs: annotations.map(annotation => annotation.query_id).filter(Boolean)
        }];
    });
}

function visualizationBlock(chunk, visualizations, queryByAttachmentId, context, vizById) {
    const viz = matchVizAttachmentByEmbedId(chunk.metadata?.embed_id, visualizations);
    if (!viz) return [missingVisualizationBlock(chunk)];

    const query = queryByAttachmentId.get(viz.query_attachment_id);
    const artifact = createVizArtifact(viz, query, context);
    vizById.set(artifact.id, { artifact, query });
    return [{
        id: stableId('block', `viz:${artifact.id}`),
        type: 'visualization',
        artifact_id: artifact.id
    }];
}

function missingVisualizationBlock(chunk) {
    return {
        id: stableId('block', `missing:${chunk.metadata?.embed_id}`),
        type: 'visualization_missing',
        content: 'Visualizacao indisponivel.',
        metadata: { embed_id: chunk.metadata?.embed_id || null }
    };
}

function fallbackBlocks(content) {
    return content ? createMarkdownOutput(content).blocks : [];
}

function includeUnreferencedVisualizations(visualizations, queryByAttachmentId, context, vizById, blocks) {
    const usedVizIds = new Set(blocks.map(block => block.artifact_id).filter(Boolean));
    for (const viz of visualizations) {
        const query = queryByAttachmentId.get(viz.query_attachment_id);
        const artifact = createVizArtifact(viz, query, context);
        if (!usedVizIds.has(artifact.id)) vizById.set(artifact.id, { artifact, query });
    }
}

async function enrichVisualizations(vizById, context) {
    const artifacts = [];
    for (const { artifact, query } of vizById.values()) {
        artifacts.push(await enrichVisualization(artifact, query, context, context.report));
    }
    return artifacts;
}

module.exports = {
    parseGenieResponse,
    indexGenieAttachments,
    matchVizAttachmentByEmbedId
};
