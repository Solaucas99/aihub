const { WORKFLOW_STATUS } = require('../constants');
const { emitAndStore } = require('../events/workflow_events');
const { communicateWorkflowResult } = require('../communication/workflow_communication');

const OUTCOME_EVENTS = {
    [WORKFLOW_STATUS.COMPLETED]: 'workflow_completed',
    [WORKFLOW_STATUS.WAITING_USER]: 'waiting_user',
    [WORKFLOW_STATUS.FAILED]: 'workflow_failed',
    [WORKFLOW_STATUS.CANCELLED]: 'workflow_cancelled'
};

async function finalizeTerminalTaskOutcome(outcome, emit = () => {}, context = {}, completedTasks = []) {
    const { task, validationResult, validationPayload, status } = outcome;
    const { workflow, workflowRepository } = context;
    if (workflowRepository && workflow) {
        await workflowRepository.updateTask(workflow.workflow_id, task.task_id, {
            status, validation: validationResult,
            output: validationPayload.sub_agent_output?.content || '',
            output_structured: validationPayload.sub_agent_output?.structured || task.output_structured || null
        });
    }
    return finish({
        status, workflow_id: workflow?.workflow_id,
        parent_conversation_id: workflow?.parent_conversation_id,
        task_id: task.task_id, agent_id: task.agent_id,
        feedback: validationResult.feedback,
        message_to_user: validationResult.message_to_user
    }, emit, context, {
        task, validation_result: validationResult,
        validation_payload: validationPayload, completed_tasks: completedTasks
    });
}

async function finalizeCompletedWorkflow(completedTasks, emit = () => {}, context = {}) {
    return finish({
        status: WORKFLOW_STATUS.COMPLETED,
        workflow_id: context.workflow?.workflow_id,
        parent_conversation_id: context.workflow?.parent_conversation_id
    }, emit, context, { completed_tasks: completedTasks });
}

async function finish(result, emit, context, details) {
    const communicated = await communicateWorkflowResult(result, emit, context, details);
    await emitAndStore(emit, OUTCOME_EVENTS[result.status], communicated, context);
    return communicated;
}

module.exports = { finalizeTerminalTaskOutcome, finalizeCompletedWorkflow };
