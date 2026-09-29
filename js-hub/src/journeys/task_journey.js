const { WORKFLOW_STATUS, TASK_STATUS } = require('../constants');
const { agentOrchestrator } = require('../tasks/task_scheduler');
const { normalizeTasks } = require('../tasks/task_planning');

// Shared lifecycle for task-based journeys. Business rules are supplied by the journey.
/** @param {import('./contracts').TaskJourneyConfig} config
 * @param {import('./contracts').JourneyServices} services
 * @returns {import('./contracts').Journey}
 */
function createTaskJourney({
    type, plan, validate, communicate, messages,
    selectAgents = agents => agents, parallelism, maxAttempts
}, { workflowRepository: repository, getAgents, workflows }) {
    async function execute(workflow, emit, { resumeMessage, resumeTaskId } = {}) {
        const context = {
            workflow, workflowRepository: repository, validate, communicate, parallelism, maxAttempts,
            providerSessions: workflow.subagent_sessions, resumeMessage, resumeTaskId
        };
        try {
            context.agents = selectAgents(await getAgents());
            context.parentConversationHistory = await repository.getParentConversationHistory(
                workflow.parent_conversation_id, { excludeWorkflowId: workflow.workflow_id }
            );
            if (!resumeTaskId) {
                workflow.tasks = normalizeTasks(await plan(workflow.messages[0].content, emit, context), context.agents);
                await repository.setTasks(workflow.workflow_id, workflow.tasks);
            }
            return await agentOrchestrator(workflow.tasks, emit, context);
        } catch (error) {
            console.error('[Journey] Falha:', error.message);
            return workflows.communicate({
                status: WORKFLOW_STATUS.FAILED, workflow_id: workflow.workflow_id,
                parent_conversation_id: workflow.parent_conversation_id,
                feedback: error.message, message_to_user: 'Nao consegui concluir esta solicitacao agora.'
            }, emit, context);
        }
    }

    return {
        type, communicate, messages,
        get: id => repository.requireById(id),
        async start({ message, parentConversationId = null, emit = () => {}, supersededWorkflowId, supersededReason }) {
            const workflow = await workflows.create({
                message, parentConversationId, journeyType: type, supersededWorkflowId, supersededReason
            });
            await workflows.announce(workflow, emit);
            return execute(workflow, emit);
        },
        async resume({ workflowId, message, emit = () => {} }) {
            const workflow = await repository.requireById(workflowId);
            if (workflow.journey_type !== type || workflow.status !== WORKFLOW_STATUS.WAITING_USER) {
                throw new Error('Workflow nao esta aguardando resposta nesta jornada.');
            }
            const resumeTaskId = workflow.waiting_task_id;
            const task = workflow.tasks.find(item => item.task_id === resumeTaskId);
            if (!task) throw new Error('Tarefa pendente nao encontrada.');
            task.status = TASK_STATUS.PENDING;
            await repository.setTasks(workflowId, workflow.tasks);
            await repository.appendMessage(workflowId, { role: 'user', content: message });
            const refreshed = await repository.updateStatus(workflowId, WORKFLOW_STATUS.RESUMING, {
                waiting_task_id: null, waiting_message: null
            });
            await workflows.announce(refreshed, emit, true);
            return execute(refreshed, emit, { resumeMessage: message, resumeTaskId });
        }
    };
}

module.exports = { createTaskJourney };
