# Checklist de verificação (gerado do código)

> Para a IA que reconstrói o projeto: percorra **cada linha**, confirme que o arquivo existe no caminho indicado, que o comportamento descrito na spec foi implementado e que os testes da última coluna passam. Gerado mecanicamente a partir do projeto original.

## 1. Classes e interfaces de `src/main/java` (54)

| # | Tipo | Estereótipo | Caminho | Spec | Dependências injetadas | Testes que a exercitam | ✔ |
|---|---|---|---|---|---|---|---|
| 1 | class | RestController | `src/main/java/com/lamorim/hubdeia/HelloController.java` | 05 | — | — | ☐ |
| 2 | class | SpringBootApplication | `src/main/java/com/lamorim/hubdeia/HubdeiaApplication.java` | 02 | — | — | ☐ |
| 3 | class | Component | `src/main/java/com/lamorim/hubdeia/api/AuthHeaders.java` | 05 | — | — | ☐ |
| 4 | class | RestController | `src/main/java/com/lamorim/hubdeia/api/ConversationController.java` | 05 | AuthHeaders, ConversationService, AgentRunService, ArtifactService, ConversationWorker | — | ☐ |
| 5 | class | Service | `src/main/java/com/lamorim/hubdeia/application/ArtifactService.java` | 08 | ArtifactRepository, SubagentRepository, WorkContextService | AgentRunOrchestratorTest, ArtifactServiceTest, PlannerDecisionHandlerTest, SubagentMemoryTest | ☐ |
| 6 | class | Component | `src/main/java/com/lamorim/hubdeia/application/ConversationMemorySanitizer.java` | 06 | — | OpenAiPlannerClientTest, TaskOutputTest | ☐ |
| 7 | class | Service | `src/main/java/com/lamorim/hubdeia/application/ConversationService.java` | 06 | ConversationRepository, MessageRepository | AgentRunOrchestratorTest, ConversationPlannerAgentTest, ConversationServiceTest, PlannerDecisionHandlerTest | ☐ |
| 8 | class | Component | `src/main/java/com/lamorim/hubdeia/application/ConversationWorker.java` | 06 | ConversationService, PlannerAgent, PlannerDecisionHandler, AgentRunOrchestrator | — | ☐ |
| 9 | class | Service | `src/main/java/com/lamorim/hubdeia/application/HumanInputService.java` | 06 | HumanInputRequestRepository | AgentRunOrchestratorTest, PlannerDecisionHandlerTest | ☐ |
| 10 | class | — | `src/main/java/com/lamorim/hubdeia/application/MonotonicTime.java` | 06 | — | — | ☐ |
| 11 | class | Service | `src/main/java/com/lamorim/hubdeia/application/TaskExecutionService.java` | 07 | SubagentExecutionRepository, ProviderSessionRepository | SubagentMemoryTest | ☐ |
| 12 | class | — | `src/main/java/com/lamorim/hubdeia/application/Text.java` | 06 | — | — | ☐ |
| 13 | class | Service | `src/main/java/com/lamorim/hubdeia/application/WorkContextService.java` | 06 | WorkContextRepository, ConversationWorkContextRepository | AgentRunOrchestratorTest, PlannerDecisionHandlerTest | ☐ |
| 14 | class | Component | `src/main/java/com/lamorim/hubdeia/application/communication/CommunicatorAgent.java` | 07 | OpenAiChatClient | AgentRunOrchestratorTest | ☐ |
| 15 | class | Component | `src/main/java/com/lamorim/hubdeia/application/llm/OpenAiChatClient.java` | 08 | HubRestClientFactory | OpenAiPlannerClientTest | ☐ |
| 16 | class | Component | `src/main/java/com/lamorim/hubdeia/application/orchestration/AgentRunOrchestrator.java` | 07 | AgentRunService, SubagentTaskExecutor, TaskValidatorAgent, CommunicatorAgent, HumanInputService, ArtifactService, SubagentRepository, TaskDependencyResolver | AgentRunOrchestratorTest | ☐ |
| 17 | class | Component | `src/main/java/com/lamorim/hubdeia/application/orchestration/AgentSkillAccessPolicy.java` | 04, 08 | SkillRepository, ToolRepository | AgentSkillAccessPolicyTest | ☐ |
| 18 | class | Component | `src/main/java/com/lamorim/hubdeia/application/orchestration/SubagentPromptBuilder.java` | 08 | AgentSkillAccessPolicy, AgentRunService, WorkContextService, TaskExecutionService, ArtifactService | SubagentMemoryTest | ☐ |
| 19 | class | Component | `src/main/java/com/lamorim/hubdeia/application/orchestration/SubagentTaskExecutor.java` | 08 | OpenAiChatClient, SubagentRepository, AgentSkillAccessPolicy, ProviderToolRegistry, SubagentPromptBuilder, TaskExecutionService | AgentRunOrchestratorTest | ☐ |
| 20 | class | Component | `src/main/java/com/lamorim/hubdeia/application/orchestration/TaskDependencyResolver.java` | 07 | — | TaskDependencyResolverTest | ☐ |
| 21 | class | Component | `src/main/java/com/lamorim/hubdeia/application/planner/ConversationPlannerAgent.java` | 06 | ConversationService, WorkContextService, SubagentRepository, HumanInputService, ArtifactService, AgentRunService, OpenAiPlannerClient | ConversationPlannerAgentTest | ☐ |
| 22 | class | Component | `src/main/java/com/lamorim/hubdeia/application/planner/OpenAiPlannerClient.java` | 06 | OpenAiChatClient, ConversationMemorySanitizer | ConversationPlannerAgentTest, OpenAiPlannerClientTest, PlannerLiveEvalTest | ☐ |
| 23 | interface | — | `src/main/java/com/lamorim/hubdeia/application/planner/PlannerAgent.java` | 06 | — | — | ☐ |
| 24 | class | Component | `src/main/java/com/lamorim/hubdeia/application/planner/PlannerDecisionHandler.java` | 06 | ConversationService, WorkContextService, HumanInputService, ArtifactService, AgentRunService | PlannerDecisionHandlerTest | ☐ |
| 25 | interface | — | `src/main/java/com/lamorim/hubdeia/application/provider/ProgressReporter.java` | 08 | — | — | ☐ |
| 26 | interface | — | `src/main/java/com/lamorim/hubdeia/application/provider/ProviderTool.java` | 08 | — | — | ☐ |
| 27 | class | Component | `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolRegistry.java` | 08 | ProviderTool | — | ☐ |
| 28 | class | Component | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieAuthClient.java` | 09 | HubRestClientFactory | — | ☐ |
| 29 | class | Component | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieClient.java` | 09 | HubRestClientFactory, GenieAuthClient | — | ☐ |
| 30 | class | Component | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieMessageTool.java` | 09 | GenieClient, GenieResponseParser | — | ☐ |
| 31 | class | Component | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieQueryResultClient.java` | 09 | HubRestClientFactory, GenieAuthClient | — | ☐ |
| 32 | class | Component | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieResponseParser.java` | 09 | GenieQueryResultClient, GenieTableRenderer, VisualizationSpecConverter | — | ☐ |
| 33 | class | Component | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieTableRenderer.java` | 09 | — | — | ☐ |
| 34 | class | Component | `src/main/java/com/lamorim/hubdeia/application/provider/genie/VisualizationSpecConverter.java` | 09 | OpenAiChatClient | — | ☐ |
| 35 | class | Service | `src/main/java/com/lamorim/hubdeia/application/run/AgentRunService.java` | 07 | AgentRunStore, AgentRunRepository, ConversationService, TaskExecutionService, WorkContextService | AgentRunOrchestratorTest, PlannerDecisionHandlerTest | ☐ |
| 36 | class | Component | `src/main/java/com/lamorim/hubdeia/application/run/AgentRunStore.java` | 07 | — | AgentRunStoreTest, ConversationPlannerAgentTest, SubagentMemoryTest | ☐ |
| 37 | class | Component | `src/main/java/com/lamorim/hubdeia/application/validation/TaskValidatorAgent.java` | 07 | OpenAiChatClient, ConversationService, TaskExecutionService | AgentRunOrchestratorTest | ☐ |
| 38 | class | Configuration | `src/main/java/com/lamorim/hubdeia/config/BootstrapConfiguration.java` | 04 | — | BootstrapConfigurationTest | ☐ |
| 39 | class | Configuration | `src/main/java/com/lamorim/hubdeia/config/CorsConfig.java` | 02 | — | — | ☐ |
| 40 | class | — | `src/main/java/com/lamorim/hubdeia/config/DotenvEnvironmentPostProcessor.java` | 02 | — | — | ☐ |
| 41 | class | Component | `src/main/java/com/lamorim/hubdeia/config/HubRestClientFactory.java` | 02 | — | — | ☐ |
| 42 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/AgentRunRepository.java` | 03 | — | — | ☐ |
| 43 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/ArtifactRepository.java` | 03 | — | — | ☐ |
| 44 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/ConversationRepository.java` | 03 | — | — | ☐ |
| 45 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/ConversationWorkContextRepository.java` | 03 | — | — | ☐ |
| 46 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/HumanInputRequestRepository.java` | 03 | — | — | ☐ |
| 47 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/MessageRepository.java` | 03 | — | — | ☐ |
| 48 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/ProviderRepository.java` | 03 | — | BootstrapConfigurationTest | ☐ |
| 49 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/ProviderSessionRepository.java` | 03 | — | — | ☐ |
| 50 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/SkillRepository.java` | 03 | — | AgentSkillAccessPolicyTest, BootstrapConfigurationTest | ☐ |
| 51 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/SubagentExecutionRepository.java` | 03 | — | — | ☐ |
| 52 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/SubagentRepository.java` | 03 | — | AgentRunOrchestratorTest, ArtifactServiceTest, BootstrapConfigurationTest, PlannerDecisionHandlerTest, SubagentMemoryTest | ☐ |
| 53 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/ToolRepository.java` | 03 | — | AgentSkillAccessPolicyTest, BootstrapConfigurationTest | ☐ |
| 54 | interface | Repository | `src/main/java/com/lamorim/hubdeia/domain/repository/WorkContextRepository.java` | 03 | — | AgentRunOrchestratorTest, BootstrapConfigurationTest, PlannerDecisionHandlerTest | ☐ |

## 2. Records e enums de `domain/model` (55)

| # | Tipo | Coleção | Caminho | Spec | ✔ |
|---|---|---|---|---|---|
| 1 | record | `agent_runs` | `src/main/java/com/lamorim/hubdeia/domain/model/AgentRun.java` | 03 | ☐ |
| 2 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/AgentRunLog.java` | 03 | ☐ |
| 3 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunLogEntry.java` | 05 | ☐ |
| 4 | enum | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/AgentRunStatus.java` | 03 | ☐ |
| 5 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunStatusResponse.java` | 05 | ☐ |
| 6 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunSummaryResponse.java` | 05 | ☐ |
| 7 | record | `artifacts` | `src/main/java/com/lamorim/hubdeia/domain/model/Artifact.java` | 03 | ☐ |
| 8 | enum | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/ArtifactStatus.java` | 03 | ☐ |
| 9 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/ArtifactView.java` | 08 | ☐ |
| 10 | record | `conversations` | `src/main/java/com/lamorim/hubdeia/domain/model/Conversation.java` | 03 | ☐ |
| 11 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/api/dto/ConversationDetailResponse.java` | 05 | ☐ |
| 12 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/api/dto/ConversationSummaryResponse.java` | 05 | ☐ |
| 13 | record | `conversation_work_contexts` | `src/main/java/com/lamorim/hubdeia/domain/model/ConversationWorkContext.java` | 03 | ☐ |
| 14 | enum | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/ConversationWorkContextStatus.java` | 03 | ☐ |
| 15 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/api/dto/CreateConversationRequest.java` | 05 | ☐ |
| 16 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/ExecutionMessage.java` | 03 | ☐ |
| 17 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/FinalAnswerBlock.java` | 03 | ☐ |
| 18 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieMessageResult.java` | 09 | ☐ |
| 19 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieParsedResponse.java` | 09 | ☐ |
| 20 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieQueryColumn.java` | 09 | ☐ |
| 21 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieQueryResult.java` | 09 | ☐ |
| 22 | record | `human_input_requests` | `src/main/java/com/lamorim/hubdeia/domain/model/HumanInputRequest.java` | 03 | ☐ |
| 23 | enum | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/HumanInputRequestStatus.java` | 03 | ☐ |
| 24 | record | `messages` | `src/main/java/com/lamorim/hubdeia/domain/model/Message.java` | 03 | ☐ |
| 25 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/api/dto/MessageAcceptedResponse.java` | 05 | ☐ |
| 26 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/PlannedTask.java` | 06 | ☐ |
| 27 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/PlannerDecision.java` | 06 | ☐ |
| 28 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/PlanningContextState.java` | 06 | ☐ |
| 29 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/PlanningInput.java` | 06 | ☐ |
| 30 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/PlanningRunSummary.java` | 06 | ☐ |
| 31 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/PlanningSubagentView.java` | 06 | ☐ |
| 32 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/PlanningTaskSummary.java` | 06 | ☐ |
| 33 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/PlanningWorkContextView.java` | 06 | ☐ |
| 34 | record | `providers` | `src/main/java/com/lamorim/hubdeia/domain/model/Provider.java` | 03 | ☐ |
| 35 | record | `provider_sessions` | `src/main/java/com/lamorim/hubdeia/domain/model/ProviderSession.java` | 03 | ☐ |
| 36 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolContext.java` | 08 | ☐ |
| 37 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolResult.java` | 08 | ☐ |
| 38 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/api/dto/SendConversationMessageRequest.java` | 05 | ☐ |
| 39 | record | `skills` | `src/main/java/com/lamorim/hubdeia/domain/model/Skill.java` | 03 | ☐ |
| 40 | record | `subagents` | `src/main/java/com/lamorim/hubdeia/domain/model/Subagent.java` | 03 | ☐ |
| 41 | record | `task_executions` | `src/main/java/com/lamorim/hubdeia/domain/model/SubagentExecution.java` | 03 | ☐ |
| 42 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/SubagentProvider.java` | 03 | ☐ |
| 43 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/Task.java` | 03 | ☐ |
| 44 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/orchestration/TaskExecutionResult.java` | 07 | ☐ |
| 45 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/content/TaskOutput.java` | 08 | ☐ |
| 46 | enum | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/TaskStatus.java` | 03 | ☐ |
| 47 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/TaskValidation.java` | 03 | ☐ |
| 48 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/validation/TaskValidationResult.java` | 07 | ☐ |
| 49 | enum | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/validation/TaskValidationStatus.java` | 07 | ☐ |
| 50 | record | `tools` | `src/main/java/com/lamorim/hubdeia/domain/model/Tool.java` | 03 | ☐ |
| 51 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/ToolCall.java` | 03 | ☐ |
| 52 | record | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/Visual.java` | 03 | ☐ |
| 53 | enum | embutido/valor | `src/main/java/com/lamorim/hubdeia/domain/model/VisualFormat.java` | 03 | ☐ |
| 54 | record | `work_contexts` | `src/main/java/com/lamorim/hubdeia/domain/model/WorkContext.java` | 03 | ☐ |
| 55 | enum | embutido/valor | `src/main/java/com/lamorim/hubdeia/application/planner/WorkContextAction.java` | 06 | ☐ |

## 3. Demais arquivos (24)

| # | Caminho | Spec | Forma | ✔ |
|---|---|---|---|---|
| 1 | `.gitattributes` | 02 | inteiro e literal | ☐ |
| 2 | `.gitignore` | 02 | inteiro e literal | ☐ |
| 3 | `.mvn/wrapper/maven-wrapper.properties` | 02 | inteiro e literal | ☐ |
| 4 | `docker-compose.yaml` | 02 | inteiro e literal | ☐ |
| 5 | `docs/data-model.md` | 12 | inteiro e literal | ☐ |
| 6 | `docs/hub-ia.png` | 12 | binário (copiar à parte) | ☐ |
| 7 | `docs/implementation-summary.md` | 12 | inteiro e literal | ☐ |
| 8 | `docs/mermaid-diagram.png` | 12 | binário (copiar à parte) | ☐ |
| 9 | `frontend/README.md` | 10 | inteiro e literal | ☐ |
| 10 | `frontend/app.js` | 10 | inteiro e literal | ☐ |
| 11 | `frontend/index.html` | 10 | inteiro e literal | ☐ |
| 12 | `frontend/styles.css` | 10 | inteiro e literal | ☐ |
| 13 | `logs/.gitkeep` | 00, 02 | vazio | ☐ |
| 14 | `mvnw` | 00, 02, 11, 12 | gerado (regenerar) | ☐ |
| 15 | `mvnw.cmd` | 00, 02, 12 | gerado (regenerar) | ☐ |
| 16 | `pom.xml` | 02 | inteiro e literal | ☐ |
| 17 | `src/main/resources/META-INF/spring.factories` | 02 | inteiro e literal | ☐ |
| 18 | `src/main/resources/application.yaml` | 02 | inteiro e literal | ☐ |
| 19 | `src/main/resources/bootstrap/providers.json` | 04 | inteiro e literal | ☐ |
| 20 | `src/main/resources/bootstrap/skills.json` | 04 | inteiro e literal | ☐ |
| 21 | `src/main/resources/bootstrap/subagents.json` | 04 | inteiro e literal | ☐ |
| 22 | `src/main/resources/bootstrap/tools.json` | 04 | inteiro e literal | ☐ |
| 23 | `src/main/resources/bootstrap/work-contexts.json` | 04 | inteiro e literal | ☐ |
| 24 | `src/test/resources/planner-eval/page-view-conversation.json` | 11 | inteiro e literal | ☐ |

## 4. Classes de teste (15)

| # | Caminho | Nº de testes | ✔ |
|---|---|---|---|
| 1 | `src/test/java/com/lamorim/hubdeia/HubdeiaApplicationTests.java` | 1 | ☐ |
| 2 | `src/test/java/com/lamorim/hubdeia/MongoIntegrationTest.java` | 0 | ☐ |
| 3 | `src/test/java/com/lamorim/hubdeia/application/ArtifactServiceTest.java` | 6 | ☐ |
| 4 | `src/test/java/com/lamorim/hubdeia/application/ConversationServiceTest.java` | 4 | ☐ |
| 5 | `src/test/java/com/lamorim/hubdeia/application/content/TaskOutputTest.java` | 7 | ☐ |
| 6 | `src/test/java/com/lamorim/hubdeia/application/orchestration/AgentRunOrchestratorTest.java` | 9 | ☐ |
| 7 | `src/test/java/com/lamorim/hubdeia/application/orchestration/AgentSkillAccessPolicyTest.java` | 1 | ☐ |
| 8 | `src/test/java/com/lamorim/hubdeia/application/orchestration/SubagentMemoryTest.java` | 7 | ☐ |
| 9 | `src/test/java/com/lamorim/hubdeia/application/orchestration/TaskDependencyResolverTest.java` | 7 | ☐ |
| 10 | `src/test/java/com/lamorim/hubdeia/application/planner/ConversationPlannerAgentTest.java` | 4 | ☐ |
| 11 | `src/test/java/com/lamorim/hubdeia/application/planner/OpenAiPlannerClientTest.java` | 5 | ☐ |
| 12 | `src/test/java/com/lamorim/hubdeia/application/planner/PlannerDecisionHandlerTest.java` | 10 | ☐ |
| 13 | `src/test/java/com/lamorim/hubdeia/application/planner/PlannerLiveEvalTest.java` | 5 | ☐ |
| 14 | `src/test/java/com/lamorim/hubdeia/application/run/AgentRunStoreTest.java` | 3 | ☐ |
| 15 | `src/test/java/com/lamorim/hubdeia/config/BootstrapConfigurationTest.java` | 3 | ☐ |

## 5. Portões de verificação (rodar nesta ordem)

| Portão | Comando | Resultado esperado |
|---|---|---|
| G1 compila | `./mvnw -q -DskipTests test-compile` | sem erro |
| G2 MongoDB no ar | `docker compose up -d` | porta 27017 aceitando conexões |
| G3 modelo de dados | `mongosh ... --file Modelling/04-verify.js` | `OK: colecoes, indices e catalogo conferem.` |
| G4 testes | `./mvnw test` | `Tests run: 72, Failures: 0, Errors: 0, Skipped: 5` |
| G5 app sobe | `./mvnw spring-boot:run` e `curl localhost:8080/hello` | `Hello World!` |
| G6 catálogo | `curl -s localhost:8080/conversations -H 'Authorization: u1'` | `[]` (200) |
| G7 fluxo real | pergunta de dados no frontend | resposta com artefato em `GET /conversations/{id}/artifacts` |
