package com.lamorim.hubdeia.application.provider;

import org.bson.Document;

public interface ProviderTool {

	String name();

	Document schema();

	ProviderToolResult execute(ProviderToolContext context, Document arguments);
}
