const { createTaskJourney } = require('./task_journey');

/** @param {import('./contracts').TaskJourneyConfig|import('./contracts').CustomJourneyConfig} config
 * @returns {import('./contracts').JourneyDefinition}
 */
function defineJourney(config = {}) {
    assertJourneyType(config.type);

    const custom = isCustomJourney(config);
    if (custom) validateCustomJourney(config);
    else assertTaskPolicies(config);

    // Registra uma definicao; o bootstrap fornece servicos ao criar cada instancia.
    return Object.freeze({
        type: config.type,
        create(services) {
            if (custom) {
                return {
                    ...config,
                    start: input => config.start(input, services),
                    resume: input => config.resume(input, services)
                };
            }
            return createTaskJourney({
                ...config,
                selectAgents: config.selectAgents || createAgentSelector(config)
            }, services);
        }
    });
}

function assertTaskPolicies(config) {
    for (const policy of ['plan', 'validate', 'communicate']) {
        if (typeof config[policy] !== 'function') {
            throw new Error(`Jornada "${config.type}" precisa declarar ${policy}.`);
        }
    }
}

function isCustomJourney(config) {
    return typeof config.start === 'function' || typeof config.resume === 'function';
}

function validateCustomJourney(config) {
    if (typeof config.start !== 'function' || typeof config.resume !== 'function') {
        throw new Error(`Jornada customizada "${config.type}" precisa de start e resume.`);
    }

    return config;
}

function assertJourneyType(type) {
    if (!type || typeof type !== 'string') {
        throw new Error('Jornada precisa declarar type como string.');
    }
}

function createAgentSelector({
    agentIds = null,
    agentCategories = null,
    capabilities = null
} = {}) {
    return agents => agents.filter(agent => {
        return matchesList(agent.id, agentIds)
            && matchesList(agent.category, agentCategories)
            && matchesCapabilities(agent.capabilities || [], capabilities);
    });
}

function matchesList(value, allowedValues) {
    if (!Array.isArray(allowedValues) || !allowedValues.length) return true;
    return allowedValues.includes(value);
}

function matchesCapabilities(agentCapabilities, requiredCapabilities) {
    if (!Array.isArray(requiredCapabilities) || !requiredCapabilities.length) return true;
    return requiredCapabilities.every(capability => agentCapabilities.includes(capability));
}

module.exports = { defineJourney };
