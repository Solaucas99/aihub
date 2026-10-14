package com.lamorim.hubdeia.application;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Timestamps that never repeat within this process (millisecond precision, the most MongoDB keeps),
 * so records written back to back keep a well-defined order when sorted by time.
 */
public final class MonotonicTime {

	private static final AtomicLong LAST = new AtomicLong();

	private MonotonicTime() {
	}

	public static Instant next() {
		long now = System.currentTimeMillis();
		return Instant.ofEpochMilli(LAST.updateAndGet(previous -> Math.max(previous + 1, now)));
	}
}
