package com.lamorim.hubdeia.application.provider;

public interface ProgressReporter {

	void report(String executionStatus, String message);
}
