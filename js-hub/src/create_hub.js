const { WorkflowRepository } = require('./repositories/workflow_repository');
const { createJourneyRegistry, loadJourneyDefinitions } = require('./journeys/registry');
const { createJourneyRouter } = require('./journeys/router');
const { createWorkflowLifecycle } = require('./journeys/workflow_lifecycle');
const { createChatTurnService } = require('./services/chat_turn_service');
const { getSubAgentConfigs } = require('./subagents/registry');

/** Monta uma instancia do Hub. Jornadas e HTTP compartilham as mesmas dependencias. */
function createHub({
    workflowRepository = new WorkflowRepository(),
    journeyDefinitions = loadJourneyDefinitions(),
    getAgents = getSubAgentConfigs,
    getDefaultJourneyType = () => process.env.DEFAULT_JOURNEY_TYPE || 'INSIGHT_CHAT'
} = {}) {
    const services = Object.freeze({
        workflowRepository,
        getAgents,
        workflows: createWorkflowLifecycle(workflowRepository)
    });
    const journeys = createJourneyRegistry(journeyDefinitions, services);
    const routeJourney = createJourneyRouter(journeys, getDefaultJourneyType);
    const processChatTurn = createChatTurnService({ workflowRepository, journeys, routeJourney });

    return { workflowRepository, journeys, processChatTurn };
}

module.exports = { createHub };
