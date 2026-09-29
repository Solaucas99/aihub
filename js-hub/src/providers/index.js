const fs = require('fs');
const path = require('path');
const { createHash } = require('crypto');

const providers = new Map();

function registerProvider(provider) {
    if (!provider?.id || !provider.tools) throw new Error('Provider precisa de id e tools.');
    if (providers.has(provider.id)) throw new Error(`Provider duplicado: ${provider.id}`);
    for (const [name, tool] of Object.entries(provider.tools)) {
        if (tool.schema?.function?.name !== name || typeof tool.execute !== 'function') {
            throw new Error(`Tool ${name} precisa de schema e execute correspondentes.`);
        }
    }
    providers.set(provider.id, provider);
}

function getProvider(id) {
    const provider = providers.get(id);
    if (!provider) throw new Error(`Provider nao registrado: ${id}`);
    return provider;
}

function validateAgentTools(agent) {
    if (!Array.isArray(agent.tools)) throw new Error(`Agente ${agent.id} precisa de tools.`);
    if (!agent.tools.length) return;
    const provider = getProvider(agent.agent_provider_category);
    provider.validateConfig?.(agent.agent_provider_config || {});
    for (const name of agent.tools) {
        if (!Object.hasOwn(provider.tools, name)) throw new Error(`Tool ${name} nao existe no provider ${provider.id}.`);
    }
}

function bindAgentTools(agent, { sessions, saveSession, report, onToolCall }) {
    validateAgentTools(agent);
    if (!agent.tools.length) return {};
    const provider = getProvider(agent.agent_provider_category);
    const config = agent.agent_provider_config || {};
    const configKey = createHash('sha256').update(JSON.stringify(config)).digest('hex');
    return Object.fromEntries(agent.tools.map(name => {
        const tool = provider.tools[name];
        return [name, {
            schema: tool.schema,
            async execute(args) {
                await onToolCall(name);
                const saved = sessions[agent.id];
                const session = saved?.provider === provider.id && saved.config_key === configKey
                    ? saved.state : null;
                return tool.execute(args, {
                    config, session, report,
                    async saveSession(state) {
                        const value = { provider: provider.id, config_key: configKey, state };
                        sessions[agent.id] = value;
                        await saveSession(agent.id, value);
                    }
                });
            }
        }];
    }));
}

for (const entry of fs.readdirSync(__dirname, { withFileTypes: true })) {
    if (entry.isDirectory()) {
        const modulePath = path.join(__dirname, entry.name, 'index.js');
        if (fs.existsSync(modulePath)) registerProvider(require(modulePath));
    }
}

module.exports = { registerProvider, getProvider, validateAgentTools, bindAgentTools };
