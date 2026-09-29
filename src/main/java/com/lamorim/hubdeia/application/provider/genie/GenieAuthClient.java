package com.lamorim.hubdeia.application.provider.genie;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.lamorim.hubdeia.config.HubRestClientFactory;

@Component
public class GenieAuthClient {

	private final RestClient restClient;
	private final String clientId;
	private final String clientSecret;

	public GenieAuthClient(
			RestClient.Builder restClientBuilder,
			HubRestClientFactory restClients,
			@Value("${hub.genie.auth-url:}") String authUrl,
			@Value("${hub.genie.client-id:}") String clientId,
			@Value("${hub.genie.client-secret:}") String clientSecret,
			@Value("${hub.genie.request-timeout-ms:60000}") long requestTimeoutMs
	) {
		this.restClient = restClients.create(restClientBuilder, authUrl, requestTimeoutMs);
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@SuppressWarnings("unchecked")
	public String accessToken() {
		String credentials = Base64.getEncoder()
				.encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
		Map<String, Object> response = restClient.post()
				.uri("/oidc/v1/token")
				.header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body("grant_type=client_credentials&scope=all-apis")
				.retrieve()
				.body(Map.class);

		Object token = response == null ? null : response.get("access_token");
		if (token == null || token.toString().isBlank()) {
			throw new IllegalStateException("GENIE auth did not return access_token.");
		}
		return token.toString();
	}
}
