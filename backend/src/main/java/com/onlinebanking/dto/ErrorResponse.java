package com.onlinebanking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standardized JSON error response returned by the API for
 * authentication (401) and authorization (403) failures.
 *
 * <p>Example:
 * <pre>{
 *   "status": 401,
 *   "error": "UNAUTHORIZED",
 *   "message": "Authentication is required.",
 *   "timestamp": "2024-01-15T10:30:00"
 * }</pre>
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final String timestamp;

    /**
     * Convenience factory that sets the timestamp to now.
     */
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, LocalDateTime.now().toString());
    }
}

