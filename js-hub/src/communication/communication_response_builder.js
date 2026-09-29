const {
    createMarkdownOutput,
    cloneWithPrefixedIds,
    normalizeAgentOutput,
    validateArtifactReferences,
    sanitizeStructuredOutputForClient
} = require('../structured_output');

function buildStructuredCommunicationResponse(message, details = {}) {
    const agentOutputs = collectAgentOutputs(details);
    if (!agentOutputs.length) return createMarkdownOutput(message);

    const structured = {
        content: message,
        contract: agentOutputs[0].output.contract,
        blocks: agentOutputs.flatMap(agentOutput => [
            agentResultBlock(agentOutput),
            ...agentOutput.output.blocks
        ]),
        artifacts: mergeArtifacts(agentOutputs),
        metadata: {
            agent_results: agentOutputs.map(agentOutput => ({
                task_id: agentOutput.task_id,
                agent_id: agentOutput.agent_id,
                status: agentOutput.status
            })),
            artifact_reference_validation: null
        }
    };

    structured.metadata.artifact_reference_validation = validateArtifactReferences(structured);
    return sanitizeStructuredOutputForClient(structured);
}

function collectAgentOutputs(details = {}) {
    const completedOutputs = (details.completed_tasks || [])
        .filter(task => task.structured_output)
        .map((task, index) => createAgentOutput(task, task.structured_output, index));

    const terminalTask = details.task;
    const terminalStructuredOutput = details.validation_payload?.sub_agent_output?.structured;
    const alreadyIncluded = terminalTask
        && completedOutputs.some(item => item.task_id === terminalTask.task_id);

    if (terminalTask && terminalStructuredOutput && !alreadyIncluded) {
        completedOutputs.push(createAgentOutput(
            terminalTask,
            terminalStructuredOutput,
            completedOutputs.length
        ));
    }

    return completedOutputs;
}

function createAgentOutput(task, structuredOutput, index) {
    const fallbackTitle = `Agente ${index + 1}`;
    return {
        task_id: task.task_id,
        agent_id: task.agent_id,
        status: task.status,
        title: task.task_description || task.agent_id || fallbackTitle,
        output: cloneWithPrefixedIds(normalizeAgentOutput(structuredOutput), `agent${index + 1}`)
    };
}

function mergeArtifacts(agentOutputs) {
    return agentOutputs.reduce((artifacts, agentOutput) => {
        artifacts.visualizations.push(...agentOutput.output.artifacts.visualizations);
        artifacts.queries.push(...agentOutput.output.artifacts.queries);
        artifacts.notes.push(...agentOutput.output.artifacts.notes);
        return artifacts;
    }, { visualizations: [], queries: [], notes: [] });
}

function agentResultBlock(agentOutput) {
    return {
        id: `agent_result_${agentOutput.task_id || agentOutput.agent_id}`,
        type: 'agent_result',
        content: `### Resultado de ${agentOutput.agent_id || 'agente'}\n\n${agentOutput.title}`,
        metadata: {
            task_id: agentOutput.task_id || null,
            agent_id: agentOutput.agent_id || null,
            status: agentOutput.status || null
        }
    };
}

module.exports = {
    buildStructuredCommunicationResponse,
    collectAgentOutputs
};
