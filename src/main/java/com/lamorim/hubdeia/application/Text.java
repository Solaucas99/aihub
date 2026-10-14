package com.lamorim.hubdeia.application;

public final class Text {

	private Text() {
	}

	/** Cuts the value to {@code max} characters, appending an ellipsis when something was removed. */
	public static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max) + "...";
	}
}
