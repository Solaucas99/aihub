package com.lamorim.hubdeia.application.orchestration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentProvider;
import com.lamorim.hubdeia.domain.model.Tool;
import com.lamorim.hubdeia.domain.repository.SkillRepository;
import com.lamorim.hubdeia.domain.repository.ToolRepository;

@Component
public class AgentSkillAccessPolicy {

	private final SkillRepository skills;
	private final ToolRepository tools;

	public AgentSkillAccessPolicy(SkillRepository skills, ToolRepository tools) {
		this.skills = skills;
		this.tools = tools;
	}

	public List<Skill> skillsFor(Subagent subagent) {
		List<String> ids = subagent.skillIds() == null ? List.of() : subagent.skillIds();
		return ids.isEmpty() ? List.of() : skills.findByIdInAndEnabledTrue(ids);
	}

	public Map<String, AllowedTool> toolsFor(Subagent subagent) {
		List<String> ids = skillsFor(subagent).stream().flatMap(skill -> safeIds(skill.toolIds()).stream()).distinct().toList();
		Map<String, AllowedTool> allowed = new LinkedHashMap<>();
		for (Tool tool : ids.isEmpty() ? List.<Tool>of() : tools.findByIdInAndEnabledTrue(ids)) {
			providerFor(subagent, tool.providerId()).ifPresent(provider -> allowed.put(tool.functionName(), new AllowedTool(tool, provider)));
		}
		return allowed;
	}

	private java.util.Optional<SubagentProvider> providerFor(Subagent subagent, String providerId) {
		return (subagent.providers() == null ? List.<SubagentProvider>of() : subagent.providers()).stream()
				.filter(SubagentProvider::enabled)
				.filter(provider -> providerId.equals(provider.providerId()))
				.findFirst();
	}

	private List<String> safeIds(List<String> ids) { return ids == null ? List.of() : ids; }

	public record AllowedTool(Tool tool, SubagentProvider provider) {}
}
