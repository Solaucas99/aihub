package com.lamorim.hubdeia.config;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HubRestClientFactory {

	public RestClient create(RestClient.Builder builder, String baseUrl, long timeoutMs) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		Duration timeout = Duration.ofMillis(Math.max(1_000, timeoutMs));
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);
		return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
	}
}
