package com.hcb.sc.load.shortener.service;

/** Thrown when a requested short code does not exist or has expired. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
