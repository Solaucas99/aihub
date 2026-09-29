package com.lamorim.hubdeia.application.validation;

import java.time.Instant;
import java.util.Map;

import org.bson.Document;

public record TaskValidationResult(
		TaskValidationStatus status,
		String feedback,
		String messageToUser,
		Instant validatedAt
) {

	public Map<String, Object> toMap() {
		return new Document()
				.append("status", status.name())
				.append("feedback", feedback)
				.append("message_to_user", messageToUser)
				.append("validated_at", validatedAt.toString());
	}
}
