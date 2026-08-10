package com.hcb.sc.load.shortener.model;

import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;

/** Domain entity representing a shortened URL mapping. */
public final class ShortUrl {
    private final String code;
    private final String longUrl;
    private final Instant createdAt;
    private final Instant expiresAt; // nullable
    // LongAdder scales far better than a synchronized counter under high
    // concurrent click volume (e.g., a viral link), since it stripes updates
    // across internal cells instead of serializing all threads on one lock.
    private final LongAdder clickCount = new LongAdder();

    public ShortUrl(String code, String longUrl, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getCode() { return code; }
    public String getLongUrl() { return longUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getClickCount() { return clickCount.sum(); }

    public void incrementClicks() { clickCount.increment(); }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
