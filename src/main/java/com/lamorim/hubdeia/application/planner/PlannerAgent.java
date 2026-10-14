package com.lamorim.hubdeia.application.planner;

public interface PlannerAgent {

	/** Decides how to handle the given user message of the conversation. */
	PlannerDecision plan(String conversationId, String messageId);
}
