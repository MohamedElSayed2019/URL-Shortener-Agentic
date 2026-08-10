package com.hcb.sc.load.shortener.service;

/** Thrown when a supplied long URL fails validation. */
public class UrlValidationException extends RuntimeException {
    public UrlValidationException(String message) {
        super(message);
    }
}
