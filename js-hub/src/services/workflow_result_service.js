const { sanitizeStructuredOutputForClient } = require('../structured_output');

function buildPayloadReference(result = {}, structured = {}) {
    const hasStructuredPayload = Boolean(
        result.workflow_id
        && (structured.blocks?.length || structured.artifacts || structured.contract || structured.metadata)
    );
    if (!hasStructuredPayload) return { payload_ready: false };
    return {
        payload_ready: true,
        payload_ref: {
            type: 'workflow_result',
            workflow_id: result.workflow_id,
            href: `/workflows/${result.workflow_id}/result`
        }
    };
}

function buildWorkflowResultPayload(workflow) {
    const structured = workflow.final_response_structured
        || null;
    if (!structured) {
        return {
            ready: false,
            workflow_id: workflow.workflow_id,
            parent_conversation_id: workflow.parent_conversation_id,
            status: workflow.status,
            message: 'Resultado ainda nao esta pronto.'
        };
    }

    const sanitized = sanitizeStructuredOutputForClient(structured);
    return {
        ready: true,
        workflow_id: workflow.workflow_id,
        parent_conversation_id: workflow.parent_conversation_id,
        journey_type: workflow.journey_type,
        status: workflow.status,
        messages: buildRenderableMessages(sanitized, workflow),
        updated_at: workflow.updated_at
    };
}

function buildRenderableMessages(structured, workflow) {
    const queries = new Map((structured.artifacts?.queries || []).map(query => [query.id, query]));
    const visualizations = new Map((structured.artifacts?.visualizations || []).map(viz => [viz.id, viz]));
    const messages = [];

    if (structured.content) {
        messages.push(textMessage('summary', structured.content));
    }

    for (const block of structured.blocks || []) {
        if (block.type === 'agent_result') {
            messages.push(agentResultMessage(block));
            continue;
        }

        if (block.type === 'visualization') {
            messages.push(visualizationMessage(block, visualizations, queries));
            continue;
        }

        if (block.type === 'visualization_missing') {
            messages.push(noticeMessage(block));
            continue;
        }

        messages.push(textMessage(block.id, block.content, annotationsForBlock(block, queries)));
    }

    const notes = structured.artifacts?.notes || [];
    if (notes.length) {
        messages.push({
            id: 'research_thoughts',
            role: 'assistant',
            type: 'research_thoughts',
            notes: notes.map(note => ({
                id: note.id,
                title: note.title,
                content: note.content
            }))
        });
    }

    if (!messages.length) {
        messages.push(textMessage('fallback', latestAssistantContent(workflow) || 'Resposta pronta.'));
    }

    return messages;
}

function latestAssistantContent(workflow) {
    return [...(workflow.messages || [])].reverse()
        .find(item => item.role === 'assistant')?.content || '';
}

function textMessage(id, content, annotations = []) {
    return {
        id,
        role: 'assistant',
        type: 'text',
        content,
        annotations
    };
}

function agentResultMessage(block) {
    return {
        id: block.id,
        role: 'assistant',
        type: 'agent_result',
        content: block.content,
        agent_id: block.metadata?.agent_id || null,
        task_id: block.metadata?.task_id || null,
        status: block.metadata?.status || null
    };
}

function visualizationMessage(block, visualizations, queries) {
    const visualization = visualizations.get(block.artifact_id);
    if (!visualization) {
        return noticeMessage({ id: block.id, content: 'Visualizacao indisponivel.' });
    }

    return {
        id: block.id,
        role: 'assistant',
        type: 'visualization',
        visualization: {
            id: visualization.id,
            title: visualization.title,
            visualization_type: visualization.visualization_type,
            render_provider: visualization.render_provider,
            status: visualization.status,
            spec: visualization.spec || null,
            table: visualization.table || null,
            data_policy: visualization.data_policy || null,
            source_query: querySummary(queries.get(visualization.source_query_id))
        }
    };
}

function noticeMessage(block) {
    return {
        id: block.id,
        role: 'assistant',
        type: 'notice',
        content: block.content || 'Informacao indisponivel.'
    };
}

function annotationsForBlock(block, queries) {
    const fromAnnotations = (block.annotations || [])
        .map(annotation => ({
            title: annotation.title || '',
            index: annotation.index || null,
            query: querySummary(queries.get(annotation.query_id))
        }))
        .filter(annotation => annotation.title || annotation.query);

    const annotatedQueryIds = new Set(fromAnnotations.map(annotation => annotation.query?.id).filter(Boolean));
    const fromRefs = (block.artifact_refs || [])
        .filter(queryId => !annotatedQueryIds.has(queryId))
        .map(queryId => ({ title: queries.get(queryId)?.title || '', index: null, query: querySummary(queries.get(queryId)) }))
        .filter(annotation => annotation.query);

    return [...fromAnnotations, ...fromRefs];
}

function querySummary(query) {
    if (!query) return null;
    return {
        id: query.id,
        title: query.title,
        sql: query.query
    };
}

module.exports = { buildPayloadReference, buildWorkflowResultPayload };
