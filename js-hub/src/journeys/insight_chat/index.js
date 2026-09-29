const { defineJourney } = require('../define_journey');
const { plannerAgent } = require('../../agents/planner_agent');
const { validatorAgent } = require('../../agents/validator_agent');
const { communicatorAgent } = require('../../agents/communicator_agent');

// As politicas atuais sao explicitas; outra jornada pode fornecer as suas.
module.exports = defineJourney({
    type: 'INSIGHT_CHAT',
    plan: plannerAgent,
    validate: validatorAgent,
    communicate: communicatorAgent,
    messages: {
        followup: 'Vou abrir uma nova analise dentro desta conversa.',
        superseded: 'Vou tratar sua nova pergunta como uma nova analise, mantendo o contexto da conversa.',
        resume: 'Sua mensagem responde a pergunta pendente. Vou continuar a analise.',
        newRequest: 'Sua mensagem parece uma nova solicitacao. Vou iniciar uma nova analise no mesmo contexto.'
    }
});
