package org.example.exception;

import lombok.Getter;

/**
 * Custom exception for API-related errors.
 */
@Getter
public class ApiException extends RuntimeException {
    
    private final int statusCode;
    private final String endpoint;
    
    /**
     * Constructs a new API exception with the specified detail message and status code.
     *
     * @param message the detail message
     * @param statusCode the HTTP status code
     * @param endpoint the API endpoint that caused the exception
     */
    public ApiException(String message, int statusCode, String endpoint) {
        super(message);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }
    
    /**
     * Constructs a new API exception with the specified detail message, status code, and cause.
     *
     * @param message the detail message
     * @param statusCode the HTTP status code
     * @param endpoint the API endpoint that caused the exception
     * @param cause the cause of the exception
     */
    public ApiException(String message, int statusCode, String endpoint, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }
} 