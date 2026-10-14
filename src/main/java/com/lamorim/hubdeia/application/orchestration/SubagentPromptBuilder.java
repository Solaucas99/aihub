package com.lamorim.hubdeia.application.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.TaskExecutionService;
import com.lamorim.hubdeia.application.Text;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentExecution;
import com.lamorim.hubdeia.domain.model.Task;
import com.lamorim.hubdeia.domain.model.WorkContext;

/** Assembles the system prompt, individual memory and consumed artifacts handed to a subagent. */
@Component
public class SubagentPromptBuilder {

	private static final int MEMORY_CHARS = 1200;
	private static final int ARTIFACT_CHARS = 6000;

	private final AgentSkillAccessPolicy accessPolicy;
	private final AgentRunService runs;
	private final WorkContextService contexts;
	private final TaskExecutionService executions;
	private final ArtifactService artifacts;

	public SubagentPromptBuilder(AgentSkillAccessPolicy accessPolicy, AgentRunService runs, WorkContextService contexts,
			TaskExecutionService executions, ArtifactService artifacts) {
		this.accessPolicy = accessPolicy;
		this.runs = runs;
		this.contexts = contexts;
		this.executions = executions;
		this.artifacts = artifacts;
	}

	public record SubagentPrompt(List<Document> messages, List<String> consumedArtifactIds) {
	}

	public SubagentPrompt build(String conversationId, String runId, Subagent subagent, Task task) {
		List<Artifact> consumed = artifacts.consumedBy(conversationId, subagent);
		List<Document> messages = new ArrayList<>();
		messages.add(system(instructions(runId, subagent, consumed)));
		String memory = memory(conversationId, subagent.id());
		if (StringUtils.hasText(memory)) {
			messages.add(system("Memoria individual do subagente:\n" + memory));
		}
		messages.add(new Document("role", "user").append("content", "Objetivo da tarefa: %s\nInput: %s".formatted(task.description(), task.input())));
		return new SubagentPrompt(messages, consumed.stream().map(Artifact::id).toList());
	}

	private String instructions(String runId, Subagent subagent, List<Artifact> consumed) {
		List<String> sections = new ArrayList<>();
		sections.add(subagent.systemPrompt());
		contextInstructions(runId).ifPresent(sections::add);
		List<Skill> skills = accessPolicy.skillsFor(subagent);
		if (!skills.isEmpty()) {
			sections.add("Skills permitidas:\n" + String.join("\n", skills.stream().map(skill -> "- " + skill.name() + ": " + skill.instructions()).toList()));
		}
		if (!consumed.isEmpty()) {
			sections.add("Resultados anteriores desta conversa que voce pode usar como base:\n"
					+ String.join("\n\n", consumed.stream().map(this::artifactSection).toList()));
		}
		sections.add("Use somente as tools disponibilizadas para voce. Nao alegue consultas que nao realizou.");
		sections.add("Quando uma tool retornar marcadores no formato [[visualization:id]], preserve todos exatamente uma vez na resposta final. Posicione cada marcador logo apos o paragrafo que explica seus dados ou insight. Nao altere o texto do marcador, nao o exponha ao usuario e nao escreva JSON ou HTML para representa-lo.");
		return String.join("\n\n", sections);
	}

	private Optional<String> contextInstructions(String runId) {
		return contexts.find(runs.get(runId).conversationWorkContextId())
				.flatMap(context -> contexts.definition(context.workContextId()))
				.map(WorkContext::instructions)
				.filter(StringUtils::hasText);
	}

	private String artifactSection(Artifact artifact) {
		return "[%s v%d] %s\n%s".formatted(artifact.type(), artifact.version(), artifact.title(), Text.truncate(new TaskOutput(artifact.content(), artifact.visuals()).plainText(), ARTIFACT_CHARS));
	}

	private String memory(String conversationId, String subagentId) {
		return String.join("\n", executions.recentMemory(conversationId, subagentId).stream().map(this::memoryLine).toList());
	}

	private String memoryLine(SubagentExecution execution) {
		String input = execution.input() == null ? "{}" : new Document(execution.input()).toJson();
		String output = TaskOutput.from(execution.output()).plainText();
		return "- task_id=%s input=%s output=%s".formatted(execution.taskId(), Text.truncate(input, MEMORY_CHARS), Text.truncate(output, MEMORY_CHARS));
	}

	private static Document system(String content) {
		return new Document("role", "system").append("content", content);
	}
}
