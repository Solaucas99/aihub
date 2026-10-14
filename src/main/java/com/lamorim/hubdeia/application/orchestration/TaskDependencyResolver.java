package com.lamorim.hubdeia.application.orchestration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Task;

/**
 * Orders the tasks of one run from the catalog alone: a task whose subagent consumes an artifact
 * type waits for the tasks of other subagents that produce it. The planner never declares
 * dependencies. If the catalog yields a cycle, the run falls back to fully parallel tasks.
 */
@Component
public class TaskDependencyResolver {

	private static final Logger logger = LoggerFactory.getLogger(TaskDependencyResolver.class);

	/** {@code order} lists producers before consumers; {@code prerequisites} maps a task id to the task ids it waits for. */
	public record Plan(List<Task> order, Map<String, Set<String>> prerequisites) {
	}

	public Plan resolve(List<Task> tasks, Map<String, Subagent> subagentsById) {
		Map<String, Set<String>> prerequisites = new LinkedHashMap<>();
		for (Task consumer : tasks) {
			prerequisites.put(consumer.id(), producersFor(consumer, tasks, subagentsById));
		}
		List<Task> order = topologicalOrder(tasks, prerequisites);
		if (order == null) {
			logger.warn("Artifact dependencies between tasks form a cycle; running them in parallel. tasks={}", tasks.stream().map(Task::id).toList());
			return new Plan(tasks, Map.of());
		}
		return new Plan(order, prerequisites);
	}

	private Set<String> producersFor(Task consumer, List<Task> tasks, Map<String, Subagent> subagentsById) {
		Subagent consumerAgent = subagentsById.get(consumer.subagentSelected());
		Set<String> producers = new LinkedHashSet<>();
		if (consumerAgent == null || consumerAgent.consumes().isEmpty()) {
			return producers;
		}
		for (Task other : tasks) {
			Subagent otherAgent = subagentsById.get(other.subagentSelected());
			boolean differentAgent = otherAgent != null && !otherAgent.id().equals(consumerAgent.id());
			if (differentAgent && otherAgent.produces().stream().anyMatch(consumerAgent.consumes()::contains)) {
				producers.add(other.id());
			}
		}
		return producers;
	}

	/** Kahn's algorithm; returns null when the dependencies are cyclic. */
	private List<Task> topologicalOrder(List<Task> tasks, Map<String, Set<String>> prerequisites) {
		List<Task> ordered = new ArrayList<>();
		Set<String> done = new HashSet<>();
		while (ordered.size() < tasks.size()) {
			List<Task> ready = tasks.stream()
					.filter(task -> !done.contains(task.id()) && done.containsAll(prerequisites.getOrDefault(task.id(), Set.of())))
					.toList();
			if (ready.isEmpty()) {
				return null;
			}
			ready.forEach(task -> done.add(task.id()));
			ordered.addAll(ready);
		}
		return ordered;
	}
}
