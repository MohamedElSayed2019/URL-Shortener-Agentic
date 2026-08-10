package com.hcb.sc.load.shortener.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hcb.sc.load.shortener.model.ShortUrl;
import com.hcb.sc.load.shortener.service.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal REST API for the URL shortener, built on the JDK's built-in
 * HttpServer to keep the prototype dependency-light and easy to run via
 * {@code java -jar}.
 *
 * <pre>
 * POST /api/shorten       { "longUrl": "...", "customAlias": "...", "ttlSeconds": 3600 }
 * GET  /r/{code}          -> 302 redirect to long URL, records analytics
 * GET  /api/urls/{code}   -> metadata JSON
 * GET  /api/urls/{code}/analytics -> analytics JSON
 * GET  /api/urls          -> list all (admin/demo use)
 * GET  /health            -> liveness probe
 * </pre>
 */
public final class HttpApiServer {

    private final Gson gson = new Gson();
    private final UrlShortenerService service;
    private final HttpServer server;
    private final ExecutorService requestExecutor;

    public HttpApiServer(UrlShortenerService service, int port) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/shorten", this::handleShorten);
        server.createContext("/api/urls", this::handleUrls);
        server.createContext("/r/", this::handleRedirect);
        // The JDK HttpServer defaults to a single-threaded synchronous executor
        // when setExecutor(null) is used, which serializes ALL requests on one
        // thread regardless of load. A bounded cached thread pool lets the
        // server handle many concurrent redirects/API calls in parallel while
        // still bounding resource usage under extreme load.
        this.requestExecutor = Executors.newFixedThreadPool(
                Math.max(8, Runtime.getRuntime().availableProcessors() * 4));
        server.setExecutor(requestExecutor);
    }

    public void start() {
        server.start();
        System.out.println("HTTP API listening on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        requestExecutor.shutdown();
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        writeJson(ex, 200, Map.of("status", "UP"));
    }

    private void handleShorten(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(body.isBlank() ? "{}" : body, JsonObject.class);
            String longUrl = getAsStringOrNull(json, "longUrl");
            String customAlias = getAsStringOrNull(json, "customAlias");
            Long ttlSeconds = json.has("ttlSeconds") && !json.get("ttlSeconds").isJsonNull()
                    ? json.get("ttlSeconds").getAsLong() : null;
            Duration ttl = ttlSeconds == null ? null : Duration.ofSeconds(ttlSeconds);
            String clientKey = ex.getRemoteAddress() == null ? "unknown" : ex.getRemoteAddress().toString();

            ShortUrl created = service.createShortUrl(longUrl, customAlias, ttl, clientKey);
            writeJson(ex, 201, toDto(created));
        } catch (UrlValidationException e) {
            writeJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (RateLimitExceededException e) {
            writeJson(ex, 429, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    private static final Pattern URLS_CODE = Pattern.compile("^/api/urls/([^/]+)(/analytics)?$");

    private void handleUrls(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/api/urls")) {
                writeJson(ex, 200, service.listAll().stream().map(this::toDto).toList());
                return;
            }
            Matcher m = URLS_CODE.matcher(path);
            if (m.matches()) {
                String code = m.group(1);
                boolean analytics = m.group(2) != null;
                if (analytics) {
                    writeJson(ex, 200, service.getAnalytics(code));
                } else {
                    writeJson(ex, 200, toDto(service.getMetadata(code)));
                }
                return;
            }
            writeJson(ex, 404, Map.of("error", "Not found"));
        } catch (NotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    private void handleRedirect(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String code = path.startsWith("/r/") ? path.substring(3) : "";
        try {
            String referrer = firstHeader(ex, "Referer");
            String userAgent = firstHeader(ex, "User-Agent");
            ShortUrl shortUrl = service.resolveAndRecordClick(code, referrer, userAgent);
            ex.getResponseHeaders().add("Location", shortUrl.getLongUrl());
            ex.sendResponseHeaders(302, -1);
        } catch (NotFoundException e) {
            writeJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(ex, 500, Map.of("error", "Internal error: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private String firstHeader(HttpExchange ex, String name) {
        var values = ex.getRequestHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String getAsStringOrNull(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private Map<String, Object> toDto(ShortUrl s) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("code", s.getCode());
        map.put("longUrl", s.getLongUrl());
        map.put("createdAt", s.getCreatedAt().toString());
        map.put("expiresAt", s.getExpiresAt() == null ? null : s.getExpiresAt().toString());
        map.put("clickCount", s.getClickCount());
        return map;
    }

    private void writeJson(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] bytes = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
