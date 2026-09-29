package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

public record Message(
		String id,
		String role,
		String content,

		@Field("created_at")
		Instant createdAt
) {
}
