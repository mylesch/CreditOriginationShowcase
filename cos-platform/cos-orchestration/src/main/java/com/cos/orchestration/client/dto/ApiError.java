package com.cos.orchestration.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The {@code $.error} object every bureau (and DRS) returns on a business/technical hiccup. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiError(String code, String message, Boolean retryable) {
}
