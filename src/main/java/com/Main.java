package com.hcb.sc.load;

import com.hcb.sc.load.agentic.PipelineBuilder;
import com.hcb.sc.load.agentic.ScenarioRunner;
import com.hcb.sc.load.orchestration.Orchestrator;
import com.hcb.sc.load.shortener.api.HttpApiServer;
import com.hcb.sc.load.shortener.ratelimit.FixedWindowRateLimiter;
import com.hcb.sc.load.shortener.repository.InMemoryUrlRepository;
import com.hcb.sc.load.shortener.repository.UrlRepository;
import com.hcb.sc.load.shortener.service.UrlShortenerService;

/**
 * Executable entry point for the Agentic URL Shortener prototype.
 *
 * <p>Usage:
 * <pre>
 *   java -jar url-shortener-agentic.jar scenarios   # run the 3 required SDLC scenarios (default)
 *   java -jar url-shortener-agentic.jar server 8080  # run the orchestrated build then start the live HTTP API
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "scenarios";

        switch (mode) {
            case "server":
                runServerMode(args);
                break;
            case "scenarios":
                new ScenarioRunner().runAll();
                break;
            default:
                System.out.println("Unknown mode '" + mode + "'. Valid modes: scenarios | server [port]");
                System.exit(1);
        }
    }

    private static void runServerMode(String[] args) throws Exception {
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        System.out.println("Running orchestrated greenfield build before starting the live server...\n");
        Orchestrator orchestrator = PipelineBuilder.build("GREENFIELD-SERVER-BOOT",
                "Build a URL shortener service with core APIs, analytics, and reliability features.", false);
        boolean success = orchestrator.run();
        System.out.println(orchestrator.getContext().metrics().summary());

        if (!success) {
            System.out.println("Orchestrated build reported failures; starting server with a fresh service instance regardless (demo mode).");
        }

        UrlRepository repository = new InMemoryUrlRepository();
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(100, 60_000);
        UrlShortenerService service = new UrlShortenerService(repository, limiter);

        HttpApiServer server = new HttpApiServer(service, port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("Press Ctrl+C to stop the server.");
        Thread.currentThread().join();
    }
}
