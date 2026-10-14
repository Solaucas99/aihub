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
