package com.hcb.sc.load.shortener.repository;

import com.hcb.sc.load.shortener.model.ClickEvent;
import com.hcb.sc.load.shortener.model.ShortUrl;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction so the persistence layer can be swapped (in-memory for
 * the prototype, JDBC/NoSQL for production) without touching service logic.
 */
public interface UrlRepository {
    void save(ShortUrl shortUrl);
    Optional<ShortUrl> findByCode(String code);
    boolean existsByCode(String code);
    List<ShortUrl> findAll();
    void recordClick(ClickEvent event);
    List<ClickEvent> findClicksByCode(String code);
    long countAll();
}
