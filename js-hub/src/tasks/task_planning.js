const { TASK_STATUS } = require('../constants');

function normalizeTasks(tasks, agents) {
    if (!Array.isArray(tasks) || tasks.length === 0) {
        throw new Error('Agente Planejador retornou uma lista de tarefas vazia ou invalida.');
    }

    const normalizedTasks = tasks.map((task, index) => ({
        ...task,
        task_id: String(task.task_id || index + 1),
        status: TASK_STATUS.PENDING,
        depends_on: Array.isArray(task.depends_on) ? task.depends_on.map(String) : task.depends_on || [],
        created_at: task.created_at || new Date().toISOString()
    }));

    validateOneTaskPerAgent(normalizedTasks);
    validateTaskDependencies(normalizedTasks);
    validateAllowedAgents(normalizedTasks, agents);
    return normalizedTasks;
}

function validateAllowedAgents(tasks, agents) {
    if (!agents) return;
    const allowed = new Set(agents.map(agent => agent.id));
    for (const task of tasks) {
        if (!allowed.has(task.agent_id)) throw new Error(`Agente nao permitido na jornada: ${task.agent_id}`);
    }
}

function validateOneTaskPerAgent(tasks) {
    const firstTaskByAgent = new Map();

    for (const task of tasks) {
        if (!task.agent_id || typeof task.agent_id !== 'string') {
            throw new Error(`Task "${task.task_id}" nao possui agent_id valido.`);
        }

        if (firstTaskByAgent.has(task.agent_id)) {
            const firstTaskId = firstTaskByAgent.get(task.agent_id);
            throw new Error(`Planejamento invalido: o agente "${task.agent_id}" recebeu mais de uma task no mesmo workflow (${firstTaskId} e ${task.task_id}). Consolide o objetivo em uma unica task para este agente.`);
        }

        firstTaskByAgent.set(task.agent_id, task.task_id);
    }
}

function validateTaskDependencies(tasks) {
    const taskIds = new Set(tasks.map(task => String(task.task_id)));
    if (taskIds.size !== tasks.length) throw new Error('task_id duplicado no planejamento.');

    for (const task of tasks) {
        if (!Array.isArray(task.depends_on)) {
            throw new Error(`Task "${task.task_id}" possui depends_on invalido.`);
        }

        for (const dependencyId of task.depends_on) {
            if (!taskIds.has(String(dependencyId))) {
                throw new Error(`Task "${task.task_id}" depende da task inexistente "${dependencyId}".`);
            }
        }
    }

    validateAcyclicDependencies(tasks);
}

function validateAcyclicDependencies(tasks) {
    const visited = new Set();
    const visiting = new Set();
    const byId = new Map(tasks.map(task => [task.task_id, task]));

    function visit(task) {
        if (visiting.has(task.task_id)) throw new Error('Ciclo nas dependencias de tarefas.');
        if (visited.has(task.task_id)) return;
        visiting.add(task.task_id);
        for (const id of task.depends_on) visit(byId.get(id));
        visiting.delete(task.task_id);
        visited.add(task.task_id);
    }

    tasks.forEach(visit);
}

module.exports = {
    normalizeTasks
};
