const { extractArtifactManifest } = require('../structured_output');

function buildCommunicationDetails(details = {}) {
    return {
        completed_tasks: (details.completed_tasks || []).map(task => ({
            task_id: task.task_id,
            task_description: task.task_description,
            status: task.status,
            feedback: task.feedback,
            generated_answer: clipForCommunicator(task.generated_answer),
            artifacts: task.structured_output ? extractArtifactManifest(task.structured_output) : []
        })),
        terminal_task: terminalTaskDetails(details),
        validation_result: details.validation_result || null
    };
}

function terminalTaskDetails(details = {}) {
    if (!details.task) return null;

    const structuredOutput = details.validation_payload?.sub_agent_output?.structured;
    return {
        task_id: details.task.task_id,
        task_description: details.task.task_description,
        status: details.task.status,
        output: clipForCommunicator(
            details.validation_payload?.sub_agent_output?.content || details.task.output || ''
        ),
        artifacts: structuredOutput ? extractArtifactManifest(structuredOutput) : []
    };
}

function clipForCommunicator(value = '', max = Number(process.env.COMMUNICATOR_TASK_PREVIEW_MAX_CHARS || 1200)) {
    const text = String(value || '');
    if (!Number.isFinite(max) || max <= 0 || text.length <= max) return text;
    return `${text.slice(0, max)}\n-- Conteudo truncado para resumo.`;
}

module.exports = {
    buildCommunicationDetails,
    clipForCommunicator
};
