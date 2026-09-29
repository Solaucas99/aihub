package com.lamorim.hubdeia.application.planner;

import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;

public interface PlannerAgent {

	PlannerDecision plan(MainConversationChatHistory conversation);
}
