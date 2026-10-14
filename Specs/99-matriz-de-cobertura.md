# 99 — Matriz de cobertura: arquivo do projeto → spec

> Gerada mecanicamente a partir do inventário real do projeto. Cada arquivo da árvore de `02-projeto-build-e-infra.md` aparece abaixo com a(s) spec(s) onde é tratado e a **forma**: *inteiro e literal* (o conteúdo completo está num bloco de código da spec e deve ser copiado byte a byte), *vazio*, *gerado* (arquivo produzido por ferramenta; regenerar conforme a spec 02) ou *binário* (copiar à parte).

| Arquivo | Spec(s) | Forma |
|---|---|---|
| `.gitattributes` | `02` | inteiro e literal |
| `.gitignore` | `02` | inteiro e literal |
| `.mvn/wrapper/maven-wrapper.properties` | `02` | inteiro e literal |
| `docker-compose.yaml` | `02` | inteiro e literal |
| `docs/data-model.md` | `12` | inteiro e literal |
| `docs/hub-ia.png` | `12` | binário (copiar à parte) |
| `docs/implementation-summary.md` | `12` | inteiro e literal |
| `docs/mermaid-diagram.png` | `12` | binário (copiar à parte) |
| `frontend/README.md` | `10` | inteiro e literal |
| `frontend/app.js` | `10` | inteiro e literal |
| `frontend/index.html` | `10` | inteiro e literal |
| `frontend/styles.css` | `10` | inteiro e literal |
| `logs/.gitkeep` | `00`, `02` | vazio |
| `mvnw` | `00`, `02`, `11`, `12` | gerado (regenerar) |
| `mvnw.cmd` | `00`, `02`, `12` | gerado (regenerar) |
| `pom.xml` | `02` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/HelloController.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/HubdeiaApplication.java` | `02` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/AuthHeaders.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/ConversationController.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunLogEntry.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunStatusResponse.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/dto/AgentRunSummaryResponse.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/dto/ConversationDetailResponse.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/dto/ConversationSummaryResponse.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/dto/CreateConversationRequest.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/dto/MessageAcceptedResponse.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/api/dto/SendConversationMessageRequest.java` | `05` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/ArtifactService.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/ArtifactView.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/ConversationMemorySanitizer.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/ConversationService.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/ConversationWorker.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/HumanInputService.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/MonotonicTime.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/TaskExecutionService.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/Text.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/WorkContextService.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/communication/CommunicatorAgent.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/content/TaskOutput.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/llm/OpenAiChatClient.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/orchestration/AgentRunOrchestrator.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/orchestration/AgentSkillAccessPolicy.java` | `04`, `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/orchestration/SubagentPromptBuilder.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/orchestration/SubagentTaskExecutor.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/orchestration/TaskDependencyResolver.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/orchestration/TaskExecutionResult.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/ConversationPlannerAgent.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/OpenAiPlannerClient.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlannedTask.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlannerAgent.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlannerDecision.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlannerDecisionHandler.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlanningContextState.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlanningInput.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlanningRunSummary.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlanningSubagentView.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlanningTaskSummary.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/PlanningWorkContextView.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/planner/WorkContextAction.java` | `06` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/ProgressReporter.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/ProviderTool.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolContext.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolRegistry.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/ProviderToolResult.java` | `08` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieAuthClient.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieClient.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieMessageResult.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieMessageTool.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieParsedResponse.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieQueryColumn.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieQueryResult.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieQueryResultClient.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieResponseParser.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/GenieTableRenderer.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/provider/genie/VisualizationSpecConverter.java` | `09` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/run/AgentRunService.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/run/AgentRunStore.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/validation/TaskValidationResult.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/validation/TaskValidationStatus.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/application/validation/TaskValidatorAgent.java` | `07` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/config/BootstrapConfiguration.java` | `04` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/config/CorsConfig.java` | `02` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/config/DotenvEnvironmentPostProcessor.java` | `02` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/config/HubRestClientFactory.java` | `02` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/AgentRun.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/AgentRunLog.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/AgentRunStatus.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Artifact.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/ArtifactStatus.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Conversation.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/ConversationWorkContext.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/ConversationWorkContextStatus.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/ExecutionMessage.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/FinalAnswerBlock.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/HumanInputRequest.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/HumanInputRequestStatus.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Message.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Provider.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/ProviderSession.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Skill.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Subagent.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/SubagentExecution.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/SubagentProvider.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Task.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/TaskStatus.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/TaskValidation.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Tool.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/ToolCall.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/Visual.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/VisualFormat.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/model/WorkContext.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/AgentRunRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/ArtifactRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/ConversationRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/ConversationWorkContextRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/HumanInputRequestRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/MessageRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/ProviderRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/ProviderSessionRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/SkillRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/SubagentExecutionRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/SubagentRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/ToolRepository.java` | `03` | inteiro e literal |
| `src/main/java/com/lamorim/hubdeia/domain/repository/WorkContextRepository.java` | `03` | inteiro e literal |
| `src/main/resources/META-INF/spring.factories` | `02` | inteiro e literal |
| `src/main/resources/application.yaml` | `02` | inteiro e literal |
| `src/main/resources/bootstrap/providers.json` | `04` | inteiro e literal |
| `src/main/resources/bootstrap/skills.json` | `04` | inteiro e literal |
| `src/main/resources/bootstrap/subagents.json` | `04` | inteiro e literal |
| `src/main/resources/bootstrap/tools.json` | `04` | inteiro e literal |
| `src/main/resources/bootstrap/work-contexts.json` | `04` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/HubdeiaApplicationTests.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/MongoIntegrationTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/ArtifactServiceTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/ConversationServiceTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/content/TaskOutputTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/orchestration/AgentRunOrchestratorTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/orchestration/AgentSkillAccessPolicyTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/orchestration/SubagentMemoryTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/orchestration/TaskDependencyResolverTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/planner/ConversationPlannerAgentTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/planner/OpenAiPlannerClientTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/planner/PlannerDecisionHandlerTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/planner/PlannerLiveEvalTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/application/run/AgentRunStoreTest.java` | `11` | inteiro e literal |
| `src/test/java/com/lamorim/hubdeia/config/BootstrapConfigurationTest.java` | `11` | inteiro e literal |
| `src/test/resources/planner-eval/page-view-conversation.json` | `11` | inteiro e literal |

## Totais

- binário (copiar à parte): **2** arquivo(s)
- gerado (regenerar): **2** arquivo(s)
- inteiro e literal: **143** arquivo(s)
- vazio: **1** arquivo(s)
- Total no inventário: **148** arquivos

## Fora do inventário (não replicar)

`js-hub/` (73 arquivos, Node.js legado), `scripts/seed-subagents.js`, `.idea/`, `.vscode/`, `.codex/`, `HELP.md`, `payload_genie*.json`, `target/`, `logs/*.log`, `.env` e `js-hub/.env`. Ver `12-docs-e-anexos.md` e `02-projeto-build-e-infra.md`.
