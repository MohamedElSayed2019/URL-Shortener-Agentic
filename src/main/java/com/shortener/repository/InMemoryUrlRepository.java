package com.hcb.sc.load.shortener.repository;

import com.hcb.sc.load.shortener.model.ClickEvent;
import com.hcb.sc.load.shortener.model.ShortUrl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory implementation suitable for the prototype and unit
 * tests. Uses an {@link AtomicLong} sequence to hand out unique IDs for
 * Base62 encoding (guarantees no collisions without random retries).
 */
public final class InMemoryUrlRepository implements UrlRepository {

    private final ConcurrentHashMap<String, ShortUrl> byCode = new ConcurrentHashMap<>();
    // ConcurrentLinkedQueue gives O(1) lock-free appends for click events,
    // unlike CopyOnWriteArrayList which copies the entire backing array on
    // every single write - a significant hot-path cost for popular links
    // receiving many clicks.
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ClickEvent>> clicksByCode = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1000); // start above small values for nicer codes

    public long nextId() {
        return sequence.incrementAndGet();
    }

    @Override
    public void save(ShortUrl shortUrl) {
        byCode.put(shortUrl.getCode(), shortUrl);
    }

    @Override
    public Optional<ShortUrl> findByCode(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    @Override
    public boolean existsByCode(String code) {
        return byCode.containsKey(code);
    }

    @Override
    public List<ShortUrl> findAll() {
        return List.copyOf(byCode.values());
    }

    @Override
    public void recordClick(ClickEvent event) {
        clicksByCode.computeIfAbsent(event.getCode(), k -> new ConcurrentLinkedQueue<>()).add(event);
    }

    @Override
    public List<ClickEvent> findClicksByCode(String code) {
        ConcurrentLinkedQueue<ClickEvent> queue = clicksByCode.get(code);
        return queue == null ? List.of() : List.copyOf(queue);
    }

    @Override
    public long countAll() {
        return byCode.size();
    }
}
