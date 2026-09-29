package com.lamorim.hubdeia.application.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ProviderToolRegistry {

	private final Map<String, ProviderTool> tools;

	public ProviderToolRegistry(List<ProviderTool> tools) {
		this.tools = tools.stream().collect(Collectors.toMap(ProviderTool::name, Function.identity()));
	}

	public ProviderTool get(String name) {
		ProviderTool tool = tools.get(name);
		if (tool == null) {
			throw new IllegalArgumentException("Provider tool not registered: " + name);
		}
		return tool;
	}
}
