package com.lamorim.hubdeia.application.provider.genie;

import java.util.List;
import java.util.Map;

public record GenieQueryResult(
		List<GenieQueryColumn> columns,
		List<Map<String, Object>> rows,
		long rowCount,
		boolean truncated,
		String state
) {
}
