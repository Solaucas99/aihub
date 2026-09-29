package com.lamorim.hubdeia.config;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.lamorim.hubdeia.domain.model.Provider;
import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Tool;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.ProviderRepository;
import com.lamorim.hubdeia.domain.repository.SkillRepository;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.ToolRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

@Configuration
public class BootstrapConfiguration {

	@Bean
	ApplicationRunner bootstrapCatalog(
			@Value("${hub.bootstrap.enabled:true}") boolean enabled,
			ProviderRepository providers,
			ToolRepository tools,
			SkillRepository skills,
			SubagentRepository subagents,
			WorkContextRepository workContexts
	) {
		return args -> {
			if (!enabled) {
				return;
			}
			ObjectMapper objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
			providers.saveAll(read(objectMapper, "bootstrap/providers.json", new TypeReference<List<Provider>>() {}));
			tools.saveAll(read(objectMapper, "bootstrap/tools.json", new TypeReference<List<Tool>>() {}));
			skills.saveAll(read(objectMapper, "bootstrap/skills.json", new TypeReference<List<Skill>>() {}));
			subagents.saveAll(read(objectMapper, "bootstrap/subagents.json", new TypeReference<List<Subagent>>() {}));
			workContexts.saveAll(read(objectMapper, "bootstrap/work-contexts.json", new TypeReference<List<WorkContext>>() {}));
		};
	}

	private <T> T read(ObjectMapper mapper, String path, TypeReference<T> type) throws IOException {
		try (var input = new ClassPathResource(path).getInputStream()) {
			return mapper.readValue(input, type);
		}
	}
}
