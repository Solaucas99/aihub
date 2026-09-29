const { FileWorkflowStore } = require('./file_workflow_store');
const { createWorkflow } = require('./workflow_factory');
const { buildParentConversationHistory } = require('./parent_conversation_history');
const { buildParentConversationView } = require('./parent_conversation_view');
const { WorkflowNotFoundError } = require('./workflow_not_found_error');

class WorkflowRepository {
    constructor(store = new FileWorkflowStore()) {
        this.store = store;
    }

    async create({ parentConversationId = null, journeyType, message }) {
        const workflow = createWorkflow({ parentConversationId, journeyType, message });
        await this.store.save(workflow);
        return workflow;
    }

    async getById(workflowId) {
        return this.store.getById(workflowId);
    }

    async getByParentConversationId(parentConversationId, options = {}) {
        return this.store.listByParent(parentConversationId, options);
    }

    async getParentConversationHistory(parentConversationId, { excludeWorkflowId = null, limit = 20 } = {}) {
        const workflows = await this.getByParentConversationId(parentConversationId, { excludeWorkflowId, sections: ['messages'] });
        return buildParentConversationHistory(workflows, { limit });
    }

    async getLatestSubagentSessions(parentConversationId, { excludeWorkflowId = null } = {}) {
        const workflows = await this.getByParentConversationId(parentConversationId, { excludeWorkflowId, sections: ['sessions'] });
        const sessions = {};

        for (const workflow of workflows) {
            for (const [agentId, session] of Object.entries(workflow.subagent_sessions || {})) {
                sessions[agentId] = {
                    ...session,
                    inherited_from_workflow_id: workflow.workflow_id
                };
            }
        }

        return sessions;
    }

    async getParentConversationView(parentConversationId) {
        const workflows = await this.getByParentConversationId(parentConversationId, {
            sections: ['messages', 'timeline', 'tasks', 'result']
        });
        return buildParentConversationView(parentConversationId, workflows);
    }

    async save(workflow) {
        workflow.updated_at = new Date().toISOString();
        return this.store.save(workflow);
    }

    // Toda alteracao parcial usa a versao mais recente, dentro da fila do store.
    async updateWorkflow(workflowId, mutate) {
        return this.store.updateById(workflowId, workflow => {
            const updatedAt = new Date().toISOString();
            mutate(workflow, updatedAt);
            workflow.updated_at = updatedAt;
        });
    }

    async finishTurn(workflowId, status, patch, message) {
        await this.updateWorkflow(workflowId, (workflow, updatedAt) => {
            Object.assign(workflow, patch, { status });
            if (Object.hasOwn(patch, 'final_response_structured')) {
                workflow.tasks = compactClosedTasks(workflow.tasks || []);
            }
            workflow.messages.push({ ...message, created_at: updatedAt });
        });
    }

    async appendMessage(workflowId, message) {
        return this.updateWorkflow(workflowId, workflow => {
            workflow.messages.push({
                ...message,
                created_at: message.created_at || new Date().toISOString()
            });
        });
    }

    async appendEvent(workflowId, event, payload = {}) {
        return this.updateWorkflow(workflowId, workflow => {
            workflow.events.push({
                event,
                payload,
                created_at: new Date().toISOString()
            });
        });
    }

    async appendTimelineEvent(workflowId, event) {
        return this.updateWorkflow(workflowId, workflow => {
            workflow.timeline_events = workflow.timeline_events || [];
            workflow.timeline_events.push(event);
        });
    }

    async setTasks(workflowId, tasks) {
        return this.updateWorkflow(workflowId, workflow => {
            workflow.tasks = tasks;
        });
    }

    async updateTask(workflowId, taskId, patch) {
        return this.updateWorkflow(workflowId, workflow => {
            workflow.tasks = workflow.tasks.map(task => {
                if (String(task.task_id) !== String(taskId)) {
                    return task;
                }

                return {
                    ...task,
                    ...patch,
                    updated_at: new Date().toISOString()
                };
            });
        });
    }

    async upsertSubagentSession(workflowId, agentId, session) {
        return this.updateWorkflow(workflowId, workflow => {
            workflow.subagent_sessions[agentId] = {
                ...(workflow.subagent_sessions[agentId] || {}),
                ...session,
                updated_at: new Date().toISOString()
            };
        });
    }

    async updateStatus(workflowId, status, patch = {}) {
        return this.updateWorkflow(workflowId, workflow => {
            workflow.status = status;
            Object.assign(workflow, patch);
        });
    }

    async requireById(workflowId) {
        const workflow = await this.getById(workflowId);
        if (!workflow) {
            throw new WorkflowNotFoundError(workflowId);
        }

        return workflow;
    }

}

function compactClosedTasks(tasks) {
    return tasks.map(task => {
        const { agent_messages, output_structured, ...summary } = task;
        return {
            ...summary,
            ...(output_structured ? { output_ref: { type: 'workflow_result' } } : {}),
            agent_messages_compacted: task.agent_messages_compacted
                || (Array.isArray(agent_messages) && agent_messages.length > 0)
        };
    });
}

module.exports = { WorkflowRepository };
