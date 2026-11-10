package com.transitpulse.exception;

/**
 * Thrown when an analytics payload is requested but the Python engine has
 * not produced it yet. Mapped to HTTP 503 with instructions, rather than a
 * misleading empty result that looks like "no delays found".
 */
public class AnalyticsUnavailableException extends RuntimeException {

    public AnalyticsUnavailableException(String message) {
        super(message);
    }
}
