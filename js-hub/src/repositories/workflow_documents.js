// O agregado usado pela jornada e reconstruido a partir de documentos menores.
// Estes nomes descrevem dados de negocio; detalhes de arquivo ficam no store.
const WORKFLOW_SECTIONS = {
    messages: 'messages',
    tasks: 'tasks',
    events: 'events',
    timeline: 'timeline_events',
    sessions: 'subagent_sessions',
    result: 'final_response_structured'
};

function splitWorkflow(workflow) {
    const metadata = { ...workflow };
    const documents = {};
    for (const [document, field] of Object.entries(WORKFLOW_SECTIONS)) {
        if (!Object.hasOwn(metadata, field)) continue;
        documents[document] = metadata[field];
        delete metadata[field];
    }

    if (documents.result && Object.hasOwn(documents.result, 'artifacts')) {
        const { artifacts, ...result } = documents.result;
        documents.result = result;
        documents.artifacts = artifacts;
    }

    // O historico de tool calling pode crescer muito durante execucao e HITL.
    if (documents.tasks) {
        documents.task_contexts = documents.tasks.map(task => {
            const context = { task_id: task.task_id };
            for (const field of ['agent_messages', 'output_structured']) {
                if (Object.hasOwn(task, field)) context[field] = task[field];
            }
            return context;
        }).filter(context => Object.keys(context).length > 1);
        documents.tasks = documents.tasks.map(task => {
            const { agent_messages, output_structured, ...summary } = task;
            return summary;
        });
    }

    const sections = Object.keys(documents);
    documents.workflow = metadata;
    return Object.fromEntries(Object.entries(documents).map(([name, data]) => [name, {
        schema_version: 1,
        workflow_id: workflow.workflow_id,
        ...(name === 'workflow' ? { sections } : {}),
        data
    }]));
}

function joinWorkflow(documents) {
    const workflow = { ...documents.workflow.data };
    for (const [document, field] of Object.entries(WORKFLOW_SECTIONS)) {
        if (documents[document]) workflow[field] = documents[document].data;
    }
    if (workflow.final_response_structured && documents.artifacts) {
        workflow.final_response_structured = {
            ...workflow.final_response_structured,
            artifacts: documents.artifacts.data
        };
    }
    if (workflow.tasks && documents.task_contexts) {
        const contexts = new Map(documents.task_contexts.data.map(context => [String(context.task_id), context]));
        workflow.tasks = workflow.tasks.map(task => ({ ...task, ...contexts.get(String(task.task_id)) }));
    }
    return workflow;
}

module.exports = { WORKFLOW_SECTIONS, splitWorkflow, joinWorkflow };
