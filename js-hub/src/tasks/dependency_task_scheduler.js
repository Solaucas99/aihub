const { WORKFLOW_STATUS, TASK_STATUS } = require('../constants');
const { positiveInteger } = require('../openai');
const { completedTaskFrom, executeWorkflowTask, failureOutcome } = require('./task_attempt_runner');

function createSchedulerState(tasks, context) {
    const completed = tasks.filter(task => task.status === TASK_STATUS.COMPLETED).map(completedTaskFrom);
    return {
        limit: positiveInteger(context.parallelism ?? process.env.TASK_PARALLELISM, 4),
        completed,
        completedIds: new Set(completed.map(task => task.task_id)),
        waiting: tasks.filter(task => task.status === TASK_STATUS.WAITING_USER),
        pending: tasks.filter(task => ![TASK_STATUS.COMPLETED, TASK_STATUS.WAITING_USER].includes(task.status)),
        running: new Map(),
        outcomes: [],
        stopped: false
    };
}

async function runReadyTasks(state, emit, context) {
    while (state.pending.length || state.running.size) {
        startReadyTasks(state, emit, context);
        if (!state.running.size) {
            handleBlockedPendingTasks(state);
            break;
        }

        const outcome = await Promise.race(state.running.values());
        state.running.delete(outcome.task.task_id);
        applyTaskOutcome(state, outcome);
    }
}

function startReadyTasks(state, emit, context) {
    while (!state.stopped && state.running.size < state.limit) {
        const index = state.pending.findIndex(task => task.depends_on.every(id => state.completedIds.has(id)));
        if (index < 0) break;

        const [task] = state.pending.splice(index, 1);
        const dependencyResults = state.completed.filter(item => task.depends_on.includes(item.task_id));
        state.running.set(task.task_id, executeWorkflowTask(task, emit, { ...context, dependencyResults })
            .catch(error => failureOutcome(task, error)));
    }
}

function handleBlockedPendingTasks(state) {
    if (state.pending.length && !state.stopped && !state.waiting.length) {
        state.outcomes.push(failureOutcome(state.pending[0], new Error('Dependencias nao concluidas.')));
    }
}

function applyTaskOutcome(state, outcome) {
    if (outcome.status === WORKFLOW_STATUS.COMPLETED) {
        state.completed.push(outcome.completedTask);
        state.completedIds.add(outcome.task.task_id);
        return;
    }

    state.outcomes.push(outcome);
    state.stopped = true;
}

function appendWaitingTasks(state) {
    for (const task of state.waiting) {
        state.outcomes.push({
            status: WORKFLOW_STATUS.WAITING_USER,
            task,
            validationResult: task.validation,
            validationPayload: { sub_agent_output: { content: task.output } }
        });
    }
}

function highestPriorityOutcome(outcomes) {
    const priority = { FAILED: 0, CANCELLED: 1, WAITING_USER: 2 };
    return outcomes.sort((a, b) => priority[a.status] - priority[b.status])[0];
}

module.exports = {
    createSchedulerState,
    runReadyTasks,
    appendWaitingTasks,
    highestPriorityOutcome
};
