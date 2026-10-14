# 11 — Suíte de testes, fixtures e critérios de aceite

> Spec do projeto **Hub de IA** (pacote base `com.lamorim.hubdeia`). Leia antes `00-LEIA-ME.md`.

## Objetivo

Definir a suíte de testes que **prova** que a implementação está correta. Os testes são o critério de aceite executável do projeto: depois de implementar as specs 02–10, `./mvnw test` deve passar com o total abaixo. Os arquivos de teste aparecem **inteiros e literais** (seções 4 e 5) para serem recriados sem ambiguidade.

## Arquivos cobertos

Todos os arquivos de `src/test/` (14 classes Java + 1 fixture JSON):

`HubdeiaApplicationTests`, `MongoIntegrationTest`, `application/ArtifactServiceTest`, `application/ConversationServiceTest`, `application/content/TaskOutputTest`, `application/orchestration/AgentRunOrchestratorTest`, `AgentSkillAccessPolicyTest`, `SubagentMemoryTest`, `TaskDependencyResolverTest`, `application/planner/ConversationPlannerAgentTest`, `OpenAiPlannerClientTest`, `PlannerDecisionHandlerTest`, `PlannerLiveEvalTest`, `application/run/AgentRunStoreTest`, `config/BootstrapConfigurationTest` e `src/test/resources/planner-eval/page-view-conversation.json`.

## Depende de

Todas as demais specs. Os testes **exigem o MongoDB no ar** (`docker compose up -d`).

## 1. Estratégia de teste

- **Testes de integração reais contra o Mongo** (sem Mongo embutido e sem *mocks* de repositório), usando um banco **separado**: `appdb_test` no mesmo servidor. A base `MongoIntegrationTest` configura `spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/appdb_test?authSource=admin` e `hub.bootstrap.enabled=false`, e **antes de cada teste apaga todos os documentos de todas as coleções** (os índices permanecem, criados na subida do contexto).
- **Só os colaboradores de LLM são substituídos**: `AgentRunOrchestratorTest` usa `@MockitoBean` (de `org.springframework.test.context.bean.override.mockito.MockitoBean`) para `SubagentTaskExecutor`, `TaskValidatorAgent` e `CommunicatorAgent`; `ConversationPlannerAgentTest` substitui `OpenAiPlannerClient`; `OpenAiPlannerClientTest` usa Mockito puro sobre `OpenAiChatClient`.
- Nenhum teste automático chama a OpenAI ou o Genie, **exceto** `PlannerLiveEvalTest`, que é *opt-in*.
- Concorrência é testada de verdade (threads reais): atualizações paralelas de tasks, publicações paralelas de artefatos.

## 2. Resumo por classe

| Classe | Testes | O que prova |
|---|---|---|
| `HubdeiaApplicationTests` | 1 | O contexto Spring completo sobe (usa o banco `appdb` padrão e a semente) |
| `application/ConversationServiceTest` | 4 | Criação, ordem das mensagens (mesmo no mesmo ms), `historyBefore`/`recent`, posse |
| `application/ArtifactServiceTest` | 6 | Versões e supersede, publicação sem tipo/vazia, `stale`, reativação, escopo por conversa, publicação paralela |
| `application/content/TaskOutputTest` | 7 | `markdown()`/`plainText()`, ida e volta do mapa, formatos desconhecidos, sanitizador de memória |
| `application/orchestration/AgentRunOrchestratorTest` | 9 | Aprovação, retry, limite de tentativas, cancelamento final, HITL, falha visível, dependência entre tasks, task pulada, visuais separados do texto |
| `application/orchestration/AgentSkillAccessPolicyTest` | 1 | Só as tools concedidas por skills e por provider habilitado |
| `application/orchestration/SubagentMemoryTest` | 7 | Memória curta: janela, ordem, tentativas, texto puro, truncamento, ausência, artefatos consumidos |
| `application/orchestration/TaskDependencyResolverTest` | 7 | Ordem produtor→consumidor, independência, ciclo, subagente desconhecido |
| `application/planner/ConversationPlannerAgentTest` | 4 | O que o planejador recebe (3 últimas runs, truncamentos, tasks canceladas, histórico anterior à mensagem) |
| `application/planner/OpenAiPlannerClientTest` | 5 | Prompt e entrada do planejador coerentes entre si |
| `application/planner/PlannerDecisionHandlerTest` | 10 | Resposta direta, ativação de contexto, validação antes de gravar, troca de contexto, pendências, reativação de artefato |
| `application/planner/PlannerLiveEvalTest` | 5 (ignorados) | Avaliação real do prompt do planejador com uma conversa real |
| `application/run/AgentRunStoreTest` | 3 | Atomicidade sob concorrência, estado terminal final, reset de retry |
| `config/BootstrapConfigurationTest` | 3 | Semente em banco vazio, nunca tocar no que existe, bootstrap desligado |

**Total: 72 testes. Numa execução normal o Maven reporta `Tests run: 72, Failures: 0, Errors: 0, Skipped: 5`** (os 5 ignorados são os do `PlannerLiveEvalTest`, que só rodam com `-Dplanner.live=true`; os 67 restantes executam).

## 3. Como rodar

```text
docker compose up -d
./mvnw test                                   # toda a suite (os 5 testes live ficam ignorados)
./mvnw test -Dtest=AgentRunOrchestratorTest   # uma classe
./mvnw test -Dtest=PlannerLiveEvalTest -Dplanner.live=true   # avaliacao REAL do planejador (chama a OpenAI)
```

- O teste live usa a chave do mesmo `.env` da aplicação (`OPENAI_API_KEY`), roda cada cenário **4 vezes** (`ROUNDS = 4`), imprime as decisões (`=== PLANNER LIVE: ...`) e afirma que: pedidos de gráfico sobre dados que um especialista acabou de produzir são **delegados ao mesmo especialista** (`agent-mensuracao`, ação `KEEP` ou `ACTIVATE`) em todas as rodadas; a tarefa carrega o contexto (`page_view`); agradecimento e pedido de explicação do que já está escrito são **respondidos direto**. É nondeterminístico por natureza (modelo real); use como medição, não como *gate*.
- Falhas típicas: Mongo fora do ar (todos os testes de integração falham ao subir o contexto); porta/credencial diferentes (ajuste `MONGODB_URI` ou o `MongoIntegrationTest`); índices ausentes (`auto-index-creation` desligado) fazem `parallelPublishes...` e testes de unicidade falharem.

## 4. Código-fonte literal dos testes

### 4.1 Base e contexto

**Arquivo `src/test/java/com/lamorim/hubdeia/MongoIntegrationTest.java`** (literal):

```java
package com.lamorim.hubdeia;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Base for tests that run against the local Mongo container. They use their own database
 * (indexes are created on startup) and start every test with empty collections.
 */
@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/appdb_test?authSource=admin",
		"hub.bootstrap.enabled=false"
})
public abstract class MongoIntegrationTest {

	@Autowired
	protected MongoTemplate mongo;

	@BeforeEach
	void cleanCollections() {
		mongo.getCollectionNames().forEach(name -> mongo.getCollection(name).deleteMany(new Document()));
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/HubdeiaApplicationTests.java`** (literal):

```java
package com.lamorim.hubdeia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HubdeiaApplicationTests {

	@Test
	void contextLoads() {
	}

}
```

### 4.2 Conversa, artefatos e conteúdo

**Arquivo `src/test/java/com/lamorim/hubdeia/application/ConversationServiceTest.java`** (literal):

```java
package com.lamorim.hubdeia.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.api.dto.MessageAcceptedResponse;
import com.lamorim.hubdeia.domain.model.Message;

class ConversationServiceTest extends MongoIntegrationTest {

	@Autowired
	ConversationService conversations;

	@Test
	void createStoresFirstMessageAndExposesItInTheDetail() {
		MessageAcceptedResponse accepted = conversations.create("user-1", "Como foi a campanha?");

		var detail = conversations.detail("user-1", accepted.conversationId());
		assertThat(detail.messages()).extracting(Message::id).containsExactly(accepted.messageId());
		assertThat(detail.chatLastMessage()).isEqualTo("Como foi a campanha?");
		assertThat(conversations.summaries("user-1")).hasSize(1);
	}

	@Test
	void messagesKeepInsertionOrderEvenWhenWrittenInTheSameMillisecond() {
		String conversationId = conversations.create("user-1", "primeira").conversationId();
		IntStream.range(0, 30).forEach(index -> conversations.appendAssistantMessage(conversationId, "resposta " + index));

		List<String> contents = conversations.messages("user-1", conversationId).stream().map(Message::content).toList();
		assertThat(contents).hasSize(31).first().isEqualTo("primeira");
		assertThat(contents.subList(1, 31)).containsExactlyElementsOf(IntStream.range(0, 30).mapToObj(index -> "resposta " + index).toList());
	}

	@Test
	void historyBeforeReturnsOlderMessagesOldestFirstAndExcludesTheMessageItself() {
		MessageAcceptedResponse first = conversations.create("user-1", "m1");
		conversations.appendAssistantMessage(first.conversationId(), "m2");
		conversations.appendAssistantMessage(first.conversationId(), "m3");
		MessageAcceptedResponse fourth = conversations.appendUserMessage("user-1", first.conversationId(), "m4");
		conversations.appendAssistantMessage(first.conversationId(), "m5");

		Message current = conversations.message(first.conversationId(), fourth.messageId());
		assertThat(conversations.historyBefore(current, 10)).extracting(Message::content).containsExactly("m1", "m2", "m3");
		assertThat(conversations.historyBefore(current, 2)).extracting(Message::content).containsExactly("m2", "m3");
		assertThat(conversations.recent(first.conversationId(), 2)).extracting(Message::content).containsExactly("m4", "m5");
	}

	@Test
	void otherUsersCannotReadOrWriteTheConversation() {
		String conversationId = conversations.create("user-1", "segredo").conversationId();

		assertThatThrownBy(() -> conversations.detail("user-2", conversationId)).isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(() -> conversations.appendUserMessage("user-2", conversationId, "oi")).isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(() -> conversations.create("user-1", " ")).isInstanceOf(ResponseStatusException.class);
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/ArtifactServiceTest.java`** (literal):

```java
package com.lamorim.hubdeia.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

class ArtifactServiceTest extends MongoIntegrationTest {

	private static final String CONVERSATION = "conversation-1";

	@Autowired
	ArtifactService artifacts;

	@Autowired
	SubagentRepository subagents;

	@BeforeEach
	void catalog() {
		subagents.save(new Subagent("agent-mensuracao", "Mensuracao", "", "", List.of(), List.of(), List.of("INSIGHT"), List.of(), true));
		subagents.save(new Subagent("agent-campanhas", "Campanhas", "", "", List.of(), List.of(), List.of("BRIEFING"), List.of("INSIGHT"), true));
		subagents.save(new Subagent("agent-sem-artefato", "Outro", "", "", List.of(), List.of(), List.of(), List.of(), true));
	}

	@Test
	void newArtifactSupersedesThePreviousActiveOneOfTheSameType() {
		Artifact first = publish("agent-mensuracao", "Insight setembro", "texto 1", List.of());
		Artifact second = publish("agent-mensuracao", "Insight jovens", "texto 2", List.of());

		assertThat(first.version()).isEqualTo(1);
		assertThat(second.version()).isEqualTo(2);
		assertThat(statusOf(first)).isEqualTo(ArtifactStatus.SUPERSEDED);
		assertThat(statusOf(second)).isEqualTo(ArtifactStatus.ACTIVE);
	}

	@Test
	void subagentsWithoutAProducedTypeOrWithBlankOutputPublishNothing() {
		assertThat(artifacts.publish(run(), task("agent-sem-artefato"), result("texto", List.of()))).isEmpty();
		assertThat(artifacts.publish(run(), task("agent-mensuracao"), result("  ", List.of()))).isEmpty();
		assertThat(artifacts.list(CONVERSATION)).isEmpty();
	}

	@Test
	void derivedArtifactBecomesStaleWhenItsParentIsSuperseded() {
		Artifact insight = publish("agent-mensuracao", "Insight setembro", "texto", List.of());
		Artifact briefing = publish("agent-campanhas", "Briefing", "briefing", List.of(insight.id()));
		assertThat(view(briefing).stale()).isFalse();

		publish("agent-mensuracao", "Insight jovens", "texto novo", List.of());

		assertThat(view(briefing).stale()).isTrue();
		assertThat(view(briefing).status()).isEqualTo(ArtifactStatus.ACTIVE);
	}

	@Test
	void reactivatingAnOlderVersionSwapsWhichOneIsActive() {
		Artifact first = publish("agent-mensuracao", "v1", "texto 1", List.of());
		Artifact second = publish("agent-mensuracao", "v2", "texto 2", List.of());

		Artifact reactivated = artifacts.reactivate(CONVERSATION, first.id()).orElseThrow();

		assertThat(reactivated.status()).isEqualTo(ArtifactStatus.ACTIVE);
		assertThat(statusOf(second)).isEqualTo(ArtifactStatus.SUPERSEDED);
		assertThat(artifacts.consumedBy(CONVERSATION, subagents.findById("agent-campanhas").orElseThrow())).extracting(Artifact::id).containsExactly(first.id());
	}

	@Test
	void reactivationIsScopedToTheConversation() {
		Artifact artifact = publish("agent-mensuracao", "v1", "texto", List.of());

		assertThat(artifacts.reactivate("other-conversation", artifact.id())).isEmpty();
		assertThat(artifacts.reactivate(CONVERSATION, "unknown-id")).isEmpty();
	}

	@Test
	void parallelPublishesGetDistinctVersionsAndExactlyOneStaysActive() {
		int publishers = 8;
		CompletableFuture.allOf(IntStream.range(0, publishers).mapToObj(index -> CompletableFuture.runAsync(
				() -> publish("agent-mensuracao", "Insight " + index, "texto " + index, List.of()))).toArray(CompletableFuture[]::new)).join();

		List<ArtifactView> all = artifacts.list(CONVERSATION);
		assertThat(all).hasSize(publishers);
		assertThat(all).extracting(ArtifactView::version).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, publishers).boxed().toList());
		assertThat(all).filteredOn(view -> view.status() == ArtifactStatus.ACTIVE).singleElement()
				.satisfies(view -> assertThat(view.version()).isEqualTo(publishers));
	}

	private Artifact publish(String subagentId, String title, String content, List<String> consumed) {
		return artifacts.publish(run(), task(subagentId, title), result(content, consumed)).orElseThrow();
	}

	private ArtifactStatus statusOf(Artifact artifact) {
		return mongo.findById(artifact.id(), Artifact.class).status();
	}

	private ArtifactView view(Artifact artifact) {
		return artifacts.list(CONVERSATION).stream().filter(value -> value.id().equals(artifact.id())).findFirst().orElseThrow();
	}

	private AgentRun run() {
		return new AgentRun("run-1", CONVERSATION, AgentRunStatus.RUNNING, List.of(), List.of(), List.of(), "message-1", "context-1", null, "", Instant.now(), null);
	}

	private Task task(String subagentId) {
		return task(subagentId, "Tarefa");
	}

	private Task task(String subagentId, String description) {
		return Task.create("task-" + System.nanoTime(), description, subagentId, "conteudo", 3, Instant.now());
	}

	private TaskExecutionResult result(String content, List<String> consumed) {
		return new TaskExecutionResult("task", "agent", Map.of("content", content), List.of(), List.of(), List.of(), consumed);
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/content/TaskOutputTest.java`** (literal):

````java
package com.lamorim.hubdeia.application.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lamorim.hubdeia.application.ConversationMemorySanitizer;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;

class TaskOutputTest {

	private final Visual table = new Visual("v1", "Desempenho", VisualFormat.HTML, "<section class=\"data-table\"><table></table></section>");
	private final Visual chart = new Visual("v2", "Receita", VisualFormat.VEGA_LITE, "{\"mark\":\"bar\"}");

	@Test
	void markdownPutsEachVisualWhereItsMarkerIsAndAppendsTheOnesTheTextLeftOut() {
		TaskOutput output = new TaskOutput("Analise [[visualization:v1]] e de novo [[visualization:v1]].", List.of(table, chart));

		assertThat(output.markdown()).isEqualTo("Analise " + TaskOutput.VISUAL_START + "\n\n" + table.payload() + "\n\n" + TaskOutput.VISUAL_END
				+ " e de novo .\n\n```vega-lite\n{\"mark\":\"bar\"}\n```");
	}

	@Test
	void markerWithoutAVisualDisappears() {
		assertThat(new TaskOutput("Veja [[visualization:nao-existe]] aqui", List.of()).markdown()).isEqualTo("Veja  aqui");
	}

	@Test
	void plainTextNeverCarriesHtmlOrChartSpecs() {
		TaskOutput output = new TaskOutput("Resumo.\n\n[[visualization:v1]]\n\nMais texto [[visualization:v2]].", List.of(table, chart));

		String text = output.plainText();

		assertThat(text).contains("[visual: Desempenho]").contains("[visual: Receita]").contains("Resumo.").contains("Mais texto");
		assertThat(text).doesNotContain("<section").doesNotContain("vega").doesNotContain("[[visualization");
	}

	@Test
	void visualsNotReferencedByTheTextAreNotInThePlainText() {
		assertThat(new TaskOutput("So texto.", List.of(table)).plainText()).isEqualTo("So texto.");
	}

	@Test
	void roundTripsThroughTheMapStoredInTasks() {
		TaskOutput original = new TaskOutput("Texto [[visualization:v2]]", List.of(table, chart));

		TaskOutput restored = TaskOutput.from(original.toMap());

		assertThat(restored).isEqualTo(original);
	}

	@Test
	void outputsWithoutVisualsOrWithUnknownFormatsAreStillReadable() {
		assertThat(TaskOutput.from(Map.of("content", "so texto"))).isEqualTo(new TaskOutput("so texto", List.of()));
		assertThat(TaskOutput.from(null).content()).isEmpty();
		TaskOutput odd = TaskOutput.from(Map.of("content", "x", "visuals", List.of(
				Map.of("id", "a", "title", "t", "format", "HOLOGRAM", "payload", "?"),
				Map.of("id", "b", "format", "HTML", "payload", ""),
				Map.of("id", "c", "title", "ok", "format", "HTML", "payload", "<b>ok</b>"))));
		assertThat(odd.visuals()).extracting(Visual::id).containsExactly("c");
	}

	@Test
	void memorySanitizerDropsAssembledVisualsFromWhatModelsRead() {
		String markdown = new TaskOutput("Texto [[visualization:v1]] e [[visualization:v2]] fim", List.of(table, chart)).markdown();

		String sanitized = new ConversationMemorySanitizer().sanitizeForAgent(markdown);

		assertThat(sanitized).isEqualTo("Texto [visual omitido da memoria] e [visual omitido da memoria] fim");
	}
}
````

### 4.3 Orquestração e subagente

**Arquivo `src/test/java/com/lamorim/hubdeia/application/run/AgentRunStoreTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunLog;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.TaskValidation;

class AgentRunStoreTest extends MongoIntegrationTest {

	@Autowired
	AgentRunStore store;

	@Test
	void parallelTaskUpdatesDoNotOverwriteEachOther() {
		int taskCount = 12;
		AgentRun run = store.insert(newRun(taskCount));

		CompletableFuture.allOf(run.tasks().stream().map(task -> CompletableFuture.runAsync(() -> {
			store.markTaskRunning(run.id(), task.id());
			store.recordTaskOutput(run.id(), task.id(), Map.of("content", "out-" + task.id()), 1);
			store.appendTaskValidation(run.id(), task.id(), new TaskValidation("CONTINUE", "ok", "", 1, Instant.now()));
			store.appendLog(run.id(), AgentRunLog.info(AgentRunStatus.RUNNING, "TASK_FINISHED", "done " + task.id()));
			store.finishTask(run.id(), task.id(), TaskStatus.FINISHED);
		})).toArray(CompletableFuture[]::new)).join();

		AgentRun stored = store.require(run.id());
		assertThat(stored.tasks()).hasSize(taskCount).allSatisfy(task -> {
			assertThat(task.status()).isEqualTo(TaskStatus.FINISHED);
			assertThat(task.output()).containsEntry("content", "out-" + task.id());
			assertThat(task.validations()).hasSize(1);
			assertThat(task.attempts()).isEqualTo(1);
			assertThat(task.finishedAt()).isNotNull();
		});
		assertThat(stored.logs()).hasSize(taskCount + 1);
	}

	@Test
	void terminalStatusIsFinal() {
		AgentRun run = store.insert(newRun(1));
		AgentRunLog cancelled = AgentRunLog.info(AgentRunStatus.CANCELLED, "CANCELLED", "cancelado");

		assertThat(store.complete(run.id(), AgentRunStatus.CANCELLED, List.of(new FinalAnswerBlock("communicator", "x", "cancelado", null, null)), cancelled)).isTrue();
		assertThat(store.complete(run.id(), AgentRunStatus.FINISHED, List.of(), AgentRunLog.info(AgentRunStatus.FINISHED, "FINISHED", "fim"))).isFalse();
		assertThat(store.appendLog(run.id(), AgentRunLog.info(AgentRunStatus.RUNNING, "LATE", "atrasado"))).isFalse();
		store.markTaskRunning(run.id(), run.tasks().getFirst().id());

		AgentRun stored = store.require(run.id());
		assertThat(stored.status()).isEqualTo(AgentRunStatus.CANCELLED);
		assertThat(stored.agentRunLastMessage()).isEqualTo("cancelado");
		assertThat(stored.finalAnswer()).hasSize(1);
		assertThat(stored.finishedAt()).isNotNull();
		assertThat(store.isRunning(run.id())).isFalse();
	}

	@Test
	void retryResetKeepsOriginalInputAndAddsFeedback() {
		AgentRun run = store.insert(newRun(1));
		String taskId = run.tasks().getFirst().id();
		store.recordTaskOutput(run.id(), taskId, Map.of("content", "ruim"), 1);

		store.resetTaskForRetry(run.id(), taskId, "faltou o periodo");

		Task task = store.requireTask(run.id(), taskId);
		assertThat(task.status()).isEqualTo(TaskStatus.NEW);
		assertThat(task.output()).isEmpty();
		assertThat(task.input()).containsEntry("enriched_content", "conteudo").containsEntry("validator_feedback", "faltou o periodo");
		assertThat(task.attempts()).isEqualTo(1);
	}

	private AgentRun newRun(int tasks) {
		Instant now = Instant.now();
		List<Task> list = IntStream.range(0, tasks)
				.mapToObj(index -> Task.create("task-" + index + "-" + System.nanoTime(), "descricao " + index, "agent-x", "conteudo", 3, now)).toList();
		return new AgentRun("run-" + System.nanoTime(), "conversation-1", AgentRunStatus.RUNNING, list,
				List.of(AgentRunLog.info(AgentRunStatus.RUNNING, "TASKS_RUNNING", "Tasks planejadas.")), List.of(), "message-1", "context-1", null, "plano", now, null);
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/orchestration/AgentRunOrchestratorTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.ArtifactView;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.HumanInputService;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.communication.CommunicatorAgent;
import com.lamorim.hubdeia.application.planner.PlannedTask;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.application.validation.TaskValidationResult;
import com.lamorim.hubdeia.application.validation.TaskValidationStatus;
import com.lamorim.hubdeia.application.validation.TaskValidatorAgent;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

/** The orchestrator runs against real Mongo; only the three LLM-backed collaborators are replaced. */
class AgentRunOrchestratorTest extends MongoIntegrationTest {

	@MockitoBean
	SubagentTaskExecutor executor;
	@MockitoBean
	TaskValidatorAgent validator;
	@MockitoBean
	CommunicatorAgent communicator;

	@Autowired
	AgentRunOrchestrator orchestrator;
	@Autowired
	AgentRunService runs;
	@Autowired
	ConversationService conversations;
	@Autowired
	WorkContextService contexts;
	@Autowired
	HumanInputService humanInput;
	@Autowired
	ArtifactService artifacts;
	@Autowired
	SubagentRepository subagents;
	@Autowired
	WorkContextRepository workContextDefinitions;

	String conversationId;
	String messageId;
	ConversationWorkContext context;

	@BeforeEach
	void setUp() {
		subagents.save(new Subagent("agent-mensuracao", "Mensuracao", "", "", List.of(), List.of(), List.of("INSIGHT"), List.of(), true));
		subagents.save(new Subagent("agent-campanhas", "Campanhas", "", "", List.of(), List.of(), List.of("BRIEFING"), List.of("INSIGHT"), true));
		workContextDefinitions.save(new WorkContext("ctx-dados", "DADOS", "Dados", "", "", List.of("agent-mensuracao"), true));
		var accepted = conversations.create("user-1", "Como foi a campanha?");
		conversationId = accepted.conversationId();
		messageId = accepted.messageId();
		context = contexts.activate(conversationId, "ctx-dados");
		when(communicator.introduceFinishedRun(any(), any())).thenReturn("Resumo final");
		when(communicator.introducePartialRun(any(), any())).thenReturn("Resumo parcial");
		when(communicator.introduceCancelledRun(any())).thenReturn("Encerrei a execucao");
		when(executor.execute(any(), any(), any(), any())).thenAnswer(call -> okResult(call.getArgument(2)));
	}

	@Test
	void approvedTaskFinishesTheRunPublishesAnArtifactAndAnswersInTheChat() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.CONTINUE, ""));
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		AgentRun run = runs.get(runId);
		assertThat(run.status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(run.tasks()).singleElement().satisfies(task -> {
			assertThat(task.status()).isEqualTo(TaskStatus.FINISHED);
			assertThat(task.attempts()).isEqualTo(1);
			assertThat(task.validations()).hasSize(1);
		});
		assertThat(run.finalAnswer()).extracting(block -> block.type()).containsExactly("communicator", "subagent");
		assertThat(chat()).containsSubsequence("Como foi a campanha?", "Resumo final", "resultado de Analisar cartao");
		assertThat(artifacts.list(conversationId)).singleElement().satisfies(view -> {
			assertThat(view.type()).isEqualTo("INSIGHT");
			assertThat(view.status()).isEqualTo(ArtifactStatus.ACTIVE);
			assertThat(view.content()).isEqualTo("resultado de Analisar cartao");
		});
		assertThat(runs.status(conversationId, runId).status()).isEqualTo("FINISHED");
		assertThat(contexts.active(conversationId)).get().satisfies(active -> assertThat(active.lastAgentRunId()).isEqualTo(runId));
	}

	@Test
	void retryReceivesTheValidatorFeedbackAndStopsAtTheAttemptLimit() {
		when(validator.validate(any(), any(), any()))
				.thenReturn(verdict(TaskValidationStatus.RETRY, "faltou o periodo"))
				.thenReturn(verdict(TaskValidationStatus.CONTINUE, ""));
		List<Task> seen = new CopyOnWriteArrayList<>();
		doAnswer(call -> {
			seen.add(call.getArgument(2));
			return okResult(call.getArgument(2));
		}).when(executor).execute(any(), any(), any(), any());
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		assertThat(seen).hasSize(2);
		assertThat(seen.get(0).input()).doesNotContainKey("validator_feedback");
		assertThat(seen.get(1).input()).containsEntry("validator_feedback", "faltou o periodo");
		Task task = runs.get(runId).tasks().getFirst();
		assertThat(task.attempts()).isEqualTo(2);
		assertThat(task.validations()).extracting(validation -> validation.status()).containsExactly("RETRY", "CONTINUE");
		assertThat(runs.get(runId).status()).isEqualTo(AgentRunStatus.FINISHED);
	}

	@Test
	void taskThatKeepsFailingValidationIsCancelledAndTheRunStillFinishes() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.RETRY, "ruim"));
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		verify(executor, times(3)).execute(any(), any(), any(), any());
		AgentRun run = runs.get(runId);
		assertThat(run.tasks().getFirst().status()).isEqualTo(TaskStatus.CANCELLED);
		assertThat(run.status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(run.finalAnswer()).extracting(block -> block.type()).containsExactly("communicator");
		assertThat(artifacts.list(conversationId)).isEmpty();
	}

	@Test
	void cancelFromOneTaskEndsTheRunForGoodEvenWhenAnotherTaskFinishesLater() {
		when(validator.validate(any(), any(), any())).thenAnswer(call ->
				"lenta".equals(((Task) call.getArgument(2)).description()) ? verdict(TaskValidationStatus.CONTINUE, "") : verdict(TaskValidationStatus.CANCEL, "impossivel"));
		doAnswer(call -> {
			Task task = call.getArgument(2);
			if ("lenta".equals(task.description())) {
				Thread.sleep(600);
			}
			return okResult(task);
		}).when(executor).execute(any(), any(), any(), any());
		String runId = startRun("lenta", "rapida");

		orchestrator.execute(conversationId, runId);

		AgentRun run = runs.get(runId);
		assertThat(run.status()).isEqualTo(AgentRunStatus.CANCELLED);
		assertThat(run.finishedAt()).isNotNull();
		assertThat(chat().stream().filter("Encerrei a execucao"::equals).count()).isEqualTo(1);
		assertThat(chat()).doesNotContain("Resumo final");
		assertThat(runs.status(conversationId, runId).status()).isEqualTo("CANCELLED");
		assertThat(artifacts.list(conversationId)).as("a task approved after the run ended publishes nothing").isEmpty();
		assertThat(humanInput.pendingForConversation(conversationId)).isEmpty();
	}

	@Test
	void humanInputRequestEndsTheRunAsPartialWithThePendingQuestion() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.HITL, "Qual segmento?"));
		String runId = startRun("Analisar clientes");

		orchestrator.execute(conversationId, runId);

		assertThat(runs.get(runId).status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(humanInput.pendingForConversation(conversationId)).singleElement().satisfies(request -> {
			assertThat(request.question()).isEqualTo("Qual segmento?");
			assertThat(request.agentRunId()).isEqualTo(runId);
		});
		assertThat(chat()).anyMatch(content -> content.contains("Qual segmento?"));
		assertThat(chat()).contains("Resumo parcial");
	}

	@Test
	void unexpectedErrorFailsTheRunAndTellsTheUser() {
		doThrow(new IllegalStateException("provider fora do ar")).when(executor).execute(any(), any(), any(), any());
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		AgentRun run = runs.get(runId);
		assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
		assertThat(run.agentRunLastMessage()).contains("provider fora do ar");
		assertThat(chat()).last().asString().contains("Nao foi possivel concluir");
		assertThat(runs.status(conversationId, runId).status()).isEqualTo("FAILED");
	}

	@Test
	void aTaskThatConsumesAnArtifactWaitsForTheTaskThatProducesItInTheSameRun() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.CONTINUE, ""));
		List<String> started = new CopyOnWriteArrayList<>();
		List<Integer> insightsVisibleWhenBriefingStarted = new CopyOnWriteArrayList<>();
		doAnswer(call -> {
			Task task = call.getArgument(2);
			started.add(task.description());
			if ("briefing".equals(task.description())) {
				insightsVisibleWhenBriefingStarted.add(artifacts.list(conversationId).size());
			} else {
				Thread.sleep(400);
			}
			return okResult(task);
		}).when(executor).execute(any(), any(), any(), any());
		String runId = runs.start(conversationId, messageId, context, null, "Vou analisar.", List.of(
				new PlannedTask("briefing", "criar briefing", "agent-campanhas"),
				new PlannedTask("insight", "analisar", "agent-mensuracao"))).id();

		orchestrator.execute(conversationId, runId);

		assertThat(started).containsExactly("insight", "briefing");
		assertThat(insightsVisibleWhenBriefingStarted).as("the briefing started after the insight was published").containsExactly(1);
		assertThat(runs.get(runId).status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(artifacts.list(conversationId)).extracting(ArtifactView::type).containsExactlyInAnyOrder("INSIGHT", "BRIEFING");
	}

	@Test
	void aTaskWhosePrerequisiteWasNotApprovedIsSkippedInsteadOfUsingAnOldResult() {
		when(validator.validate(any(), any(), any())).thenAnswer(call ->
				"insight".equals(((Task) call.getArgument(2)).description()) ? verdict(TaskValidationStatus.RETRY, "ruim") : verdict(TaskValidationStatus.CONTINUE, ""));
		List<String> started = new CopyOnWriteArrayList<>();
		doAnswer(call -> {
			Task task = call.getArgument(2);
			started.add(task.description());
			return okResult(task);
		}).when(executor).execute(any(), any(), any(), any());
		String runId = runs.start(conversationId, messageId, context, null, "Vou analisar.", List.of(
				new PlannedTask("insight", "analisar", "agent-mensuracao"),
				new PlannedTask("briefing", "criar briefing", "agent-campanhas"))).id();

		orchestrator.execute(conversationId, runId);

		assertThat(started).containsOnly("insight").hasSize(3);
		AgentRun run = runs.get(runId);
		assertThat(run.tasks()).extracting(Task::description, Task::status).containsExactlyInAnyOrder(
				org.assertj.core.groups.Tuple.tuple("insight", TaskStatus.CANCELLED), org.assertj.core.groups.Tuple.tuple("briefing", TaskStatus.CANCELLED));
		assertThat(run.status()).isEqualTo(AgentRunStatus.FINISHED);
		assertThat(run.logs()).extracting(log -> log.executionStatus()).contains("TASK_SKIPPED");
		assertThat(artifacts.list(conversationId)).isEmpty();
	}

	@Test
	void visualsStayApartFromTheTextInStorageAndComeTogetherOnlyForPeople() {
		when(validator.validate(any(), any(), any())).thenReturn(verdict(TaskValidationStatus.CONTINUE, ""));
		Visual table = new Visual("viz1", "Desempenho", VisualFormat.HTML, "<section class=\"data-table\"><table></table></section>");
		doAnswer(call -> {
			Task task = call.getArgument(2);
			return new TaskExecutionResult(task.id(), "agent-mensuracao",
					new TaskOutput("Resumo.\n\n[[visualization:viz1]]\n\nFim.", List.of(table)).toMap(), List.of(), List.of(), List.of(), List.of());
		}).when(executor).execute(any(), any(), any(), any());
		String runId = startRun("Analisar cartao");

		orchestrator.execute(conversationId, runId);

		ArtifactView artifact = artifacts.list(conversationId).getFirst();
		assertThat(artifact.content()).contains("[visual: Desempenho]").doesNotContain("<section");
		assertThat(artifact.renderedContent()).contains("<section class=\"data-table\">");
		assertThat(chat()).anyMatch(message -> message.contains("<section class=\"data-table\">"));
		assertThat(mongo.findById(artifact.id(), com.lamorim.hubdeia.domain.model.Artifact.class).content()).doesNotContain("<section");
	}

	private String startRun(String... descriptions) {
		List<PlannedTask> planned = new ArrayList<>();
		for (String description : descriptions) {
			planned.add(new PlannedTask(description, "conteudo de " + description, "agent-mensuracao"));
		}
		return runs.start(conversationId, messageId, context, null, "Vou analisar.", planned).id();
	}

	private TaskExecutionResult okResult(Task task) {
		return new TaskExecutionResult(task.id(), "agent-mensuracao", Map.of("content", "resultado de " + task.description()), List.of(), List.of(), List.of(), List.of());
	}

	private TaskValidationResult verdict(TaskValidationStatus status, String text) {
		return new TaskValidationResult(status, text, text, Instant.now());
	}

	private List<String> chat() {
		return conversations.messages("user-1", conversationId).stream().map(Message::content).toList();
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/orchestration/TaskDependencyResolverTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;

class TaskDependencyResolverTest {

	private final TaskDependencyResolver resolver = new TaskDependencyResolver();

	private final Subagent measurement = agent("measurement", List.of("INSIGHT"), List.of());
	private final Subagent campaigns = agent("campaigns", List.of("BRIEFING"), List.of("INSIGHT"));
	private final Subagent customers = agent("customers", List.of(), List.of());

	@Test
	void consumerWaitsForTheProducerOfTheTypeItConsumes() {
		Task briefing = task("briefing", "campaigns");
		Task insight = task("insight", "measurement");

		TaskDependencyResolver.Plan plan = resolver.resolve(List.of(briefing, insight), catalog());

		assertThat(plan.order()).extracting(Task::id).containsExactly("insight", "briefing");
		assertThat(plan.prerequisites().get("briefing")).containsExactly("insight");
		assertThat(plan.prerequisites().get("insight")).isEmpty();
	}

	@Test
	void unrelatedTasksStayIndependent() {
		TaskDependencyResolver.Plan plan = resolver.resolve(List.of(task("a", "measurement"), task("b", "customers")), catalog());

		assertThat(plan.prerequisites().values()).allSatisfy(prerequisites -> assertThat(prerequisites).isEmpty());
		assertThat(plan.order()).extracting(Task::id).containsExactly("a", "b");
	}

	@Test
	void consumerAloneHasNothingToWaitFor() {
		TaskDependencyResolver.Plan plan = resolver.resolve(List.of(task("briefing", "campaigns")), catalog());

		assertThat(plan.prerequisites().get("briefing")).isEmpty();
	}

	@Test
	void everyProducerIsAPrerequisite() {
		TaskDependencyResolver.Plan plan = resolver.resolve(
				List.of(task("briefing", "campaigns"), task("insight-1", "measurement"), task("insight-2", "measurement")), catalog());

		assertThat(plan.prerequisites().get("briefing")).containsExactlyInAnyOrder("insight-1", "insight-2");
		assertThat(plan.order().getLast().id()).isEqualTo("briefing");
	}

	@Test
	void agentsOfTheSameKindNeverWaitForEachOther() {
		Subagent both = agent("both", List.of("INSIGHT"), List.of("INSIGHT"));

		TaskDependencyResolver.Plan plan = resolver.resolve(List.of(task("x", "both"), task("y", "both")), Map.of("both", both));

		assertThat(plan.prerequisites().values()).allSatisfy(prerequisites -> assertThat(prerequisites).isEmpty());
	}

	@Test
	void aCycleInTheCatalogFallsBackToParallelTasks() {
		Subagent first = agent("first", List.of("A"), List.of("B"));
		Subagent second = agent("second", List.of("B"), List.of("A"));

		TaskDependencyResolver.Plan plan = resolver.resolve(List.of(task("t1", "first"), task("t2", "second")), Map.of("first", first, "second", second));

		assertThat(plan.prerequisites()).isEmpty();
		assertThat(plan.order()).extracting(Task::id).containsExactly("t1", "t2");
	}

	@Test
	void tasksOfUnknownSubagentsAreLeftAlone() {
		TaskDependencyResolver.Plan plan = resolver.resolve(List.of(task("ghost", "missing"), task("insight", "measurement")), catalog());

		assertThat(plan.prerequisites().values()).allSatisfy(prerequisites -> assertThat(prerequisites).isEmpty());
		assertThat(plan.prerequisites().get("ghost")).isEqualTo(Set.of());
	}

	private Map<String, Subagent> catalog() {
		return Map.of(measurement.id(), measurement, campaigns.id(), campaigns, customers.id(), customers);
	}

	private static Subagent agent(String id, List<String> produces, List<String> consumes) {
		return new Subagent(id, id, "", "", List.of(), List.of(), produces, consumes, true);
	}

	private static Task task(String id, String subagentId) {
		return Task.create(id, id, subagentId, "", 3, Instant.now());
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/orchestration/SubagentMemoryTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.TaskExecutionService;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.orchestration.SubagentPromptBuilder.SubagentPrompt;
import com.lamorim.hubdeia.application.run.AgentRunStore;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

/** The individual short-term memory a subagent receives: its own recent tasks, as plain text. */
class SubagentMemoryTest extends MongoIntegrationTest {

	private static final String CONVERSATION = "conversation-1";
	private static final String RUN = "run-1";

	@Autowired
	TaskExecutionService executions;
	@Autowired
	SubagentPromptBuilder prompts;
	@Autowired
	AgentRunStore runStore;
	@Autowired
	ArtifactService artifacts;
	@Autowired
	SubagentRepository subagents;

	private Subagent measurement;
	private Subagent campaigns;

	@BeforeEach
	void catalog() {
		measurement = subagents.save(new Subagent("agent-mensuracao", "Mensuracao", "", "Voce mede campanhas.", List.of(), List.of(), List.of("INSIGHT"), List.of(), true));
		campaigns = subagents.save(new Subagent("agent-campanhas", "Campanhas", "", "Voce cria briefings.", List.of(), List.of(), List.of("BRIEFING"), List.of("INSIGHT"), true));
		runStore.insert(new AgentRun(RUN, CONVERSATION, AgentRunStatus.RUNNING, List.of(), List.of(), List.of(), "message-1", null, null, "", Instant.now(), null));
	}

	@Test
	void memoryHoldsTheLatestFiveTasksOfTheSubagentOldestFirst() {
		for (int index = 1; index <= 7; index++) {
			record(CONVERSATION, measurement, "t" + index, "resultado " + index);
		}
		record(CONVERSATION, campaigns, "other-agent", "nao deve aparecer");
		record("other-conversation", measurement, "other-conversation-task", "nao deve aparecer");

		List<SubagentExecution> memory = executions.recentMemory(CONVERSATION, measurement.id());

		assertThat(memory).extracting(SubagentExecution::taskId).containsExactly("t3", "t4", "t5", "t6", "t7");
	}

	@Test
	void severalAttemptsOfOneTaskCountOnceAndTheLatestAttemptWins() {
		for (int index = 1; index <= 5; index++) {
			record(CONVERSATION, measurement, "t" + index, "resultado " + index);
		}
		for (int attempt = 1; attempt <= 8; attempt++) {
			record(CONVERSATION, measurement, "t6", "tentativa " + attempt);
		}

		List<SubagentExecution> memory = executions.recentMemory(CONVERSATION, measurement.id());

		assertThat(memory).extracting(SubagentExecution::taskId).containsExactly("t2", "t3", "t4", "t5", "t6");
		assertThat(TaskOutput.from(memory.getLast().output()).content()).isEqualTo("tentativa 8");
	}

	@Test
	void executionsWrittenBackToBackKeepTheirOrder() {
		for (int index = 1; index <= 20; index++) {
			record(CONVERSATION, measurement, "t" + index, "resultado " + index);
		}

		assertThat(executions.recentMemory(CONVERSATION, measurement.id())).extracting(SubagentExecution::taskId)
				.containsExactly("t16", "t17", "t18", "t19", "t20");
	}

	@Test
	void theMemoryInThePromptIsPlainTextWithoutVisualMarkup() {
		Visual table = new Visual("viz1", "Tabela de campanhas", VisualFormat.HTML, "<section class=\"data-table\"><table><tr><td>1</td></tr></table></section>");
		record(CONVERSATION, measurement, "t1", new TaskOutput("Black lidera. [[visualization:viz1]] Fim.", List.of(table)));
		record(CONVERSATION, campaigns, "t-other", new TaskOutput("memoria de outro agente", List.of()));

		SubagentPrompt prompt = prompts.build(CONVERSATION, RUN, measurement, newTask("t2", "agent-mensuracao", "Agora so o publico jovem"));

		assertThat(prompt.messages()).hasSize(3);
		String memory = prompt.messages().get(1).getString("content");
		assertThat(memory).startsWith("Memoria individual do subagente:").contains("task_id=t1").contains("Black lidera. [visual: Tabela de campanhas] Fim.");
		assertThat(memory).doesNotContain("<section").doesNotContain("[[visualization").doesNotContain("memoria de outro agente");
		assertThat(prompt.messages().getLast().getString("content")).contains("Agora so o publico jovem");
	}

	@Test
	void longOutputsAreCutSoMemoryStaysShort() {
		record(CONVERSATION, measurement, "t1", "x".repeat(5000));

		String memory = prompts.build(CONVERSATION, RUN, measurement, newTask("t2", "agent-mensuracao", "algo")).messages().get(1).getString("content");

		assertThat(memory.length()).isLessThan(2000);
		assertThat(memory).endsWith("...");
	}

	@Test
	void withoutHistoryThereIsNoMemorySection() {
		SubagentPrompt prompt = prompts.build(CONVERSATION, RUN, measurement, newTask("t1", "agent-mensuracao", "primeira pergunta"));

		assertThat(prompt.messages()).extracting(message -> message.getString("role")).containsExactly("system", "user");
		assertThat(prompt.consumedArtifactIds()).isEmpty();
	}

	@Test
	void consumedArtifactsReachThePromptAsPlainTextAndAreReportedForLineage() {
		Visual chart = new Visual("viz9", "Receita", VisualFormat.VEGA_LITE, "{\"mark\":\"bar\"}");
		AgentRun run = runStore.require(RUN);
		Task producer = newTask("insight-task", "agent-mensuracao", "Insight setembro");
		var insight = artifacts.publish(run, producer, new TaskExecutionResult(producer.id(), measurement.id(),
				new TaskOutput("Black lidera. [[visualization:viz9]]", List.of(chart)).toMap(), List.of(), List.of(), List.of(), List.of())).orElseThrow();

		SubagentPrompt prompt = prompts.build(CONVERSATION, RUN, campaigns, newTask("brief", "agent-campanhas", "Crie o briefing"));

		String instructions = prompt.messages().getFirst().getString("content");
		assertThat(instructions).contains("[INSIGHT v1] descricao insight-task").contains("Black lidera. [visual: Receita]");
		assertThat(instructions).doesNotContain("vega").doesNotContain("{\"mark\"");
		assertThat(prompt.consumedArtifactIds()).containsExactly(insight.id());
	}

	private void record(String conversationId, Subagent subagent, String taskId, String text) {
		record(conversationId, subagent, taskId, new TaskOutput(text, List.of()));
	}

	private void record(String conversationId, Subagent subagent, String taskId, TaskOutput output) {
		Task task = newTask(taskId, subagent.id(), "descricao " + taskId);
		executions.record(conversationId, RUN, task, new TaskExecutionResult(taskId, subagent.id(), output.toMap(), List.of(), List.of(), List.of(), List.of()));
	}

	private static Task newTask(String id, String subagentId, String enrichedContent) {
		return Task.create(id, "descricao " + id, subagentId, enrichedContent, 3, Instant.now());
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/orchestration/AgentSkillAccessPolicyTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentProvider;
import com.lamorim.hubdeia.domain.model.Tool;
import com.lamorim.hubdeia.domain.repository.SkillRepository;
import com.lamorim.hubdeia.domain.repository.ToolRepository;

class AgentSkillAccessPolicyTest {

	@Test
	void exposesOnlyToolsGrantedBySkillsAndEnabledProviders() {
		SkillRepository skills = Mockito.mock(SkillRepository.class);
		ToolRepository tools = Mockito.mock(ToolRepository.class);
		AgentSkillAccessPolicy policy = new AgentSkillAccessPolicy(skills, tools);
		Subagent subagent = new Subagent("agent-1", "Agente", "", "", List.of("skill-1"),
				List.of(new SubagentProvider("genie", "provider-genie", Map.of(), true)), List.of(), List.of(), true);
		Skill skill = new Skill("skill-1", "ANALYSIS", "Analise", "", "", List.of("tool-genie", "tool-other"), true);
		Tool genieTool = new Tool("tool-genie", "provider-genie", "Genie", "function", "enviarMensagemGenie", "", Map.of(), Map.of(), Map.of(), true);
		Tool otherTool = new Tool("tool-other", "provider-other", "Other", "function", "otherTool", "", Map.of(), Map.of(), Map.of(), true);

		when(skills.findByIdInAndEnabledTrue(List.of("skill-1"))).thenReturn(List.of(skill));
		when(tools.findByIdInAndEnabledTrue(List.of("tool-genie", "tool-other"))).thenReturn(List.of(genieTool, otherTool));

		assertThat(policy.toolsFor(subagent)).containsOnlyKeys("enviarMensagemGenie");
	}
}
```

### 4.4 Planejador

**Arquivo `src/test/java/com/lamorim/hubdeia/application/planner/PlannerDecisionHandlerTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.HumanInputService;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.orchestration.TaskExecutionResult;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ConversationWorkContextStatus;
import com.lamorim.hubdeia.domain.model.HumanInputRequestStatus;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

class PlannerDecisionHandlerTest extends MongoIntegrationTest {

	@Autowired
	PlannerDecisionHandler handler;
	@Autowired
	ConversationService conversations;
	@Autowired
	WorkContextService contexts;
	@Autowired
	HumanInputService humanInput;
	@Autowired
	ArtifactService artifacts;
	@Autowired
	AgentRunService runs;
	@Autowired
	WorkContextRepository workContextDefinitions;
	@Autowired
	SubagentRepository subagents;

	String conversationId;
	String messageId;

	@BeforeEach
	void conversationAndCatalog() {
		subagents.save(new Subagent("agent-mensuracao", "Mensuracao", "", "", List.of(), List.of(), List.of("INSIGHT"), List.of(), true));
		workContextDefinitions.save(new WorkContext("ctx-dados", "DADOS", "Dados", "", "", List.of("agent-mensuracao"), true));
		workContextDefinitions.save(new WorkContext("ctx-outro", "OUTRO", "Outro", "", "", List.of("agent-outro"), true));
		var accepted = conversations.create("user-1", "oi");
		conversationId = accepted.conversationId();
		messageId = accepted.messageId();
	}

	@Test
	void directAnswerIsWrittenToTheChatWithoutStartingARun() {
		Optional<String> runId = handler.apply(conversationId, messageId, decision(false, "Ola!", WorkContextAction.NONE, "", "", List.of()));

		assertThat(runId).isEmpty();
		assertThat(conversations.messages("user-1", conversationId)).extracting(Message::role, Message::content).containsExactly(
				org.assertj.core.groups.Tuple.tuple("user", "oi"), org.assertj.core.groups.Tuple.tuple("assistant", "Ola!"));
		assertThat(runs.summaries(conversationId, null)).isEmpty();
	}

	@Test
	void tasksActivateTheContextAndStartARun() {
		Optional<String> runId = handler.apply(conversationId, messageId, decision(true, "Vou analisar.", WorkContextAction.ACTIVATE, "ctx-dados", "",
				List.of(new PlannedTask("Analisar setembro", "cartao em setembro", "agent-mensuracao"))));

		AgentRun run = runs.get(runId.orElseThrow());
		assertThat(run.status()).isEqualTo(AgentRunStatus.RUNNING);
		assertThat(run.tasks()).singleElement().satisfies(task -> {
			assertThat(task.subagentSelected()).isEqualTo("agent-mensuracao");
			assertThat(task.input()).containsEntry("enriched_content", "cartao em setembro");
		});
		assertThat(contexts.active(conversationId)).get().satisfies(active -> {
			assertThat(active.workContextId()).isEqualTo("ctx-dados");
			assertThat(active.lastAgentRunId()).isEqualTo(run.id());
		});
		assertThat(runs.summaries(conversationId, messageId)).singleElement().satisfies(summary -> assertThat(summary.workContextId()).isEqualTo("ctx-dados"));
	}

	@Test
	void subagentOutsideTheSelectedContextIsRejected() {
		assertThatThrownBy(() -> handler.apply(conversationId, messageId, decision(true, "", WorkContextAction.ACTIVATE, "ctx-dados", "",
				List.of(new PlannedTask("x", "y", "agent-outro"))))).isInstanceOf(IllegalStateException.class);
		assertThat(runs.summaries(conversationId, null)).isEmpty();
	}

	@Test
	void tasksWithoutAnyContextAreRejected() {
		assertThatThrownBy(() -> handler.apply(conversationId, messageId, decision(true, "", WorkContextAction.NONE, "", "",
				List.of(new PlannedTask("x", "y", "agent-mensuracao"))))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void contextActionIsIgnoredWhenThereAreNoTasks() {
		Optional<String> runId = handler.apply(conversationId, messageId, decision(false, "Claro.", WorkContextAction.KEEP, "", "", List.of()));

		assertThat(runId).isEmpty();
		assertThat(contexts.active(conversationId)).isEmpty();
		assertThat(conversations.recent(conversationId, 1)).singleElement().satisfies(message -> assertThat(message.content()).isEqualTo("Claro."));
	}

	@Test
	void rejectedPlanLeavesNoTrace() {
		String requestId = humanInput.create(conversationId, "run-1", "task-1", "Qual segmento?").id();
		Artifact insight = publishInsight("Insight setembro");

		assertThatThrownBy(() -> handler.apply(conversationId, messageId, new PlannerDecision(true, "", WorkContextAction.ACTIVATE, "ctx-dados", requestId,
				insight.id(), List.of(new PlannedTask("x", "y", "agent-outro"))))).isInstanceOf(IllegalStateException.class);

		assertThat(humanInput.pendingForConversation(conversationId)).hasSize(1);
		assertThat(contexts.definitionIds(conversationId)).isEmpty();
		assertThat(runs.summaries(conversationId, null)).isEmpty();
	}

	@Test
	void switchingContextPausesTheActiveOneAndReturningReusesItsInstance() {
		var dados = contexts.activate(conversationId, "ctx-dados");
		var outro = contexts.activate(conversationId, "ctx-outro");

		assertThat(contexts.active(conversationId)).get().extracting(c -> c.id()).isEqualTo(outro.id());
		assertThat(contexts.paused(conversationId)).extracting(c -> c.id()).containsExactly(dados.id());

		var back = contexts.activate(conversationId, "ctx-dados");

		assertThat(back.id()).isEqualTo(dados.id());
		assertThat(contexts.paused(conversationId)).extracting(c -> c.id()).containsExactly(outro.id());
		assertThat(contexts.definitionIds(conversationId)).hasSize(2);
		assertThat(contexts.paused(conversationId)).allSatisfy(c -> assertThat(c.status()).isEqualTo(ConversationWorkContextStatus.PAUSED));
	}

	@Test
	void answeringAPendingRequestMarksItAnsweredOnlyWithinTheConversation() {
		String requestId = humanInput.create(conversationId, "run-1", "task-1", "Qual segmento?").id();
		humanInput.answer("other-conversation", requestId, messageId);
		assertThat(humanInput.pendingForConversation(conversationId)).hasSize(1);

		handler.apply(conversationId, messageId, decision(false, "Entendi.", WorkContextAction.NONE, "", requestId, List.of()));

		assertThat(humanInput.pendingForConversation(conversationId)).isEmpty();
		assertThat(mongo.findById(requestId, com.lamorim.hubdeia.domain.model.HumanInputRequest.class)).satisfies(request -> {
			assertThat(request.status()).isEqualTo(HumanInputRequestStatus.ANSWERED);
			assertThat(request.answerMessageId()).isEqualTo(messageId);
		});
	}

	@Test
	void reactivatingAnArtifactAnswersWithAConfirmationWhenThePlannerSaidNothing() {
		Artifact old = publishInsight("Insight setembro");
		publishInsight("Insight jovens");

		handler.apply(conversationId, messageId, decisionReactivating("", old.id()));

		assertThat(artifacts.list(conversationId)).filteredOn(view -> view.id().equals(old.id())).singleElement()
				.satisfies(view -> assertThat(view.status().name()).isEqualTo("ACTIVE"));
		assertThat(conversations.recent(conversationId, 1)).singleElement().satisfies(message -> {
			assertThat(message.role()).isEqualTo("assistant");
			assertThat(message.content()).contains("Insight setembro").contains("versao 1");
		});
	}

	@Test
	void unknownArtifactIdNeverLetsThePlannerClaimARestore() {
		handler.apply(conversationId, messageId, decisionReactivating("Voltei para o briefing anterior.", "nao-existe"));

		assertThat(conversations.recent(conversationId, 1)).singleElement()
				.satisfies(message -> assertThat(message.content()).isEqualTo("Nao encontrei essa versao para retomar."));
	}

	private Artifact publishInsight(String title) {
		AgentRun run = new AgentRun("run-x", conversationId, AgentRunStatus.RUNNING, List.of(), List.of(), List.of(), messageId, null, null, "", Instant.now(), null);
		Task task = Task.create("task-" + System.nanoTime(), title, "agent-mensuracao", "", 3, Instant.now());
		return artifacts.publish(run, task, new TaskExecutionResult(task.id(), "agent-mensuracao", Map.of("content", title), List.of(), List.of(), List.of(), List.of()))
				.orElseThrow();
	}

	private PlannerDecision decision(boolean createTasks, String message, WorkContextAction action, String contextId, String requestId, List<PlannedTask> tasks) {
		return new PlannerDecision(createTasks, message, action, contextId, requestId, "", tasks);
	}

	private PlannerDecision decisionReactivating(String message, String artifactId) {
		return new PlannerDecision(false, message, WorkContextAction.NONE, "", "", artifactId, List.of());
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/planner/ConversationPlannerAgentTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.run.AgentRunStore;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.AgentRunStatus;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.TaskStatus;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;

/** The planner decides from what really happened in the conversation, so that is what it is handed. */
class ConversationPlannerAgentTest extends MongoIntegrationTest {

	@MockitoBean
	OpenAiPlannerClient client;

	@Autowired
	ConversationPlannerAgent planner;
	@Autowired
	ConversationService conversations;
	@Autowired
	AgentRunStore runStore;

	String conversationId;
	String requestId;

	@BeforeEach
	void conversationWithHistory() {
		var first = conversations.create("user-1", "Quantos page_views tivemos?");
		conversationId = first.conversationId();
		conversations.appendAssistantMessage(conversationId, "Foram 500.000 page_views.");
		requestId = conversations.appendUserMessage("user-1", conversationId, "Gere um grafico desses dados?").messageId();
		when(client.plan(any())).thenReturn(new PlannerDecision(false, "ok", WorkContextAction.NONE, "", "", "", List.of()));
	}

	@Test
	void theLastThreeRunsAreHandedOverOldestFirstWithWhoDidWhatAndTheResultAsPlainText() {
		Visual table = new Visual("viz1", "Tabela", VisualFormat.HTML, "<section class=\"data-table\"><table></table></section>");
		for (int index = 1; index <= 4; index++) {
			runStore.insert(run(index, new TaskOutput("Resultado " + index + " [[visualization:viz1]]", List.of(table)), TaskStatus.FINISHED));
		}

		PlanningInput input = planned();

		assertThat(input.recentRuns()).extracting(PlanningRunSummary::runId).containsExactly("run-2", "run-3", "run-4");
		PlanningTaskSummary task = input.recentRuns().getLast().tasks().getFirst();
		assertThat(task.subagentId()).isEqualTo("agent-mensuracao");
		assertThat(task.description()).isEqualTo("Tarefa 4");
		assertThat(task.status()).isEqualTo("FINISHED");
		assertThat(task.request()).isEqualTo("Pedido 4");
		assertThat(task.result()).isEqualTo("Resultado 4 [visual: Tabela]");
	}

	@Test
	void longRequestsAndResultsAreCut() {
		runStore.insert(run(1, new TaskOutput("r".repeat(5000), List.of()), TaskStatus.FINISHED));

		PlanningTaskSummary task = planned().recentRuns().getFirst().tasks().getFirst();

		assertThat(task.result().length()).isLessThan(800);
		assertThat(task.result()).endsWith("...");
	}

	@Test
	void aCancelledTaskShowsAsSuchWithNoResult() {
		runStore.insert(run(1, new TaskOutput("", List.of()), TaskStatus.CANCELLED));

		PlanningTaskSummary task = planned().recentRuns().getFirst().tasks().getFirst();

		assertThat(task.status()).isEqualTo("CANCELLED");
		assertThat(task.result()).isEmpty();
	}

	@Test
	void theRequestAndOnlyTheMessagesBeforeItGoWithIt() {
		PlanningInput input = planned();

		assertThat(input.currentRequest()).isEqualTo("Gere um grafico desses dados?");
		assertThat(input.history()).extracting(message -> message.content()).containsExactly("Quantos page_views tivemos?", "Foram 500.000 page_views.");
		assertThat(input.recentRuns()).isEmpty();
	}

	private PlanningInput planned() {
		planner.plan(conversationId, requestId);
		ArgumentCaptor<PlanningInput> captor = ArgumentCaptor.forClass(PlanningInput.class);
		verify(client).plan(captor.capture());
		return captor.getValue();
	}

	private AgentRun run(int index, TaskOutput output, TaskStatus status) {
		Instant at = Instant.now().minus(10 - index, ChronoUnit.MINUTES);
		Task task = new Task("task-" + index, "Tarefa " + index, "agent-mensuracao", status, Map.of("enriched_content", "Pedido " + index),
				output.toMap(), List.of(), 1, 3, at, at);
		return new AgentRun("run-" + index, conversationId, AgentRunStatus.FINISHED, List.of(task), List.of(), List.of(), "message-" + index,
				null, null, "AgentRun finalizado.", at, at);
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/planner/OpenAiPlannerClientTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.lamorim.hubdeia.application.ArtifactView;
import com.lamorim.hubdeia.application.ConversationMemorySanitizer;
import com.lamorim.hubdeia.application.llm.OpenAiChatClient;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;
import com.lamorim.hubdeia.domain.model.Message;

/** What the planner model is told: the prompt must reference exactly the context the application really sends. */
class OpenAiPlannerClientTest {

	private final OpenAiChatClient chat = mock(OpenAiChatClient.class);
	private final OpenAiPlannerClient client = new OpenAiPlannerClient(chat, "model-x", new ConversationMemorySanitizer());

	@BeforeEach
	void modelAnswersWithADirectReply() {
		when(chat.completeJson(any())).thenReturn(Document.parse("""
				{"should_create_tasks": false, "response_message": "ok", "work_context_action": "NONE", "work_context_id": "",
				 "related_human_input_request_id": "", "reactivate_artifact_id": "", "tasks": []}"""));
	}

	@Test
	void theSystemPromptTellsThePlannerToReadTheContextBeforeDecidingAndNeverToProduceVisualsItself() {
		String prompt = request().getList("messages", Document.class).getFirst().getString("content");

		assertThat(prompt).contains("LEIA O CONTEXTO ANTES DE DECIDIR").contains("CONTINUACAO de trabalho de especialista");
		assertThat(prompt).contains("graficos, tabelas, calculos e numeros novos nunca saem de voce");
		assertThat(prompt).contains("DEVOLVA o trabalho ao mesmo especialista");
		assertThat(prompt).contains("O especialista NAO ve a conversa");
	}

	@Test
	void everyContextFieldThePromptMentionsIsReallyInThePlanningInput() {
		String prompt = request().getList("messages", Document.class).getFirst().getString("content");
		Document planningInput = planningInputOf(request());

		List.of("recent_runs", "artifacts", "active_context", "paused_contexts", "pending_human_input_requests", "current_request", "available_work_contexts")
				.forEach(field -> {
					assertThat(prompt).as("prompt mentions " + field).contains(field);
					assertThat(planningInput).as("input carries " + field).containsKey(field);
				});
	}

	@Test
	void recentRunsReachTheModelWithWhoWasAskedWhatAndWhatCameBack() {
		PlanningTaskSummary task = new PlanningTaskSummary("agent-mensuracao", "Total de page_views", "FINISHED", "quantos page_views?", "500.000 page_views");
		PlanningInput input = new PlanningInput("Gere um grafico desses dados?", List.of(), null, List.of(),
				List.of(new PlanningRunSummary("run-1", "FINISHED", List.of(task))), List.of(), List.of(), List.of());

		client.plan(input);

		Document sent = planningInputOf(captured());
		List<Document> runs = sent.getList("recent_runs", Document.class);
		assertThat(runs).hasSize(1);
		assertThat(runs.getFirst().getString("agent_run_id")).isEqualTo("run-1");
		Document sentTask = runs.getFirst().getList("tasks", Document.class).getFirst();
		assertThat(sentTask.getString("subagent_id")).isEqualTo("agent-mensuracao");
		assertThat(sentTask.getString("request")).isEqualTo("quantos page_views?");
		assertThat(sentTask.getString("result")).isEqualTo("500.000 page_views");
	}

	@Test
	void artifactsTellTheModelWhichSpecialistProducedThem() {
		ArtifactView artifact = new ArtifactView("a1", "INSIGHT", 1, ArtifactStatus.ACTIVE, false, "Page views", "500.000", "500.000", List.of(),
				null, "agent-mensuracao", "run-1", Instant.now());
		PlanningInput input = new PlanningInput("x", List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(artifact));

		client.plan(input);

		assertThat(planningInputOf(captured()).getList("artifacts", Document.class).getFirst().getString("subagent_id")).isEqualTo("agent-mensuracao");
	}

	@Test
	void theChatHistoryIsSentBeforeTheRequestWithoutRenderedVisuals() {
		Message earlier = new Message("m1", "c1", "assistant", "Resultado.\n\n<!--visual:start-->\n\n<table></table>\n\n<!--visual:end-->", Instant.now());
		PlanningInput input = new PlanningInput("e agora?", List.of(earlier), null, List.of(), List.of(), List.of(), List.of(), List.of());

		client.plan(input);

		List<Document> messages = captured().getList("messages", Document.class);
		assertThat(messages).extracting(message -> message.getString("role")).containsExactly("system", "assistant", "user");
		assertThat(messages.get(1).getString("content")).contains("[visual omitido da memoria]").doesNotContain("<table>");
	}

	private Document request() {
		client.plan(new PlanningInput("pedido", List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of()));
		return captured();
	}

	private Document captured() {
		ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
		verify(chat, org.mockito.Mockito.atLeastOnce()).completeJson(captor.capture());
		return captor.getValue();
	}

	private static Document planningInputOf(Document request) {
		List<Document> messages = request.getList("messages", Document.class);
		return Document.parse(messages.getLast().getString("content"));
	}
}
```

**Arquivo `src/test/java/com/lamorim/hubdeia/application/planner/PlannerLiveEvalTest.java`** (literal):

```java
package com.lamorim.hubdeia.application.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamorim.hubdeia.domain.model.Message;

/**
 * Live evaluation of the planner prompt against the real model, replaying a real conversation
 * (src/test/resources/planner-eval) in which the planner once answered chart requests by itself
 * instead of handing them back to the specialist. It calls OpenAI, so it only runs on demand:
 * {@code ./mvnw test -Dtest=PlannerLiveEvalTest -Dplanner.live=true} (the key comes from the same
 * .env the application uses).
 */
@SpringBootTest(properties = "hub.bootstrap.enabled=false")
@EnabledIfSystemProperty(named = "planner.live", matches = "true")
class PlannerLiveEvalTest {

	private static final int ROUNDS = 4;
	/** Index of the user messages in the fixture that asked for charts. */
	private static final int FIRST_CHART_REQUEST = 8;
	private static final int SECOND_CHART_REQUEST = 10;

	@Autowired
	OpenAiPlannerClient planner;

	private JsonNode conversation;

	private JsonNode conversation() throws IOException {
		if (conversation == null) {
			conversation = new ObjectMapper().readTree(new ClassPathResource("planner-eval/page-view-conversation.json").getInputStream());
		}
		return conversation;
	}

	@Test
	void chartRequestOnDataTheSpecialistJustProducedGoesBackToTheSpecialist() throws IOException {
		List<PlannerDecision> decisions = decide(FIRST_CHART_REQUEST, 2);
		report("chart request #1: 'Gere um grafico desses dados?'", decisions);

		assertThat(decisions).allSatisfy(this::assertDelegatedToMeasurement);
	}

	@Test
	void secondChartRequestAfterAFailedAttemptStillGoesToTheSpecialist() throws IOException {
		List<PlannerDecision> decisions = decide(SECOND_CHART_REQUEST, 2);
		report("chart request #2: 'me traga graficos relevantes'", decisions);

		assertThat(decisions).allSatisfy(this::assertDelegatedToMeasurement);
	}

	@Test
	void theTaskCarriesWhatTheSpecialistNeedsBecauseItNeverSeesTheChat() throws IOException {
		PlannerDecision decision = decide(FIRST_CHART_REQUEST, 2).getFirst();
		report("enriched content", List.of(decision));

		assertThat(decision.tasks()).isNotEmpty();
		assertThat(decision.tasks().getFirst().enrichedContent().toLowerCase()).contains("page_view");
	}

	@Test
	void plainConversationStillGetsADirectAnswer() throws IOException {
		List<PlannerDecision> decisions = new ArrayList<>();
		for (int round = 0; round < ROUNDS; round++) {
			decisions.add(planner.plan(input(conversation().get("messages").size(), "Obrigado, ficou claro!", 2)));
		}
		report("control: thanks", decisions);

		assertThat(decisions).allSatisfy(decision -> assertThat(decision.hasTasks()).isFalse());
	}

	@Test
	void explainingWhatIsAlreadyWrittenStillGetsADirectAnswer() throws IOException {
		List<PlannerDecision> decisions = new ArrayList<>();
		for (int round = 0; round < ROUNDS; round++) {
			decisions.add(planner.plan(input(conversation().get("messages").size(), "Voce pode explicar em uma frase a limitacao que voce citou?", 2)));
		}
		report("control: explain existing text", decisions);

		assertThat(decisions).allSatisfy(decision -> assertThat(decision.hasTasks()).isFalse());
	}

	private void assertDelegatedToMeasurement(PlannerDecision decision) {
		assertThat(decision.hasTasks()).as("must hand the request to a specialist, not answer by itself: %s", decision.responseMessage()).isTrue();
		assertThat(decision.tasks()).extracting(PlannedTask::subagentId).containsOnly("agent-mensuracao");
		assertThat(decision.workContextAction()).isIn(WorkContextAction.KEEP, WorkContextAction.ACTIVATE);
	}

	private List<PlannerDecision> decide(int requestIndex, int runsAlreadyDone) throws IOException {
		String request = conversation().get("messages").get(requestIndex).get("content").asText();
		List<PlannerDecision> decisions = new ArrayList<>();
		for (int round = 0; round < ROUNDS; round++) {
			decisions.add(planner.plan(input(requestIndex, request, runsAlreadyDone)));
		}
		return decisions;
	}

	/** The state the planner saw at that point of the real conversation: everything before the request. */
	private PlanningInput input(int requestIndex, String request, int runsAlreadyDone) throws IOException {
		List<Message> history = new ArrayList<>();
		for (int index = 0; index < requestIndex; index++) {
			JsonNode message = conversation().get("messages").get(index);
			history.add(new Message("m" + index, "conversation-1", message.get("role").asText(), message.get("content").asText(), Instant.now()));
		}
		PlanningContextState active = new PlanningContextState("ctx-instance-1", "work-context-analise-dados", "run-2", "AgentRun finalizado.");
		PlanningWorkContextView context = new PlanningWorkContextView("work-context-analise-dados", "ANALISE_DE_DADOS", "Analise de Dados",
				"Analises de clientes, segmentacao, campanhas, metricas e desempenho.",
				"Use dados verificaveis, explique limitacoes e delegue para os especialistas disponiveis quando a solicitacao exigir consulta de dados.", List.of(
				new PlanningSubagentView("agent-mensuracao", "Agente Mensuracao",
						"Responsavel por desempenho de campanhas, metricas, KPIs, taxas de conversao e resultados de marketing.", List.of("INSIGHT"), List.of()),
				new PlanningSubagentView("agent-mapa-clientes", "Agente Mapa de Clientes",
						"Responsavel por dados de clientes, perfil, segmentacao, renda, historico de interacoes e propensao de compra.", List.of(), List.of())));
		return new PlanningInput(request, history, active, List.of(), recentRuns(runsAlreadyDone), List.of(), List.of(context), List.of());
	}

	/** The runs that had already happened at that point of the real conversation, as the application would summarise them. */
	private List<PlanningRunSummary> recentRuns(int count) throws IOException {
		List<PlanningRunSummary> runs = new ArrayList<>();
		JsonNode all = conversation().get("runs");
		for (int index = 0; index < Math.min(count, all.size()); index++) {
			List<PlanningTaskSummary> tasks = new ArrayList<>();
			for (JsonNode task : all.get(index).get("tasks")) {
				tasks.add(new PlanningTaskSummary(task.get("subagent").asText(), task.get("description").asText(), task.get("status").asText(),
						cut(task.path("asked").asText(""), 400), cut(task.path("result").asText(""), 700)));
			}
			runs.add(new PlanningRunSummary("run-" + (index + 1), all.get(index).get("status").asText(), tasks));
		}
		return runs;
	}

	private static String cut(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max) + "...";
	}

	private static void report(String title, List<PlannerDecision> decisions) {
		System.out.println("=== PLANNER LIVE: " + title);
		decisions.forEach(decision -> System.out.println("  delegate=" + decision.hasTasks() + " action=" + decision.workContextAction()
				+ " agents=" + decision.tasks().stream().map(PlannedTask::subagentId).toList()
				+ " | reply=" + shorten(decision.responseMessage())
				+ (decision.hasTasks() ? " | enriched=" + shorten(decision.tasks().getFirst().enrichedContent()) : "")));
	}

	private static String shorten(String value) {
		String flat = value == null ? "" : value.replaceAll("\\s+", " ");
		return flat.length() > 220 ? flat.substring(0, 220) + "..." : flat;
	}
}
```

### 4.5 Catálogo

**Arquivo `src/test/java/com/lamorim/hubdeia/config/BootstrapConfigurationTest.java`** (literal):

```java
package com.lamorim.hubdeia.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.repository.ProviderRepository;
import com.lamorim.hubdeia.domain.repository.SkillRepository;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.ToolRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

class BootstrapConfigurationTest extends MongoIntegrationTest {

	@Autowired
	ProviderRepository providers;
	@Autowired
	ToolRepository tools;
	@Autowired
	SkillRepository skills;
	@Autowired
	SubagentRepository subagents;
	@Autowired
	WorkContextRepository workContexts;

	private void bootstrap(boolean enabled) throws Exception {
		new BootstrapConfiguration().bootstrapCatalog(enabled, providers, tools, skills, subagents, workContexts).run(null);
	}

	@Test
	void seedsAnEmptyDatabase() throws Exception {
		bootstrap(true);

		assertThat(providers.count()).isEqualTo(1);
		assertThat(tools.count()).isEqualTo(1);
		assertThat(skills.count()).isEqualTo(4);
		assertThat(subagents.count()).isEqualTo(2);
		assertThat(workContexts.count()).isEqualTo(1);
		assertThat(subagents.findById("agent-mensuracao").orElseThrow().produces()).containsExactly("INSIGHT");
	}

	@Test
	void whatLivesInTheDatabaseIsNeverTouchedAgain() throws Exception {
		bootstrap(true);
		Subagent edited = subagents.findById("agent-mensuracao").orElseThrow();
		subagents.save(new Subagent(edited.id(), "Renomeado no Mongo", edited.description(), edited.systemPrompt(), edited.skillIds(),
				edited.providers(), List.of("INSIGHT", "RELATORIO"), edited.consumes(), false));
		skills.deleteById("skill-customer-profile");

		bootstrap(true);

		Subagent afterRestart = subagents.findById("agent-mensuracao").orElseThrow();
		assertThat(afterRestart.name()).isEqualTo("Renomeado no Mongo");
		assertThat(afterRestart.enabled()).isFalse();
		assertThat(afterRestart.produces()).containsExactly("INSIGHT", "RELATORIO");
		assertThat(skills.existsById("skill-customer-profile")).as("a removed entry must not come back").isFalse();
	}

	@Test
	void disabledBootstrapSeedsNothing() throws Exception {
		bootstrap(false);

		assertThat(subagents.count()).isZero();
	}
}
```

## 5. Fixture do teste live (literal)

O arquivo `src/test/resources/planner-eval/page-view-conversation.json` é uma conversa **real** (12 mensagens e 2 runs) usada pelo `PlannerLiveEvalTest`. Os índices das mensagens do usuário que pedem gráficos são `8` e `10`. Estrutura: `{ "messages": [ {role, content} ], "runs": [ {status, tasks: [ {subagent, description, status, asked, result} ]} ] }`.

**Arquivo `src/test/resources/planner-eval/page-view-conversation.json`** (literal):

```json
{
 "messages": [
  {
   "role": "user",
   "content": "Boa noite!"
  },
  {
   "role": "assistant",
   "content": "Boa noite! Como posso te ajudar hoje?"
  },
  {
   "role": "user",
   "content": "Quero saber quantos clientes converteram esse ultimo mês."
  },
  {
   "role": "assistant",
   "content": "Segue abaixo o resultado da análise de conversão de clientes no último mês, gerado pelo agente de mensuração."
  },
  {
   "role": "assistant",
   "content": "No momento, não é possível calcular o número de clientes convertidos no último mês, pois os dados disponíveis não contêm informações sobre ações de conversão (como compras, assinaturas ou leads efetivados). A base de dados acessível tem apenas visualizações de páginas, referentes a um período muito curto (apenas alguns minutos do dia 4 de setembro de 2025), e não inclui eventos ou tabelas relacionados a transações efetivas, que são necessários para mensurar conversões.\n\nIsso significa que não é possível fornecer o número absoluto de clientes convertidos, nem a taxa de conversão ou a variação em relação ao mês anterior, com base nos dados atuais.\n\nRecomendação: Para calcular esses indicadores, é necessário o acesso a dados de transações ou de eventos de conversão definidos pelo funil principal do negócio. Caso tenha acesso a essas tabelas ou registros, posso ajudá-lo a fazer a análise adequada. Se houver outro dataset disponível, por favor, informe para que eu possa refazer a consulta."
  },
  {
   "role": "user",
   "content": "Então vamos analisar page_views, quantos tivemos no ultimo mês?"
  },
  {
   "role": "assistant",
   "content": "A seguir estão os resultados da mensuração solicitada sobre o total de *page_views* do último mês, incluindo o esclarecimento do período exato considerado.  \n\nRevise os detalhes apresentados pelo agente de mensuração e, se quiser, posso ajudar a interpretar os números, definir metas ou sugerir próximos passos com base nesses dados."
  },
  {
   "role": "assistant",
   "content": "O total de page_views registrados no período disponível na base de dados é de 500.000.\n\nPeríodo considerado: a janela de tempo abrange exclusivamente de 2025-09-04 às 23:27:19.835 até 2025-09-04 às 23:29:02.649 (UTC), ou seja, cerca de 1 minuto e 43 segundos.\n\nLimitação importante: a base de dados não cobre um mês inteiro – na verdade, cobre apenas 1,72 minutos de um único dia. Portanto, o número informado (500.000 page_views) refere-se somente a esse curto intervalo, e não é representativo de um mês cheio de atividade. Não é possível, com os dados fornecidos, gerar análises ou tendências mensais, semanais ou de longo prazo. \n\nSe houver necessidade de análises por períodos mais amplos, será indispensável o acesso a uma base de dados com cobertura temporal adequada."
  },
  {
   "role": "user",
   "content": "Gere um gráfico desses dados?"
  },
  {
   "role": "assistant",
   "content": "Com os dados que já temos (500.000 page_views em ~1min43s), posso te sugerir como montar um gráfico, mas preciso saber o formato que você prefere:\n\n1) Um gráfico de barra simples, mostrando apenas o total de page_views nesse intervalo.\n2) Um gráfico de linha ou barras por pequenos intervalos de tempo (por exemplo, a cada 10 segundos), mas isso exigiria detalhamento por timestamp que não está descrito no resumo.\n\nVocê quer um gráfico bem simples, só com o total desse período (opção 1), ou quer tentar algo mais detalhado (opção 2)? Posso te devolver o código em Python (matplotlib ou seaborn), em SQL + ferramenta de BI, ou até um modelo em texto de como ficaria o gráfico."
  },
  {
   "role": "user",
   "content": "Na analise de pageviews, me traga graficos relevantes"
  },
  {
   "role": "assistant",
   "content": "Com os dados já analisados (500.000 page_views em ~1min43s, sem detalhamento por timestamp), não dá para montar gráficos de série temporal ou por segmento de forma fiel, mas posso te sugerir visualizações relevantes e já devolver o código/modelo para você usar na sua ferramenta.\n\nSugestões de gráficos com o que temos:\n\n1) Gráfico de barra – volume total no período\n   - Eixo X: \"Período analisado (1min43s em 04/09/2025)\"\n   - Eixo Y: \"Total de page_views\"\n   - Valor: 500.000\n   - Uso: bom para comunicar rapidamente o volume concentrado em um intervalo muito curto.\n\n2) Gráfico de barra comparando \"mês completo\" vs \"janela disponível\" (gráfico conceitual)\n   - Barra 1: \"Mês completo\" (marcada como \"sem dados / não disponível\")\n   - Barra 2: \"Janela de 1min43s\" = 500.000 page_views\n   - Uso: evidenciar para stakeholders que a base é extremamente limitada e não representa o mês inteiro.\n\n3) Gráfico de taxa média por segundo (métrica derivada)\n   - Cálculo aproximado: 500.000 page_views / 103 segundos ≈ 4.854 page_views/segundo\n   - Gráfico de barra:\n     - X: \"Média por segundo\"\n     - Y: ~4.854 page_views/segundo\n   - Uso: mostrar a intensidade de tráfego nesse curto intervalo.\n\nSe você me disser a ferramenta que usa (Python/matplotlib, Power BI, Looker, Excel, etc.), te devolvo o código ou o passo a passo exato para montar esses gráficos."
  }
 ],
 "runs": [
  {
   "status": "FINISHED",
   "tasks": [
    {
     "subagent": "agent-mensuracao",
     "description": "Calcular quantos clientes converteram no último mês.",
     "status": "FINISHED",
     "asked": "O usuário quer saber quantos clientes converteram no último mês. \n\nDefinições e escopo esperados:\n- Conversão: considerar como clientes que realizaram a ação principal definida pelo funil padrão (por exemplo, compra efetivada, assinatura contratada ou lead qualificado convertido em cliente pagante). Caso existam múltiplas definições de conversão, utilizar a principal/metrificação padrão do negócio.\n- Período: \"último mês\" = mês calendário imediatamente anterior ao mês atual, usando o fuso/horário oficial padrão da conta.\n- Resultado esperado: \n  - Número absoluto de clientes convertidos no período.\n  - Se disponível sem esforço extra, informar também a taxa de conversão e o comparativo com o mês anterior (variação percentual).\n- Segmentações: usar visão geral (total). Só detalhar por segmento se for padrão nos relatórios.\n\nEntregável:\n- Número de clientes convertidos no último mês.\n- Breve comentário sobre o que esse número representa (ex.: acima/abaixo do período anterior, se essa informação estiver prontamente disponível).",
     "result": "No momento, não é possível calcular o número de clientes convertidos no último mês, pois os dados disponíveis não contêm informações sobre ações de conversão (como compras, assinaturas ou leads efetivados). A base de dados acessível tem apenas visualizações de páginas, referentes a um período muito curto (apenas alguns minutos do dia 4 de setembro de 2025), e não inclui eventos ou tabelas relacionados a transações efetivas, que são necessários para mensurar conversões.\n\nIsso significa que não é possível fornecer o número absoluto de clientes convertidos, nem a taxa de conversão ou a variação em relação ao mês anterior, com base nos dados atuais.\n\nRecomendação: Para calcular esses indicadores, é necessário o acesso a dados de transações ou de eventos de conversão definidos pelo funil principal do negócio. Caso tenha acesso a essas tabelas ou registros, posso ajudá-lo a fazer a análise adequada. Se houver outro dataset disponível, por favor, informe para que eu possa refazer a consulta."
    }
   ]
  },
  {
   "status": "FINISHED",
   "tasks": [
    {
     "subagent": "agent-mensuracao",
     "description": "Calcular o total de page_views no último mês com base na base de dados disponível e esclarecer o período exato considerado.",
     "status": "FINISHED",
     "asked": "O usuário quer saber: \"quantos page_views tivemos no último mês?\".\n\nInstruções para a análise:\n- Considerar a mesma fonte de dados utilizada anteriormente, na qual já foi identificado que há uma tabela/evento de page_views.\n- Calcular o total de page_views no \"último mês\" em relação à data máxima disponível na base. Se a base tiver apenas alguns minutos/horas de dados (como apontado no insight anterior), esclarecer essa limitação explicitamente.\n- Informar:\n  1) total de page_views no período identificado como \"último mês\" (ou o período máximo realmente disponível, se menor),\n  2) a janela de tempo exata utilizada (datas e horários de início e fim),\n  3) qualquer limitação relevante (por exemplo, se o período real de dados disponível é muito menor do que um mês).\n- Caso não seja possível chegar a um mês completo de dados, deixar claro que o número não representa um mês cheio, mas apenas o intervalo efetivamente presente na base.\n\nEntregável esperado: um INSIGHT objetivo, com números consolidados e a explicação de eventuais limitações de cobertura temporal.",
     "result": "O total de page_views registrados no período disponível na base de dados é de 500.000.\n\nPeríodo considerado: a janela de tempo abrange exclusivamente de 2025-09-04 às 23:27:19.835 até 2025-09-04 às 23:29:02.649 (UTC), ou seja, cerca de 1 minuto e 43 segundos.\n\nLimitação importante: a base de dados não cobre um mês inteiro – na verdade, cobre apenas 1,72 minutos de um único dia. Portanto, o número informado (500.000 page_views) refere-se somente a esse curto intervalo, e não é representativo de um mês cheio de atividade. Não é possível, com os dados fornecidos, gerar análises ou tendências mensais, semanais ou de longo prazo. \n\nSe houver necessidade de análises por períodos mais amplos, será indispensável o acesso a uma base de dados com cobertura temporal adequada."
    }
   ]
  }
 ]
}
```

## Armadilhas e decisões não óbvias

1. `MongoIntegrationTest` apaga **documentos**, não coleções, para preservar os índices (inclusive os únicos que alguns testes exercitam).
2. Os testes de concorrência (`parallelTaskUpdates...`, `parallelPublishes...`) dependem dos índices únicos e do update atômico; não os "simplifique" com `synchronized`.
3. Em `Mockito`, para trocar o comportamento de um *mock* já configurado use `doAnswer(...).when(mock).metodo(...)` (e não `when(...)`, que invocaria a resposta anterior com argumentos nulos).
4. Os testes do orquestrador usam apenas dados em memória/Mongo; os "visuais" são `Visual` fictícios, não saídas reais do Genie.
5. O teste live depende de um modelo real e da fixture: a contagem de sucessos pode variar entre execuções.

## Critérios de aceite do projeto inteiro

- `./mvnw test` com o Mongo no ar: **0 falhas, 0 erros** (72 testes; 5 ignorados).
- Rodar a suíte duas vezes seguidas dá o mesmo resultado (sem testes intermitentes).
- `./mvnw -q -DskipTests test-compile` compila produção e testes.

## Ordem de implementação sugerida

Implemente cada componente com a sua spec e traga o teste correspondente junto: domínio/persistência (`AgentRunStoreTest`, `ConversationServiceTest`), conteúdo (`TaskOutputTest`), artefatos, planejador, orquestração, catálogo e, por fim, o teste de contexto e o live.
