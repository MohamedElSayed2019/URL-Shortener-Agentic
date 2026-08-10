package com.hcb.sc.load.shortener.service;

/** Thrown when a client exceeds the allowed request rate. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
