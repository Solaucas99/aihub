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
				List.of(new SubagentProvider("genie", "provider-genie", Map.of(), true)), true);
		Skill skill = new Skill("skill-1", "ANALYSIS", "Analise", "", "", List.of("tool-genie", "tool-other"), true);
		Tool genieTool = new Tool("tool-genie", "provider-genie", "Genie", "function", "enviarMensagemGenie", "", Map.of(), Map.of(), Map.of(), true);
		Tool otherTool = new Tool("tool-other", "provider-other", "Other", "function", "otherTool", "", Map.of(), Map.of(), Map.of(), true);

		when(skills.findByIdInAndEnabledTrue(List.of("skill-1"))).thenReturn(List.of(skill));
		when(tools.findByIdInAndEnabledTrue(List.of("tool-genie", "tool-other"))).thenReturn(List.of(genieTool, otherTool));

		assertThat(policy.toolsFor(subagent)).containsOnlyKeys("enviarMensagemGenie");
	}
}
