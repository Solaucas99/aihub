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
