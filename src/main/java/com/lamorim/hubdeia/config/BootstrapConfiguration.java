package com.lamorim.hubdeia.config;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.repository.MongoRepository;

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

/**
 * First-run seed so a new environment has a working catalog. MongoDB is the source of truth: a
 * collection is seeded only while it is empty, and nothing here ever updates or recreates what
 * lives in the database afterwards. Set {@code hub.bootstrap.enabled=false} to skip it entirely.
 */
@Configuration
public class BootstrapConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(BootstrapConfiguration.class);

	private final ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

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
			seedIfEmpty(providers, "providers", new TypeReference<List<Provider>>() {});
			seedIfEmpty(tools, "tools", new TypeReference<List<Tool>>() {});
			seedIfEmpty(skills, "skills", new TypeReference<List<Skill>>() {});
			seedIfEmpty(subagents, "subagents", new TypeReference<List<Subagent>>() {});
			seedIfEmpty(workContexts, "work-contexts", new TypeReference<List<WorkContext>>() {});
		};
	}

	private <T> void seedIfEmpty(MongoRepository<T, String> repository, String file, TypeReference<List<T>> type) throws IOException {
		if (repository.count() > 0) {
			return;
		}
		try (var input = new ClassPathResource("bootstrap/" + file + ".json").getInputStream()) {
			List<T> packaged = mapper.readValue(input, type);
			repository.saveAll(packaged);
			logger.info("Seeded {} catalog entries from bootstrap/{}.json", packaged.size(), file);
		}
	}
}
