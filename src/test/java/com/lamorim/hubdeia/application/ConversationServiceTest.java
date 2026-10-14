package com.lamorim.hubdeia.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import com.lamorim.hubdeia.MongoIntegrationTest;
import com.lamorim.hubdeia.api.dto.MessageAcceptedResponse;
import com.lamorim.hubdeia.domain.model.Message;

class ConversationServiceTest extends MongoIntegrationTest {

	@Autowired
	ConversationService conversations;

	@Test
	void createStoresFirstMessageAndExposesItInTheDetail() {
		MessageAcceptedResponse accepted = conversations.create("user-1", "Como foi a campanha?");

		var detail = conversations.detail("user-1", accepted.conversationId());
		assertThat(detail.messages()).extracting(Message::id).containsExactly(accepted.messageId());
		assertThat(detail.chatLastMessage()).isEqualTo("Como foi a campanha?");
		assertThat(conversations.summaries("user-1")).hasSize(1);
	}

	@Test
	void messagesKeepInsertionOrderEvenWhenWrittenInTheSameMillisecond() {
		String conversationId = conversations.create("user-1", "primeira").conversationId();
		IntStream.range(0, 30).forEach(index -> conversations.appendAssistantMessage(conversationId, "resposta " + index));

		List<String> contents = conversations.messages("user-1", conversationId).stream().map(Message::content).toList();
		assertThat(contents).hasSize(31).first().isEqualTo("primeira");
		assertThat(contents.subList(1, 31)).containsExactlyElementsOf(IntStream.range(0, 30).mapToObj(index -> "resposta " + index).toList());
	}

	@Test
	void historyBeforeReturnsOlderMessagesOldestFirstAndExcludesTheMessageItself() {
		MessageAcceptedResponse first = conversations.create("user-1", "m1");
		conversations.appendAssistantMessage(first.conversationId(), "m2");
		conversations.appendAssistantMessage(first.conversationId(), "m3");
		MessageAcceptedResponse fourth = conversations.appendUserMessage("user-1", first.conversationId(), "m4");
		conversations.appendAssistantMessage(first.conversationId(), "m5");

		Message current = conversations.message(first.conversationId(), fourth.messageId());
		assertThat(conversations.historyBefore(current, 10)).extracting(Message::content).containsExactly("m1", "m2", "m3");
		assertThat(conversations.historyBefore(current, 2)).extracting(Message::content).containsExactly("m2", "m3");
		assertThat(conversations.recent(first.conversationId(), 2)).extracting(Message::content).containsExactly("m4", "m5");
	}

	@Test
	void otherUsersCannotReadOrWriteTheConversation() {
		String conversationId = conversations.create("user-1", "segredo").conversationId();

		assertThatThrownBy(() -> conversations.detail("user-2", conversationId)).isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(() -> conversations.appendUserMessage("user-2", conversationId, "oi")).isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(() -> conversations.create("user-1", " ")).isInstanceOf(ResponseStatusException.class);
	}
}
