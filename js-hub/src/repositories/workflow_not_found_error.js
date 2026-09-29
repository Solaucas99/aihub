class WorkflowNotFoundError extends Error {
    constructor(workflowId) {
        super(`Workflow "${workflowId}" nao encontrado.`);
        this.name = 'WorkflowNotFoundError';
    }
}

module.exports = { WorkflowNotFoundError };
