const { loadFileSubAgents } = require('./file_subagent_store');
const { validateAgentTools } = require('../providers');

function getSubAgentConfigs() {
    const agents = loadFileSubAgents();
    const ids = new Set();
    for (const agent of agents) {
        if (!agent.id || !agent.name || !agent.prompt) throw new Error('Agente precisa de id, name e prompt.');
        if (ids.has(agent.id)) throw new Error(`Agente duplicado: ${agent.id}`);
        ids.add(agent.id);
        validateAgentTools(agent);
    }
    return agents;
}

function getPublicSubAgents(agents = getSubAgentConfigs()) {
    return agents.map(({ id, name, description, category, capabilities }) => ({
        id, name, description, category, capabilities: capabilities || []
    }));
}

function findSubAgentConfig(agentId) {
    return getSubAgentConfigs().find(agent => agent.id === agentId);
}

function getSubAgentDisplayName(agentId) {
    return loadFileSubAgents().find(agent => agent.id === agentId)?.name || null;
}

module.exports = { getSubAgentConfigs, getPublicSubAgents, findSubAgentConfig, getSubAgentDisplayName };
