package com.lamorim.hubdeia.application.planner;

public enum WorkContextAction {
	/** No delegation: keep whatever context is active without using it. */
	NONE,
	/** Continue in the active context. */
	KEEP,
	/** Start or return to the catalog context named by the decision. */
	ACTIVATE;

	public static WorkContextAction parse(String value) {
		try {
			return valueOf(value);
		} catch (IllegalArgumentException | NullPointerException error) {
			return NONE;
		}
	}
}
