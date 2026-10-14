package com.lamorim.hubdeia.application;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.ConversationWorkContextStatus;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.ConversationWorkContextRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

/** Catalog work contexts and their per-conversation instances (at most one ACTIVE at a time). */
@Service
public class WorkContextService {

	private final WorkContextRepository definitions;
	private final ConversationWorkContextRepository instances;
	private final MongoTemplate mongo;

	public WorkContextService(WorkContextRepository definitions, ConversationWorkContextRepository instances, MongoTemplate mongo) {
		this.definitions = definitions;
		this.instances = instances;
		this.mongo = mongo;
	}

	public List<WorkContext> enabledDefinitions() {
		return definitions.findByEnabledTrue();
	}

	public Optional<WorkContext> definition(String workContextId) {
		return workContextId == null ? Optional.empty() : definitions.findById(workContextId);
	}

	public Optional<ConversationWorkContext> find(String instanceId) {
		return instanceId == null ? Optional.empty() : instances.findById(instanceId);
	}

	public Optional<ConversationWorkContext> active(String conversationId) {
		return instances.findFirstByConversationIdAndStatus(conversationId, ConversationWorkContextStatus.ACTIVE);
	}

	public List<ConversationWorkContext> paused(String conversationId) {
		return instances.findByConversationIdAndStatus(conversationId, ConversationWorkContextStatus.PAUSED);
	}

	/** Maps instance id to catalog work context id for every instance of the conversation. */
	public Map<String, String> definitionIds(String conversationId) {
		return instances.findByConversationId(conversationId).stream()
				.collect(Collectors.toMap(ConversationWorkContext::id, ConversationWorkContext::workContextId));
	}

	/** Makes the catalog context the active one, reusing its instance when the conversation already has it. */
	public ConversationWorkContext activate(String conversationId, String workContextId) {
		WorkContext definition = definition(workContextId).filter(WorkContext::enabled)
				.orElseThrow(() -> new IllegalStateException("WorkContext selecionado nao encontrado: " + workContextId));
		Instant now = Instant.now();
		ConversationWorkContext selected = mongo.findAndModify(
				Query.query(where("conversationId").is(conversationId).and("workContextId").is(definition.id())),
				new Update().set("status", ConversationWorkContextStatus.ACTIVE).set("updatedAt", now)
						.setOnInsert("_id", UUID.randomUUID().toString()).setOnInsert("createdAt", now),
				FindAndModifyOptions.options().upsert(true).returnNew(true),
				ConversationWorkContext.class);
		mongo.updateMulti(
				Query.query(where("conversationId").is(conversationId).and("status").is(ConversationWorkContextStatus.ACTIVE).and("_id").ne(selected.id())),
				new Update().set("status", ConversationWorkContextStatus.PAUSED).set("updatedAt", now),
				ConversationWorkContext.class);
		return selected;
	}

	public void recordRun(String instanceId, String runId) {
		mongo.updateFirst(Query.query(where("_id").is(instanceId)),
				new Update().set("lastAgentRunId", runId).set("updatedAt", Instant.now()), ConversationWorkContext.class);
	}
}
