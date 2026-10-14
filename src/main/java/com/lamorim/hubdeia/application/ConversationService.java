package com.lamorim.hubdeia.application;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.lamorim.hubdeia.api.dto.ConversationDetailResponse;
import com.lamorim.hubdeia.api.dto.ConversationSummaryResponse;
import com.lamorim.hubdeia.api.dto.MessageAcceptedResponse;
import com.lamorim.hubdeia.domain.model.Conversation;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.repository.ConversationRepository;
import com.lamorim.hubdeia.domain.repository.MessageRepository;

/** Conversations and their chat messages. Ownership is checked here, never deeper in the stack. */
@Service
public class ConversationService {

	private static final int LAST_MESSAGE_PREVIEW = 200;

	private final ConversationRepository conversations;
	private final MessageRepository messages;
	private final MongoTemplate mongo;

	public ConversationService(ConversationRepository conversations, MessageRepository messages, MongoTemplate mongo) {
		this.conversations = conversations;
		this.messages = messages;
		this.mongo = mongo;
	}

	public MessageAcceptedResponse create(String userId, String content) {
		requireText(content);
		Instant now = Instant.now();
		Conversation saved = conversations.save(new Conversation(null, userId, preview(content), now, now));
		return accepted(saved.id(), addMessage(saved.id(), "user", content));
	}

	public MessageAcceptedResponse appendUserMessage(String userId, String conversationId, String content) {
		requireText(content);
		requireOwned(userId, conversationId);
		return accepted(conversationId, addMessage(conversationId, "user", content));
	}

	public void appendAssistantMessage(String conversationId, String content) {
		addMessage(conversationId, "assistant", content);
	}

	public void appendAssistantBlocks(String conversationId, List<FinalAnswerBlock> blocks) {
		blocks.stream().map(FinalAnswerBlock::markdown).filter(StringUtils::hasText)
				.forEach(markdown -> addMessage(conversationId, "assistant", markdown));
	}

	public Conversation requireOwned(String userId, String conversationId) {
		return conversations.findByIdAndUserId(conversationId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
	}

	public ConversationDetailResponse detail(String userId, String conversationId) {
		Conversation conversation = requireOwned(userId, conversationId);
		return new ConversationDetailResponse(conversation.id(), conversation.chatLastMessage(),
				messages.findByConversationIdOrderByCreatedAtAsc(conversationId), conversation.createdAt(), conversation.updatedAt());
	}

	public List<Message> messages(String userId, String conversationId) {
		requireOwned(userId, conversationId);
		return messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
	}

	public List<ConversationSummaryResponse> summaries(String userId) {
		return conversations.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(value -> new ConversationSummaryResponse(value.id(), value.chatLastMessage(), value.createdAt(), value.updatedAt()))
				.toList();
	}

	public Message message(String conversationId, String messageId) {
		return messages.findByIdAndConversationId(messageId, conversationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found."));
	}

	/** Up to {@code limit} messages written before {@code message}, oldest first. */
	public List<Message> historyBefore(Message message, int limit) {
		return messages.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
				message.conversationId(), message.createdAt(), PageRequest.of(0, limit)).reversed();
	}

	/** The last {@code limit} messages of the conversation, oldest first. */
	public List<Message> recent(String conversationId, int limit) {
		return messages.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit)).reversed();
	}

	private Message addMessage(String conversationId, String role, String content) {
		Message message = messages.insert(new Message(UUID.randomUUID().toString(), conversationId, role, content, MonotonicTime.next()));
		mongo.updateFirst(Query.query(where("_id").is(conversationId)),
				new Update().set("chatLastMessage", preview(content)).set("updatedAt", message.createdAt()), Conversation.class);
		return message;
	}

	private static MessageAcceptedResponse accepted(String conversationId, Message message) {
		return new MessageAcceptedResponse("ACCEPTED", conversationId, message.id());
	}

	private static String preview(String content) {
		return content.length() <= LAST_MESSAGE_PREVIEW ? content : content.substring(0, LAST_MESSAGE_PREVIEW);
	}

	private static void requireText(String value) {
		if (!StringUtils.hasText(value)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required.");
		}
	}
}
