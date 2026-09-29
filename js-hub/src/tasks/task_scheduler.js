const { finalizeTerminalTaskOutcome, finalizeCompletedWorkflow } = require('./task_finalizer');
const {
    createSchedulerState,
    runReadyTasks,
    appendWaitingTasks,
    highestPriorityOutcome
} = require('./dependency_task_scheduler');

async function agentOrchestrator(tasks, emit = () => {}, context = {}) {
    if (!Array.isArray(tasks) || !tasks.length) throw new Error('Lista de tarefas vazia.');
    context.providerSessions ||= {};

    const state = createSchedulerState(tasks, context);
    await runReadyTasks(state, emit, context);
    appendWaitingTasks(state);

    if (state.outcomes.length) {
        return finalizeTerminalTaskOutcome(highestPriorityOutcome(state.outcomes), emit, context, state.completed);
    }
    if (state.completed.length !== tasks.length) throw new Error('Nem todas as tarefas foram concluidas.');
    return finalizeCompletedWorkflow(state.completed, emit, context);
}

module.exports = { agentOrchestrator };
