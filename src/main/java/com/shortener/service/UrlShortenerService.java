package com.hcb.sc.load.shortener.service;

import com.hcb.sc.load.shortener.model.ClickEvent;
import com.hcb.sc.load.shortener.model.ShortUrl;
import com.hcb.sc.load.shortener.ratelimit.FixedWindowRateLimiter;
import com.hcb.sc.load.shortener.repository.InMemoryUrlRepository;
import com.hcb.sc.load.shortener.repository.UrlRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core business logic for the URL shortener: validation, code generation,
 * custom aliases, expiry, redirect resolution, click analytics, and rate
 * limiting. Framework-agnostic so it can be reused by the HTTP layer or
 * tests directly.
 */
public final class UrlShortenerService {

    private static final Pattern CUSTOM_ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,20}$");

    private final UrlRepository repository;
    private final InMemoryUrlRepository inMemoryRepositoryForIdGen; // for Base62 sequence access
    private final FixedWindowRateLimiter rateLimiter;

    public UrlShortenerService(UrlRepository repository, FixedWindowRateLimiter rateLimiter) {
        this.repository = repository;
        this.inMemoryRepositoryForIdGen = repository instanceof InMemoryUrlRepository
                ? (InMemoryUrlRepository) repository : new InMemoryUrlRepository();
        this.rateLimiter = rateLimiter;
    }

    /**
     * Creates a short URL for the given long URL.
     *
     * @param longUrl     the destination URL (must be http/https and well-formed)
     * @param customAlias optional custom alias (3-20 alphanumerics/dash/underscore); null for auto-generated
     * @param ttl         optional time-to-live; null means the link never expires
     * @param clientKey   identifier used for rate limiting (e.g., caller IP or API key)
     */
    public ShortUrl createShortUrl(String longUrl, String customAlias, Duration ttl, String clientKey) {
        if (!rateLimiter.allow(clientKey)) {
            throw new RateLimitExceededException("Rate limit exceeded for client: " + clientKey);
        }
        validateLongUrl(longUrl);

        String code;
        if (customAlias != null && !customAlias.isBlank()) {
            if (!CUSTOM_ALIAS_PATTERN.matcher(customAlias).matches()) {
                throw new UrlValidationException("Custom alias must be 3-20 chars of letters, digits, '_' or '-'.");
            }
            if (repository.existsByCode(customAlias)) {
                throw new UrlValidationException("Custom alias already in use: " + customAlias);
            }
            code = customAlias;
        } else {
            code = generateUniqueCode();
        }

        Instant now = Instant.now();
        Instant expiresAt = ttl == null ? null : now.plus(ttl);
        ShortUrl shortUrl = new ShortUrl(code, longUrl, now, expiresAt);
        repository.save(shortUrl);
        return shortUrl;
    }

    /** Resolves a code to its long URL, recording a click event. Throws if missing/expired. */
    public ShortUrl resolveAndRecordClick(String code, String referrer, String userAgent) {
        ShortUrl shortUrl = repository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Short code not found: " + code));
        if (shortUrl.isExpired()) {
            throw new NotFoundException("Short code expired: " + code);
        }
        shortUrl.incrementClicks();
        repository.recordClick(new ClickEvent(code, Instant.now(), referrer, userAgent));
        return shortUrl;
    }

    public ShortUrl getMetadata(String code) {
        return repository.findByCode(code).orElseThrow(() -> new NotFoundException("Short code not found: " + code));
    }

    public List<ClickEvent> getClickHistory(String code) {
        return repository.findClicksByCode(code);
    }

    /** Aggregated analytics: total clicks, unique referrers count, last click time. */
    public Map<String, Object> getAnalytics(String code) {
        ShortUrl shortUrl = getMetadata(code);
        List<ClickEvent> clicks = getClickHistory(code);
        long uniqueReferrers = clicks.stream().map(ClickEvent::getReferrer).distinct().count();
        Instant last = clicks.isEmpty() ? null : clicks.get(clicks.size() - 1).getTimestamp();
        Map<String, Long> referrerBreakdown = clicks.stream()
                .collect(Collectors.groupingBy(ClickEvent::getReferrer, Collectors.counting()));
        return Map.of(
                "code", code,
                "longUrl", shortUrl.getLongUrl(),
                "totalClicks", shortUrl.getClickCount(),
                "uniqueReferrers", uniqueReferrers,
                "lastClickAt", last == null ? "never" : last.toString(),
                "referrerBreakdown", referrerBreakdown
        );
    }

    public List<ShortUrl> listAll() {
        return repository.findAll();
    }

    private String generateUniqueCode() {
        long id = inMemoryRepositoryForIdGen.nextId();
        String code = Base62Encoder.encode(id);
        // Extremely unlikely with sequence-based IDs, but guard against
        // collisions if the repository is pre-seeded with clashing custom aliases.
        int guard = 0;
        while (repository.existsByCode(code) && guard++ < 5) {
            id = inMemoryRepositoryForIdGen.nextId();
            code = Base62Encoder.encode(id);
        }
        return code;
    }

    private void validateLongUrl(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new UrlValidationException("longUrl must not be blank");
        }
        if (longUrl.length() > 2048) {
            throw new UrlValidationException("longUrl exceeds maximum length of 2048 characters");
        }
        try {
            URI uri = new URI(longUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new UrlValidationException("longUrl must use http or https scheme");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new UrlValidationException("longUrl must contain a valid host");
            }
        } catch (URISyntaxException e) {
            throw new UrlValidationException("longUrl is not a well-formed URI: " + e.getMessage());
        }
    }
}
