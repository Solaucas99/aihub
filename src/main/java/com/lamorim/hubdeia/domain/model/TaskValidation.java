package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

public record TaskValidation(
		String status,
		String feedback,

		@Field("message_to_user")
		String messageToUser,

		int attempt,

		@Field("validated_at")
		Instant validatedAt
) {
}
