package com.lamorim.hubdeia.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final String PROPERTY_SOURCE_NAME = "hubDotenv";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Map<String, Object> values = new LinkedHashMap<>();
		load(Path.of("js-hub", ".env"), values);
		load(Path.of(".env"), values);
		if (values.isEmpty()) {
			return;
		}

		environment.getPropertySources()
				.addAfter("systemEnvironment", new MapPropertySource(PROPERTY_SOURCE_NAME, values));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 20;
	}

	private void load(Path path, Map<String, Object> values) {
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			List<String> lines = Files.readAllLines(path);
			for (String line : lines) {
				parseLine(line, values);
			}
		} catch (IOException ignored) {
			// The app can still boot with real environment variables.
		}
	}

	private void parseLine(String rawLine, Map<String, Object> values) {
		String line = rawLine == null ? "" : rawLine.trim();
		if (line.isEmpty() || line.startsWith("#")) {
			return;
		}
		if (line.startsWith("export ")) {
			line = line.substring("export ".length()).trim();
		}

		int separator = line.indexOf('=');
		if (separator <= 0) {
			return;
		}

		String key = line.substring(0, separator).trim();
		String value = line.substring(separator + 1).trim();
		if (key.isEmpty()) {
			return;
		}
		values.putIfAbsent(key, cleanValue(value));
	}

	private String cleanValue(String value) {
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
				String unquoted = value.substring(1, value.length() - 1);
				return first == '"' ? unescapeDoubleQuoted(unquoted) : unquoted;
			}
		}
		int comment = value.indexOf(" #");
		if (comment >= 0) {
			return value.substring(0, comment).trim();
		}
		return value;
	}

	private String unescapeDoubleQuoted(String value) {
		return value
				.replace("\\n", "\n")
				.replace("\\r", "\r")
				.replace("\\t", "\t")
				.replace("\\\"", "\"")
				.replace("\\\\", "\\");
	}
}
