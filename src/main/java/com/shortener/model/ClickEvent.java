package com.hcb.sc.load.shortener.model;

import java.time.Instant;

/** Analytics event recorded on each redirect. */
public final class ClickEvent {
    private final String code;
    private final Instant timestamp;
    private final String referrer;
    private final String userAgent;

    public ClickEvent(String code, Instant timestamp, String referrer, String userAgent) {
        this.code = code;
        this.timestamp = timestamp;
        this.referrer = referrer == null ? "unknown" : referrer;
        this.userAgent = userAgent == null ? "unknown" : userAgent;
    }

    public String getCode() { return code; }
    public Instant getTimestamp() { return timestamp; }
    public String getReferrer() { return referrer; }
    public String getUserAgent() { return userAgent; }
}
