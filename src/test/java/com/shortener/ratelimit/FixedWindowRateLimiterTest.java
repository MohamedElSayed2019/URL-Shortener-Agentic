package com.hcb.sc.load.shortener.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowRateLimiterTest {

    @Test
    void allowsRequestsUpToLimit() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 60_000);
        assertTrue(limiter.allow("k"));
        assertTrue(limiter.allow("k"));
        assertTrue(limiter.allow("k"));
        assertFalse(limiter.allow("k"));
    }

    @Test
    void tracksDifferentKeysIndependently() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, 60_000);
        assertTrue(limiter.allow("a"));
        assertTrue(limiter.allow("b"));
        assertFalse(limiter.allow("a"));
        assertFalse(limiter.allow("b"));
    }

    @Test
    void resetsAfterWindowExpires() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, 50);
        assertTrue(limiter.allow("k"));
        assertFalse(limiter.allow("k"));
        Thread.sleep(80);
        assertTrue(limiter.allow("k"));
    }
}
