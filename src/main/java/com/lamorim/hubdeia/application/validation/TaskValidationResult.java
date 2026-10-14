package com.lamorim.hubdeia.application.validation;

import java.time.Instant;

public record TaskValidationResult(
		TaskValidationStatus status,
		String feedback,
		String messageToUser,
		Instant validatedAt
) {
}
