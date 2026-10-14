package com.lamorim.hubdeia.domain.model;

/**
 * Provider-neutral visual content (table, chart) built by a tool. The core stores and displays it
 * without knowing which provider made it; text refers to it with a {@code [[visualization:id]]} marker.
 */
public record Visual(
		String id,
		String title,
		VisualFormat format,
		String payload
) {
}
