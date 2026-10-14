# 02 — Projeto Maven, infraestrutura, configuração e árvore de arquivos

> Spec do projeto **Hub de IA** (pacote base `com.lamorim.hubdeia`). Leia antes `00-LEIA-ME.md`.

## Objetivo

Definir tudo o que é necessário para o projeto existir, compilar, configurar e subir numa máquina nova, **antes** de qualquer classe de negócio: a árvore exata de arquivos, o `pom.xml`, o `application.yaml`, a infraestrutura Docker (MongoDB), os arquivos de controle do repositório, o carregamento de `.env`, a fábrica de clientes HTTP e o CORS.

## Arquivos cobertos

- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- `docker-compose.yaml`, `.gitignore`, `.gitattributes`, `logs/.gitkeep`
- `src/main/resources/application.yaml`, `src/main/resources/META-INF/spring.factories`
- `src/main/java/com/lamorim/hubdeia/HubdeiaApplication.java`
- `src/main/java/com/lamorim/hubdeia/config/DotenvEnvironmentPostProcessor.java`
- `src/main/java/com/lamorim/hubdeia/config/HubRestClientFactory.java`
- `src/main/java/com/lamorim/hubdeia/config/CorsConfig.java`

(`BootstrapConfiguration.java` pertence a `04-catalogo-e-bootstrap.md`.)

## Depende de

Nada. É a primeira spec a ser implementada.

## 1. Pré-requisitos da máquina

| Item | Versão / detalhe |
|---|---|
| JDK | **21** (o código usa `List.getLast()`, `List.reversed()`, `Executors.newVirtualThreadPerTaskExecutor()`, `try`-with-resources em `ExecutorService`, `switch` com `->`, pattern matching de `instanceof` e *records*) |
| Maven | Não precisa instalar: o projeto traz o **Maven Wrapper** (`./mvnw`, `mvnw.cmd`) que baixa o Maven **3.9.16** |
| Docker | Para o MongoDB (`mongo:8`) via `docker compose` |
| Chave OpenAI | Variável `OPENAI_API_KEY` (necessária para o planejador, subagentes, validador e comunicador reais; os testes automáticos **não** precisam dela) |
| Credenciais Genie | Opcionais: `GENIE_*` (necessárias só para consultar o provider Genie de verdade) |
| Sistema | Funciona em Windows, Linux e macOS. O repositório original foi desenvolvido em Windows 11 |

## 2. Árvore completa de arquivos do projeto

Esta é a lista **exata** dos arquivos do projeto replicado (nada a mais, nada a menos). Arquivos binários e fora de escopo estão listados na seção 9.

```text
hubdeia/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── docs/
│   ├── data-model.md
│   ├── hub-ia.png
│   ├── implementation-summary.md
│   └── mermaid-diagram.png
├── frontend/
│   ├── README.md
│   ├── app.js
│   ├── index.html
│   └── styles.css
├── logs/
│   └── .gitkeep
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── lamorim/
│   │   │           └── hubdeia/
│   │   │               ├── api/
│   │   │               │   ├── dto/
│   │   │               │   │   ├── AgentRunLogEntry.java
│   │   │               │   │   ├── AgentRunStatusResponse.java
│   │   │               │   │   ├── AgentRunSummaryResponse.java
│   │   │               │   │   ├── ConversationDetailResponse.java
│   │   │               │   │   ├── ConversationSummaryResponse.java
│   │   │               │   │   ├── CreateConversationRequest.java
│   │   │               │   │   ├── MessageAcceptedResponse.java
│   │   │               │   │   └── SendConversationMessageRequest.java
│   │   │               │   ├── AuthHeaders.java
│   │   │               │   └── ConversationController.java
│   │   │               ├── application/
│   │   │               │   ├── communication/
│   │   │               │   │   └── CommunicatorAgent.java
│   │   │               │   ├── content/
│   │   │               │   │   └── TaskOutput.java
│   │   │               │   ├── llm/
│   │   │               │   │   └── OpenAiChatClient.java
│   │   │               │   ├── orchestration/
│   │   │               │   │   ├── AgentRunOrchestrator.java
│   │   │               │   │   ├── AgentSkillAccessPolicy.java
│   │   │               │   │   ├── SubagentPromptBuilder.java
│   │   │               │   │   ├── SubagentTaskExecutor.java
│   │   │               │   │   ├── TaskDependencyResolver.java
│   │   │               │   │   └── TaskExecutionResult.java
│   │   │               │   ├── planner/
│   │   │               │   │   ├── ConversationPlannerAgent.java
│   │   │               │   │   ├── OpenAiPlannerClient.java
│   │   │               │   │   ├── PlannedTask.java
│   │   │               │   │   ├── PlannerAgent.java
│   │   │               │   │   ├── PlannerDecision.java
│   │   │               │   │   ├── PlannerDecisionHandler.java
│   │   │               │   │   ├── PlanningContextState.java
│   │   │               │   │   ├── PlanningInput.java
│   │   │               │   │   ├── PlanningRunSummary.java
│   │   │               │   │   ├── PlanningSubagentView.java
│   │   │               │   │   ├── PlanningTaskSummary.java
│   │   │               │   │   ├── PlanningWorkContextView.java
│   │   │               │   │   └── WorkContextAction.java
│   │   │               │   ├── provider/
│   │   │               │   │   ├── genie/
│   │   │               │   │   │   ├── GenieAuthClient.java
│   │   │               │   │   │   ├── GenieClient.java
│   │   │               │   │   │   ├── GenieMessageResult.java
│   │   │               │   │   │   ├── GenieMessageTool.java
│   │   │               │   │   │   ├── GenieParsedResponse.java
│   │   │               │   │   │   ├── GenieQueryColumn.java
│   │   │               │   │   │   ├── GenieQueryResult.java
│   │   │               │   │   │   ├── GenieQueryResultClient.java
│   │   │               │   │   │   ├── GenieResponseParser.java
│   │   │               │   │   │   ├── GenieTableRenderer.java
│   │   │               │   │   │   └── VisualizationSpecConverter.java
│   │   │               │   │   ├── ProgressReporter.java
│   │   │               │   │   ├── ProviderTool.java
│   │   │               │   │   ├── ProviderToolContext.java
│   │   │               │   │   ├── ProviderToolRegistry.java
│   │   │               │   │   └── ProviderToolResult.java
│   │   │               │   ├── run/
│   │   │               │   │   ├── AgentRunService.java
│   │   │               │   │   └── AgentRunStore.java
│   │   │               │   ├── validation/
│   │   │               │   │   ├── TaskValidationResult.java
│   │   │               │   │   ├── TaskValidationStatus.java
│   │   │               │   │   └── TaskValidatorAgent.java
│   │   │               │   ├── ArtifactService.java
│   │   │               │   ├── ArtifactView.java
│   │   │               │   ├── ConversationMemorySanitizer.java
│   │   │               │   ├── ConversationService.java
│   │   │               │   ├── ConversationWorker.java
│   │   │               │   ├── HumanInputService.java
│   │   │               │   ├── MonotonicTime.java
│   │   │               │   ├── TaskExecutionService.java
│   │   │               │   ├── Text.java
│   │   │               │   └── WorkContextService.java
│   │   │               ├── config/
│   │   │               │   ├── BootstrapConfiguration.java
│   │   │               │   ├── CorsConfig.java
│   │   │               │   ├── DotenvEnvironmentPostProcessor.java
│   │   │               │   └── HubRestClientFactory.java
│   │   │               ├── domain/
│   │   │               │   ├── model/
│   │   │               │   │   ├── AgentRun.java
│   │   │               │   │   ├── AgentRunLog.java
│   │   │               │   │   ├── AgentRunStatus.java
│   │   │               │   │   ├── Artifact.java
│   │   │               │   │   ├── ArtifactStatus.java
│   │   │               │   │   ├── Conversation.java
│   │   │               │   │   ├── ConversationWorkContext.java
│   │   │               │   │   ├── ConversationWorkContextStatus.java
│   │   │               │   │   ├── ExecutionMessage.java
│   │   │               │   │   ├── FinalAnswerBlock.java
│   │   │               │   │   ├── HumanInputRequest.java
│   │   │               │   │   ├── HumanInputRequestStatus.java
│   │   │               │   │   ├── Message.java
│   │   │               │   │   ├── Provider.java
│   │   │               │   │   ├── ProviderSession.java
│   │   │               │   │   ├── Skill.java
│   │   │               │   │   ├── Subagent.java
│   │   │               │   │   ├── SubagentExecution.java
│   │   │               │   │   ├── SubagentProvider.java
│   │   │               │   │   ├── Task.java
│   │   │               │   │   ├── TaskStatus.java
│   │   │               │   │   ├── TaskValidation.java
│   │   │               │   │   ├── Tool.java
│   │   │               │   │   ├── ToolCall.java
│   │   │               │   │   ├── Visual.java
│   │   │               │   │   ├── VisualFormat.java
│   │   │               │   │   └── WorkContext.java
│   │   │               │   └── repository/
│   │   │               │       ├── AgentRunRepository.java
│   │   │               │       ├── ArtifactRepository.java
│   │   │               │       ├── ConversationRepository.java
│   │   │               │       ├── ConversationWorkContextRepository.java
│   │   │               │       ├── HumanInputRequestRepository.java
│   │   │               │       ├── MessageRepository.java
│   │   │               │       ├── ProviderRepository.java
│   │   │               │       ├── ProviderSessionRepository.java
│   │   │               │       ├── SkillRepository.java
│   │   │               │       ├── SubagentExecutionRepository.java
│   │   │               │       ├── SubagentRepository.java
│   │   │               │       ├── ToolRepository.java
│   │   │               │       └── WorkContextRepository.java
│   │   │               ├── HelloController.java
│   │   │               └── HubdeiaApplication.java
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── spring.factories
│   │       ├── bootstrap/
│   │       │   ├── providers.json
│   │       │   ├── skills.json
│   │       │   ├── subagents.json
│   │       │   ├── tools.json
│   │       │   └── work-contexts.json
│   │       └── application.yaml
│   └── test/
│       ├── java/
│       │   └── com/
│       │       └── lamorim/
│       │           └── hubdeia/
│       │               ├── application/
│       │               │   ├── content/
│       │               │   │   └── TaskOutputTest.java
│       │               │   ├── orchestration/
│       │               │   │   ├── AgentRunOrchestratorTest.java
│       │               │   │   ├── AgentSkillAccessPolicyTest.java
│       │               │   │   ├── SubagentMemoryTest.java
│       │               │   │   └── TaskDependencyResolverTest.java
│       │               │   ├── planner/
│       │               │   │   ├── ConversationPlannerAgentTest.java
│       │               │   │   ├── OpenAiPlannerClientTest.java
│       │               │   │   ├── PlannerDecisionHandlerTest.java
│       │               │   │   └── PlannerLiveEvalTest.java
│       │               │   ├── run/
│       │               │   │   └── AgentRunStoreTest.java
│       │               │   ├── ArtifactServiceTest.java
│       │               │   └── ConversationServiceTest.java
│       │               ├── config/
│       │               │   └── BootstrapConfigurationTest.java
│       │               ├── HubdeiaApplicationTests.java
│       │               └── MongoIntegrationTest.java
│       └── resources/
│           └── planner-eval/
│               └── page-view-conversation.json
├── .gitattributes
├── .gitignore
├── docker-compose.yaml
├── mvnw
├── mvnw.cmd
└── pom.xml
```

Cada arquivo é detalhado na spec indicada:

| Caminho | Conteúdo | Spec |
|---|---|---|
| `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Build Maven | 02 |
| `docker-compose.yaml`, `.gitignore`, `.gitattributes`, `logs/.gitkeep` | Infra e controle do repositório | 02 |
| `src/main/resources/application.yaml`, `src/main/resources/META-INF/spring.factories` | Configuração | 02 |
| `src/main/java/com/lamorim/hubdeia/HubdeiaApplication.java` | Classe principal | 02 |
| `src/main/java/com/lamorim/hubdeia/config/*` | Dotenv, REST client, CORS (02); Bootstrap (04) | 02 / 04 |
| `src/main/java/com/lamorim/hubdeia/HelloController.java`, `api/**` | API REST | 05 |
| `src/main/java/com/lamorim/hubdeia/domain/**` | Modelo Mongo e repositórios | 03 |
| `src/main/resources/bootstrap/*.json`, `application/orchestration/AgentSkillAccessPolicy.java` | Catálogo semente e política de acesso | 04 |
| `application/ConversationService`, `ConversationWorker`, `MonotonicTime`, `Text`, `ConversationMemorySanitizer`, `WorkContextService`, `HumanInputService`, `application/planner/**` | Conversa e planejador | 06 |
| `application/run/**`, `AgentRunOrchestrator`, `TaskDependencyResolver`, `TaskExecutionService`, `TaskExecutionResult`, `application/validation/**`, `application/communication/**` | Runs, validação, comunicador | 07 |
| `SubagentTaskExecutor`, `SubagentPromptBuilder`, `application/llm/**`, `application/content/**`, `ArtifactService`, `ArtifactView`, `application/provider/*` (sem `genie`) | Execução do subagente e conteúdo | 08 |
| `application/provider/genie/**` | Provider Genie | 09 |
| `frontend/**` | Interface web | 10 |
| `src/test/**` | Testes | 11 |
| `docs/*.md` | Documentação do repositório | 12 |

## 3. `pom.xml`

**Arquivo `pom.xml`** (literal):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.lamorim</groupId>
	<artifactId>hubdeia</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name/>
	<description/>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>21</java.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-mongodb</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-restclient</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-databind</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-mongodb-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-restclient-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>
```

Pontos que **não** podem ser alterados:

- Parent `spring-boot-starter-parent` **4.1.1**; `groupId` `com.lamorim`, `artifactId` `hubdeia`, `version` `0.0.1-SNAPSHOT`; `java.version` **21**.
- Os elementos de metadado vazios (`<name/>`, `<description/>`, `<url/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>`, `<scm>` com filhos vazios) são do gerador Spring Initializr e **ficam como estão**.
- Dependências de runtime (todas sem versão, gerenciadas pelo parent): `spring-boot-starter-data-mongodb` (Spring Data MongoDB, `MongoTemplate`, repositórios), `spring-boot-starter-restclient` (`RestClient`), `spring-boot-starter-webmvc` (MVC e Tomcat), `com.fasterxml.jackson.core:jackson-databind` (declarada explicitamente porque `BootstrapConfiguration` cria seu próprio `ObjectMapper` com `com.fasterxml.jackson.databind`).
- Dependências de teste (escopo `test`): `spring-boot-starter-data-mongodb-test`, `spring-boot-starter-restclient-test`, `spring-boot-starter-webmvc-test`. Elas trazem transitivamente JUnit 5, AssertJ e Mockito; **não** declare `spring-boot-starter-test`.
- **Não há** dependência de Redis nem de qualquer outro armazenamento: o MongoDB é o único repositório de dados.
- Plugin de build: apenas `spring-boot-maven-plugin`.

## 4. `application.yaml`

**Arquivo `src/main/resources/application.yaml`** (literal):

```yaml
spring:
  application:
    name: hubdeia
  jackson:
    property-naming-strategy: SNAKE_CASE
  mongodb:
    uri: ${MONGODB_URI:mongodb://admin:admin123@localhost:27017/appdb?authSource=admin}
  data:
    mongodb:
      auto-index-creation: true

logging:
  file:
    name: ${HUB_LOG_FILE:logs/hubdeia.log}
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 14
  level:
    root: INFO
    com.lamorim.hubdeia: DEBUG

hub:
  openai:
    base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
    api-key: ${OPENAI_API_KEY:}
    model: ${OPENAI_MODEL:gpt-5}
    subagent-model: ${OPENAI_SUBAGENT_MODEL:${OPENAI_MODEL:gpt-5}}
    max-tool-rounds: ${OPENAI_MAX_TOOL_ROUNDS:8}
    timeout-ms: ${OPENAI_TIMEOUT_MS:60000}
  genie:
    base-url: ${GENIE_BASE_URL:}
    auth-url: ${GENIE_AUTH_URL:${GENIE_BASE_URL:}}
    client-id: ${GENIE_CLIENT_ID:}
    client-secret: ${GENIE_CLIENT_SECRET:}
    poll-interval-ms: ${GENIE_POLL_INTERVAL_MS:8000}
    poll-timeout-ms: ${GENIE_POLL_TIMEOUT_MS:1800000}
    request-timeout-ms: ${GENIE_REQUEST_TIMEOUT_MS:60000}
    viz-conversion-timeout-ms: ${GENIE_VIZ_CONVERSION_TIMEOUT_MS:${OPENAI_TIMEOUT_MS:60000}}
    viz-conversion-model: ${GENIE_VIZ_CONVERSION_MODEL:${OPENAI_MODEL:gpt-5.1}}
  agent-run:
    task-parallelism: ${TASK_PARALLELISM:4}
    task-max-attempts: ${TASK_RETRY_ATTEMPTS:3}
  bootstrap:
    enabled: ${HUB_BOOTSTRAP_ENABLED:true}
```

Tabela de propriedades (cada uma com sua variável de ambiente, default e quem a consome):

| Propriedade | Variável de ambiente | Default | Consumida por |
|---|---|---|---|
| `spring.application.name` | — | `hubdeia` | Spring |
| `spring.jackson.property-naming-strategy` | — | `SNAKE_CASE` | Serialização JSON de toda a API (ver `05-api-rest.md`) |
| `spring.mongodb.uri` | `MONGODB_URI` | `mongodb://admin:admin123@localhost:27017/appdb?authSource=admin` | Conexão MongoDB (Spring Boot 4: propriedade `spring.mongodb.uri`) |
| `spring.data.mongodb.auto-index-creation` | — | `true` | **Obrigatória**: cria os índices declarados em `@Indexed`/`@CompoundIndex` (ver 03) |
| `logging.file.name` | `HUB_LOG_FILE` | `logs/hubdeia.log` | Arquivo de log |
| `logging.logback.rollingpolicy.max-file-size` / `max-history` | — | `10MB` / `14` | Rotação do log (10 MB por arquivo, 14 dias de histórico) |
| `logging.level.root` / `logging.level.com.lamorim.hubdeia` | — | `INFO` / `DEBUG` | Níveis de log |
| `hub.openai.base-url` | `OPENAI_BASE_URL` | `https://api.openai.com/v1` | `OpenAiChatClient` |
| `hub.openai.api-key` | `OPENAI_API_KEY` | vazio | `OpenAiChatClient` (sem chave: `isConfigured()` é falso) |
| `hub.openai.model` | `OPENAI_MODEL` | `gpt-5` | `OpenAiPlannerClient`, `TaskValidatorAgent`, `CommunicatorAgent` |
| `hub.openai.subagent-model` | `OPENAI_SUBAGENT_MODEL` | valor de `OPENAI_MODEL`, senão `gpt-5` | `SubagentTaskExecutor` |
| `hub.openai.max-tool-rounds` | `OPENAI_MAX_TOOL_ROUNDS` | `8` | `SubagentTaskExecutor` |
| `hub.openai.timeout-ms` | `OPENAI_TIMEOUT_MS` | `60000` | `OpenAiChatClient` |
| `hub.genie.base-url` | `GENIE_BASE_URL` | vazio | `GenieClient`, `GenieQueryResultClient` |
| `hub.genie.auth-url` | `GENIE_AUTH_URL` | valor de `GENIE_BASE_URL` | `GenieAuthClient` |
| `hub.genie.client-id` / `client-secret` | `GENIE_CLIENT_ID` / `GENIE_CLIENT_SECRET` | vazio | `GenieAuthClient` |
| `hub.genie.poll-interval-ms` | `GENIE_POLL_INTERVAL_MS` | `8000` | `GenieClient` |
| `hub.genie.poll-timeout-ms` | `GENIE_POLL_TIMEOUT_MS` | `1800000` (30 min) | `GenieClient` |
| `hub.genie.request-timeout-ms` | `GENIE_REQUEST_TIMEOUT_MS` | `60000` | `GenieAuthClient`, `GenieClient`, `GenieQueryResultClient` |
| `hub.genie.viz-conversion-timeout-ms` | `GENIE_VIZ_CONVERSION_TIMEOUT_MS` | valor de `OPENAI_TIMEOUT_MS`, senão `60000` | `VisualizationSpecConverter` (o `@Value` tem default `15000` no código, mas o `application.yaml` sempre fornece valor) |
| `hub.genie.viz-conversion-model` | `GENIE_VIZ_CONVERSION_MODEL` | valor de `OPENAI_MODEL`, senão `gpt-5.1` | `VisualizationSpecConverter` |
| `hub.agent-run.task-parallelism` | `TASK_PARALLELISM` | `4` | `AgentRunOrchestrator` |
| `hub.agent-run.task-max-attempts` | `TASK_RETRY_ATTEMPTS` | `3` | `AgentRunService` |
| `hub.bootstrap.enabled` | `HUB_BOOTSTRAP_ENABLED` | `true` | `BootstrapConfiguration` |

Defaults dos `@Value` no código (valem se a propriedade estiver ausente do YAML): `hub.openai.model` `gpt-5`; `hub.openai.subagent-model` `gpt-5`; `hub.openai.max-tool-rounds` `8`; `hub.openai.timeout-ms` `60000`; `hub.openai.base-url` `https://api.openai.com/v1`; `hub.openai.api-key` vazio; `hub.genie.*` conforme a tabela; `hub.agent-run.task-parallelism` `4`; `hub.agent-run.task-max-attempts` `3`; `hub.bootstrap.enabled` `true`; `hub.genie.viz-conversion-model` `gpt-5`; `hub.genie.viz-conversion-timeout-ms` `15000`.

## 5. Infraestrutura Docker

**Arquivo `docker-compose.yaml`** (literal):

```yaml
services:
  mongodb:
    image: mongo:8
    container_name: mongodb
    restart: unless-stopped
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin123
      MONGO_INITDB_DATABASE: appdb
    volumes:
      - mongodb_data:/data/db

volumes:
  mongodb_data:
```

- Só existe o serviço **`mongodb`** (imagem `mongo:8`, `container_name: mongodb`, porta `27017`, usuário `admin`, senha `admin123`, banco inicial `appdb`, volume nomeado `mongodb_data`). Não há Redis nem serviço da aplicação.
- Esses valores batem com o default de `spring.mongodb.uri`. Os testes usam o **mesmo servidor** mas o banco `appdb_test` (ver `11-testes-e-criterios-de-aceite.md`).
- Subir: `docker compose up -d`.

## 6. Arquivos de controle do repositório

**Arquivo `.gitignore`** (literal):

```gitignore
HELP.md
target/
!**/src/main/**/target/
!**/src/test/**/target/

### Local environment and runtime data ###
.env
.env.*
!.env.example
logs/*
!logs/.gitkeep
*.log
*.log.*
*.pid
*.hprof
payload_genie*.json
.codex/

### Legacy JS Hub runtime data ###
node_modules/
js-hub/data/workflows/
js-hub/src/logs/

### Local Docker Compose overrides ###
docker-compose.override.yml
docker-compose.override.yaml

### STS ###
.apt_generated
.classpath
.factorypath
.project
.settings
.springBeans
.sts4-cache

### IntelliJ IDEA ###
.idea
*.iws
*.iml
*.ipr
out/

### NetBeans ###
/nbproject/private/
/nbbuild/
/dist/
/nbdist/
/.nb-gradle/
build/
!**/src/main/**/build/
!**/src/test/**/build/

### VS Code ###
.vscode/
```

**Arquivo `.gitattributes`** (literal):

```gitattributes
/mvnw text eol=lf
*.cmd text eol=crlf
```

**Arquivo `.mvn/wrapper/maven-wrapper.properties`** (literal):

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
```

- `.gitignore`: ignora, entre outros, `.env`/`.env.*` (exceto `.env.example`), `logs/*` (exceto `logs/.gitkeep`), `*.log`, `payload_genie*.json`, `.codex/`, `node_modules/`, `js-hub/data/workflows/`, `js-hub/src/logs/`, `.idea`, `.vscode/`, `target/`, `HELP.md`.
- `.gitattributes`: `mvnw` com fim de linha LF; `*.cmd` com CRLF.
- `logs/.gitkeep` é um arquivo **vazio** que existe só para versionar a pasta `logs/`.
- **`mvnw` e `mvnw.cmd`** são arquivos *gerados* pelo plugin Maven Wrapper (versão **3.3.4**, tipo **`only-script`**, Maven **3.9.16**, conforme `maven-wrapper.properties`). Não os reescreva à mão: gere-os com `mvn -N wrapper:wrapper -Dtype=only-script -Dmaven=3.9.16` (ou copie os originais). O `mvnw` lê `distributionUrl` de `.mvn/wrapper/maven-wrapper.properties`, baixa o Maven para `~/.m2/wrapper/dists` e executa-o.

## 7. Carregamento de `.env`: `DotenvEnvironmentPostProcessor` e `spring.factories`

**Arquivo `src/main/resources/META-INF/spring.factories`** (literal):

```properties
org.springframework.boot.env.EnvironmentPostProcessor=\
com.lamorim.hubdeia.config.DotenvEnvironmentPostProcessor
```

**Arquivo `src/main/java/com/lamorim/hubdeia/config/DotenvEnvironmentPostProcessor.java`** (literal):

```java
package com.lamorim.hubdeia.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final String PROPERTY_SOURCE_NAME = "hubDotenv";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Map<String, Object> values = new LinkedHashMap<>();
		load(Path.of("js-hub", ".env"), values);
		load(Path.of(".env"), values);
		if (values.isEmpty()) {
			return;
		}

		environment.getPropertySources()
				.addAfter("systemEnvironment", new MapPropertySource(PROPERTY_SOURCE_NAME, values));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 20;
	}

	private void load(Path path, Map<String, Object> values) {
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			List<String> lines = Files.readAllLines(path);
			for (String line : lines) {
				parseLine(line, values);
			}
		} catch (IOException ignored) {
			// The app can still boot with real environment variables.
		}
	}

	private void parseLine(String rawLine, Map<String, Object> values) {
		String line = rawLine == null ? "" : rawLine.trim();
		if (line.isEmpty() || line.startsWith("#")) {
			return;
		}
		if (line.startsWith("export ")) {
			line = line.substring("export ".length()).trim();
		}

		int separator = line.indexOf('=');
		if (separator <= 0) {
			return;
		}

		String key = line.substring(0, separator).trim();
		String value = line.substring(separator + 1).trim();
		if (key.isEmpty()) {
			return;
		}
		values.putIfAbsent(key, cleanValue(value));
	}

	private String cleanValue(String value) {
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
				String unquoted = value.substring(1, value.length() - 1);
				return first == '"' ? unescapeDoubleQuoted(unquoted) : unquoted;
			}
		}
		int comment = value.indexOf(" #");
		if (comment >= 0) {
			return value.substring(0, comment).trim();
		}
		return value;
	}

	private String unescapeDoubleQuoted(String value) {
		return value
				.replace("\\n", "\n")
				.replace("\\r", "\r")
				.replace("\\t", "\t")
				.replace("\\\"", "\"")
				.replace("\\\\", "\\");
	}
}
```

Comportamento exato:

1. Registrada em `spring.factories` como `EnvironmentPostProcessor`; `getOrder()` = `Ordered.HIGHEST_PRECEDENCE + 20`.
2. Lê, nesta ordem, os arquivos **`js-hub/.env`** e depois **`.env`**, ambos relativos ao **diretório de trabalho** (a raiz do projeto). Arquivo inexistente ou que não seja arquivo regular é ignorado; `IOException` é engolida (a aplicação sobe só com variáveis reais de ambiente).
3. Como o mapa usa `putIfAbsent`, **a primeira definição de uma chave vence**: uma chave presente em `js-hub/.env` tem precedência sobre `.env`.
4. O resultado vira uma `MapPropertySource` chamada **`hubDotenv`**, adicionada **depois** de `systemEnvironment`; portanto variáveis reais de ambiente do sistema **ganham** do `.env`. Se nenhuma chave foi lida, nada é adicionado.
5. Parse de cada linha: `trim`; ignora linhas vazias e as que começam com `#`; remove o prefixo `export `; exige `=` na posição > 0; chave e valor são `trim`ados; chave vazia é ignorada.
6. Valor: se tiver ≥ 2 caracteres e começar e terminar com a mesma aspa (`"` ou `'`), remove as aspas; só no caso de aspas duplas aplica `unescapeDoubleQuoted` (`\n`, `\r`, `\t`, `\"`, `\\`). Sem aspas: corta o comentário a partir de `" #"` (espaço + `#`) e faz `trim`.
7. **Por que existe**: o repositório original tem um projeto Node legado (`js-hub/`) cujo `.env` já guarda `OPENAI_API_KEY` e credenciais Genie; o Java reaproveita esses nomes de variável sem exigir outro arquivo. Na máquina nova basta criar `.env` na raiz (ou exportar as variáveis).

## 8. Classe principal, REST client e CORS

**Arquivo `src/main/java/com/lamorim/hubdeia/HubdeiaApplication.java`** (literal):

```java
package com.lamorim.hubdeia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HubdeiaApplication {

	public static void main(String[] args) {
		SpringApplication.run(HubdeiaApplication.class, args);
	}

}
```

**Arquivo `src/main/java/com/lamorim/hubdeia/config/HubRestClientFactory.java`** (literal):

```java
package com.lamorim.hubdeia.config;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HubRestClientFactory {

	public RestClient create(RestClient.Builder builder, String baseUrl, long timeoutMs) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		Duration timeout = Duration.ofMillis(Math.max(1_000, timeoutMs));
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);
		return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
	}
}
```

- `HubRestClientFactory.create(builder, baseUrl, timeoutMs)`: cria um `SimpleClientHttpRequestFactory` com `connectTimeout` e `readTimeout` iguais a `Duration.ofMillis(Math.max(1_000, timeoutMs))` (**mínimo de 1 segundo**) e devolve `builder.baseUrl(baseUrl).requestFactory(...).build()`. É um `@Component`; usada por `OpenAiChatClient`, `GenieAuthClient`, `GenieClient` e `GenieQueryResultClient`.

**Arquivo `src/main/java/com/lamorim/hubdeia/config/CorsConfig.java`** (literal):

```java
package com.lamorim.hubdeia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOriginPatterns("*")
				.allowedMethods("GET", "POST", "OPTIONS")
				.allowedHeaders("*");
	}
}
```

- `CorsConfig` (`@Configuration`, `WebMvcConfigurer`): para **todas** as rotas (`/**`) permite qualquer origem (`allowedOriginPatterns("*")`), métodos `GET`, `POST`, `OPTIONS` e qualquer cabeçalho. É o que permite abrir `frontend/index.html` direto do disco (`file://`) e chamar a API em `http://localhost:8080`. Não há `allowCredentials`.

## 9. Fora do escopo e arquivos locais

Não fazem parte do projeto replicado e **não devem ser recriados**: `js-hub/` (implementação Node.js legada), `scripts/seed-subagents.js`, `.idea/`, `.vscode/`, `.codex/`, `HELP.md`, `payload_genie*.json`, `target/`, `logs/*.log`. Os dois PNG de `docs/` (`hub-ia.png`, `mermaid-diagram.png`) são binários: copie-os separadamente (ver `12-docs-e-anexos.md`).

## 10. Como subir e verificar

```text
docker compose up -d                      # MongoDB em localhost:27017
# criar .env na raiz com, no mínimo:
#   OPENAI_API_KEY=...
#   (opcional) GENIE_BASE_URL, GENIE_CLIENT_ID, GENIE_CLIENT_SECRET, GENIE_*_SPACE_ID
./mvnw spring-boot:run                    # API em http://localhost:8080  (Windows: .\mvnw.cmd spring-boot:run)
curl http://localhost:8080/hello          # -> Hello World!
./mvnw test                               # exige o MongoDB no ar; usa o banco appdb_test
```

- A API sobe na porta padrão do Spring Boot (**8080**).
- Na primeira subida com o banco vazio, o bootstrap semeia o catálogo (ver `04-catalogo-e-bootstrap.md`).
- Logs vão para `logs/hubdeia.log` (e console), nível `DEBUG` para `com.lamorim.hubdeia`.

## Armadilhas e decisões não óbvias

1. `spring.data.mongodb.auto-index-creation: true` é indispensável: sem ela nenhum índice (inclusive os `unique`) é criado.
2. A propriedade de conexão é `spring.mongodb.uri` (Spring Boot 4), não `spring.data.mongodb.uri`.
3. Precedência do `.env`: `js-hub/.env` antes de `.env`; ambos abaixo das variáveis reais de ambiente.
4. Nenhuma chave de API é guardada no código nem no repositório.
5. `HubRestClientFactory` impõe mínimo de 1 s de timeout mesmo que a propriedade seja menor.

## Critérios de aceite

- `./mvnw -q -DskipTests test-compile` conclui sem erro (após as demais specs).
- Com o Mongo no ar, `./mvnw spring-boot:run` sobe, e `GET /hello` responde `Hello World!`.
- As coleções são criadas **com os índices** descritos em `03-dominio-e-mongodb.md` (verifique com `db.<colecao>.getIndexes()`).
- Com `OPENAI_API_KEY` ausente a aplicação sobe normalmente; o erro só aparece quando o planejador é chamado.

## Ordem de implementação sugerida

1. `pom.xml`, wrapper, `.gitignore`, `.gitattributes`, `docker-compose.yaml`, `logs/.gitkeep`.
2. `application.yaml`, `spring.factories`, `HubdeiaApplication`.
3. `DotenvEnvironmentPostProcessor`, `HubRestClientFactory`, `CorsConfig`.
4. Seguir para `03-dominio-e-mongodb.md`.
