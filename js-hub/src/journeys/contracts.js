/**
 * Contratos de extensao das jornadas. Nao dependem de Express nem do formato JSON no disco.
 *
 * @typedef {(event: string, payload: Object) => void} Emit
 *
 * @typedef {Object} StartJourneyInput
 * @property {string} message
 * @property {string|null} [parentConversationId]
 * @property {Emit} [emit]
 * @property {string} [supersededWorkflowId]
 * @property {string} [supersededReason]
 *
 * @typedef {Object} ResumeJourneyInput
 * @property {string} workflowId
 * @property {string} message
 * @property {Emit} [emit]
 *
 * @typedef {Object} JourneyResult
 * @property {string} workflow_id
 * @property {string} parent_conversation_id
 * @property {string} status
 * @property {string} [message_to_user]
 * @property {boolean} [communicated]
 * @property {Object} [structured_response]
 *
 * @typedef {Object} PlannedTask
 * @property {string} task_id
 * @property {string} agent_id
 * @property {string} task_description
 * @property {string} enriched_content
 * @property {string} original_question
 * @property {string[]} depends_on
 *
 * @typedef {Object} TaskValidation
 * @property {'COMPLETED'|'ASK_HUMAN'|'RETRY'|'CANCELLED'} status
 * @property {string} feedback
 * @property {string} message_to_user
 *
 * @typedef {Object} TaskContext
 * @property {Object} workflow
 * @property {import('../repositories/workflow_repository').WorkflowRepository} workflowRepository
 * @property {Object[]} agents
 * @property {Object[]} parentConversationHistory
 * @property {Object} providerSessions
 * @property {string} [resumeMessage]
 * @property {string} [resumeTaskId]
 * @property {Object[]} [dependencyResults]
 *
 * @typedef {Object} JourneyServices
 * @property {import('../repositories/workflow_repository').WorkflowRepository} workflowRepository
 * @property {() => Object[]|Promise<Object[]>} getAgents
 * @property {ReturnType<typeof import('./workflow_lifecycle').createWorkflowLifecycle>} workflows
 *
 * @typedef {Object} Journey
 * @property {string} type
 * @property {(input: StartJourneyInput) => Promise<JourneyResult>} start
 * @property {(input: ResumeJourneyInput) => Promise<JourneyResult>} resume
 * @property {(payload: Object) => Promise<string>} [communicate]
 * @property {Record<string, string>} [messages]
 *
 * @typedef {Object} JourneyDefinition
 * @property {string} type
 * @property {(services: JourneyServices) => Journey} create
 *
 * @typedef {Object} TaskJourneyConfig
 * @property {string} type
 * @property {(message: string, emit: Emit, context: TaskContext) => Promise<PlannedTask[]>} plan
 * @property {(payload: Object, task: PlannedTask, context: TaskContext) => Promise<TaskValidation>} validate
 * @property {(payload: Object) => Promise<string>} communicate
 * @property {string[]} [agentIds]
 * @property {string[]} [agentCategories]
 * @property {string[]} [capabilities]
 * @property {(agents: Object[]) => Object[]} [selectAgents]
 * @property {number} [parallelism]
 * @property {number} [maxAttempts]
 * @property {Record<string, string>} [messages]
 *
 * @typedef {Object} CustomJourneyConfig
 * @property {string} type
 * @property {(input: StartJourneyInput, services: JourneyServices) => Promise<JourneyResult>} start
 * @property {(input: ResumeJourneyInput, services: JourneyServices) => Promise<JourneyResult>} resume
 * @property {(payload: Object) => Promise<string>} [communicate]
 * @property {Record<string, string>} [messages]
 */
module.exports = {};
