const { parseJsonField } = require('./helios_to_vega');
const { providerMeta, stableId, clipQueryText } = require('./provider_metadata');

function normalizeAnnotations(annotations = [], queriesByStatementId = new Map()) {
    return annotations.map(annotation => {
        const query = queriesByStatementId.get(annotation.identifier);
        return {
            title: annotation.title || query?.title || '',
            asset_type: annotation.asset_type || null,
            identifier: annotation.identifier || null,
            index: annotation.index || null,
            query_id: query?.id || null
        };
    });
}

function queryArtifact(query, context) {
    const clipped = clipQueryText(query.query || '');
    return {
        id: stableId('query', query.id || query.statement_id || query.title),
        type: 'query',
        title: query.title || 'Query gerada',
        query: clipped.text,
        truncated: clipped.truncated,
        columns: [],
        row_count: null,
        provider: providerMeta('GENIE_QUERY_ATTACHMENT', {
            space_id: context.spaceId,
            conversation_id: context.conversationId,
            message_id: context.messageId,
            attachment_id: query.id,
            statement_id: query.statement_id
        })
    };
}

function noteArtifact(note, context) {
    return {
        id: stableId('note', note.id || `${note.title}:${note.thought}`),
        type: 'provider_note',
        title: note.title || 'Etapa da analise',
        content: note.thought || '',
        provider: providerMeta('GENIE_RESEARCH_THOUGHT', {
            space_id: context.spaceId,
            conversation_id: context.conversationId,
            message_id: context.messageId,
            attachment_id: note.id
        })
    };
}

function createVizArtifact(viz, query, context) {
    return {
        id: stableId('viz', viz.id || viz.asset_id || viz.title),
        type: 'visualization',
        title: viz.title || 'Visualizacao',
        visualization_type: viz.type || 'unknown',
        render_provider: isTableViz(viz) ? 'HTML_TABLE' : 'VEGA_LITE',
        status: 'PENDING',
        spec: null,
        table: null,
        source_query_id: query ? stableId('query', query.id || query.statement_id || query.title) : null,
        source_query_attachment_id: viz.query_attachment_id || null,
        provider: providerMeta('GENIE_DEEP_RESEARCH_VIZ', {
            space_id: context.spaceId,
            conversation_id: context.conversationId,
            message_id: context.messageId,
            attachment_id: viz.id,
            asset_id: viz.asset_id,
            query_attachment_id: viz.query_attachment_id
        }),
        provider_payload: {
            chart_library: viz.chart_library || null,
            asset_id: viz.asset_id || null,
            definition: parseVizDefinition(viz),
            direct_viz_tool_call: parseVizDescription(viz)?.directVizToolCall || null
        }
    };
}

function parseVizDefinition(viz) {
    try {
        return parseJsonField(viz.definition, 'viz.definition');
    } catch (error) {
        return { parse_error: error.message };
    }
}

function parseVizDescription(viz) {
    try {
        return parseJsonField(viz.description, 'viz.description');
    } catch {
        return null;
    }
}

function isTableViz(viz = {}) {
    return String(viz.type || '').toLowerCase() === 'table';
}

module.exports = {
    normalizeAnnotations,
    queryArtifact,
    noteArtifact,
    createVizArtifact
};
