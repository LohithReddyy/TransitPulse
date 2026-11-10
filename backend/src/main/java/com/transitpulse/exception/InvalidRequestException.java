package com.transitpulse.exception;

/** Thrown when a request is syntactically valid but semantically wrong. HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
