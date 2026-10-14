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
