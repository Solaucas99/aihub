package com.lamorim.hubdeia.application;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.HumanInputRequestStatus;
import com.lamorim.hubdeia.domain.repository.HumanInputRequestRepository;

/** Questions an agent run needs the user to answer. They never block the conversation. */
@Service
public class HumanInputService {

	private static final String DEFAULT_QUESTION = "Preciso de uma confirmacao para continuar.";

	private final HumanInputRequestRepository requests;
	private final MongoTemplate mongo;

	public HumanInputService(HumanInputRequestRepository requests, MongoTemplate mongo) {
		this.requests = requests;
		this.mongo = mongo;
	}

	public HumanInputRequest create(String conversationId, String runId, String taskId, String question) {
		return requests.insert(new HumanInputRequest(UUID.randomUUID().toString(), conversationId, runId, taskId,
				StringUtils.hasText(question) ? question : DEFAULT_QUESTION, HumanInputRequestStatus.PENDING, null, Instant.now(), null));
	}

	/** Marks a pending request of this conversation as answered. Unknown or already answered ids are ignored. */
	public void answer(String conversationId, String requestId, String answerMessageId) {
		if (!StringUtils.hasText(requestId)) {
			return;
		}
		mongo.updateFirst(
				Query.query(where("_id").is(requestId).and("conversationId").is(conversationId).and("status").is(HumanInputRequestStatus.PENDING)),
				new Update().set("status", HumanInputRequestStatus.ANSWERED).set("answerMessageId", answerMessageId).set("answeredAt", Instant.now()),
				HumanInputRequest.class);
	}

	public List<HumanInputRequest> pendingForConversation(String conversationId) {
		return requests.findByConversationIdAndStatus(conversationId, HumanInputRequestStatus.PENDING);
	}

	public List<HumanInputRequest> pendingForRun(String runId) {
		return requests.findByAgentRunIdAndStatus(runId, HumanInputRequestStatus.PENDING);
	}
}
