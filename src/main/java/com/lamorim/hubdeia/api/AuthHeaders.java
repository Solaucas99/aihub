package com.lamorim.hubdeia.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthHeaders {

	public String requireUserId(String authorization) {
		if (!StringUtils.hasText(authorization)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required.");
		}

		return authorization.trim();
	}
}
