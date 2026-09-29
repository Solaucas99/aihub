const { WORKFLOW_STATUS, TASK_STATUS } = require('../constants');
const { deterministicValidationGuard } = require('../validation');
const { emitAndStore } = require('../events/workflow_events');
const { executeSubAgentTask } = require('./subagent_task_executor');
const { validatorAgent } = require('../agents/validator_agent');
const { positiveInteger } = require('../openai');

function completedTaskFrom(task) {
    return {
        task_id: task.task_id,
        agent_id: task.agent_id,
        task_description: task.task_description,
        status: TASK_STATUS.COMPLETED,
        feedback: task.validation?.feedback || '',
        generated_answer: task.output,
        structured_output: task.output_structured || null
    };
}

async function executeWorkflowTask(task, emit, context) {
    const maxAttempts = positiveInteger(context.maxAttempts ?? process.env.TASK_RETRY_ATTEMPTS, 3);
    const validate = context.validate || validatorAgent;
    const { workflow, workflowRepository } = context;
    let payload;
    let validation;

    await emitAndStore(emit, 'task_start', {
        task_id: task.task_id, agent_id: task.agent_id, task_description: task.task_description
    }, context);

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        await markTaskAttempt(workflowRepository, workflow, task, attempt);
        await emitAndStore(emit, 'task_attempt_start', {
            task_id: task.task_id, agent_id: task.agent_id, attempt, max_attempts: maxAttempts
        }, context);

        ({ payload, validation } = await runValidatedAttempt(task, emit, context, validate, validation));
        await emitAndStore(emit, 'task_validated', {
            task_id: task.task_id, status: validation.status, attempt, max_attempts: maxAttempts
        }, context);

        if (validation.status !== 'RETRY') break;
        await announceRetryIfNeeded(task, emit, context, attempt, maxAttempts);
    }

    return persistTaskOutcome(task, payload, validation, workflowRepository, workflow);
}

async function markTaskAttempt(workflowRepository, workflow, task, attempt) {
    if (!workflowRepository || !workflow) return;
    await workflowRepository.updateTask(workflow.workflow_id, task.task_id, {
        status: TASK_STATUS.IN_PROGRESS,
        attempts: (task.attempts || 0) + attempt
    });
}

async function runValidatedAttempt(task, emit, context, validate, previousValidation) {
    try {
        const payload = await executeSubAgentTask(task, emit, {
            ...context,
            retryFeedback: previousValidation?.feedback
        });
        const validation = deterministicValidationGuard(payload, await validate(payload, task, context));
        return { payload, validation };
    } catch (error) {
        return {
            payload: failedTaskPayload(task, error),
            validation: { status: 'RETRY', feedback: error.message, message_to_user: '' }
        };
    }
}

function failedTaskPayload(task, error) {
    return {
        task_description: task.task_description,
        original_question: task.original_question,
        sub_agent_output: {
            content: '',
            status: 'FAILED',
            error: { message: error.message, status: error.status }
        }
    };
}

async function announceRetryIfNeeded(task, emit, context, attempt, maxAttempts) {
    if (attempt >= maxAttempts) return;
    await emitAndStore(emit, 'task_retrying', {
        task_id: task.task_id,
        agent_id: task.agent_id,
        next_attempt: attempt + 1,
        max_attempts: maxAttempts
    }, context);
}

async function persistTaskOutcome(task, payload, validation, workflowRepository, workflow) {
    const status = workflowStatusFromValidation(validation.status);
    Object.assign(task, {
        status,
        output: payload.sub_agent_output.content,
        output_structured: payload.sub_agent_output.structured || null,
        validation
    });

    if (workflowRepository && workflow) {
        await workflowRepository.updateTask(workflow.workflow_id, task.task_id, {
            status, output: task.output, output_structured: task.output_structured, validation
        });
    }

    return {
        status,
        task,
        validationPayload: payload,
        validationResult: validation,
        completedTask: status === WORKFLOW_STATUS.COMPLETED ? completedTaskFrom(task) : null
    };
}

function workflowStatusFromValidation(status) {
    return { ASK_HUMAN: WORKFLOW_STATUS.WAITING_USER, RETRY: WORKFLOW_STATUS.FAILED }[status] || status;
}

function failureOutcome(task, error) {
    return {
        status: WORKFLOW_STATUS.FAILED,
        task,
        validationPayload: { sub_agent_output: { content: '', status: 'FAILED' } },
        validationResult: { status: 'RETRY', feedback: error.message, message_to_user: '' }
    };
}

module.exports = {
    completedTaskFrom,
    executeWorkflowTask,
    failureOutcome
};
