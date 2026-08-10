# Agentic Software Engineering System — URL Shortener

A working prototype demonstrating an **agentic execution model** that takes a
requirement all the way through the SDLC — requirements → design →
implementation → testing → documentation → release readiness — using a
custom, stateful **orchestration engine**, and produces a real, runnable
**URL Shortener service** as the engineering output.

This module lives at `src/main/java/com` and is a **fully independent Maven
project** (own `pom.xml`) so it can be built and run in isolation from the
rest of the repository. Note: source files declare packages as
`com.*` — the actual folders are `com/agentic`, `com/orchestration`, and `com/shortener`
(see [File structure](#2-file-structure) below).

## 1. Why this design

The assignment's core differentiator is **workflow orchestration**, not just
"an agent that writes code." So this prototype is split into two cooperating
halves:

1. **`com.orchestration`** — a generic, reusable agentic
   orchestration engine (dependency graph, gates, retries, rollback,
   approvals, guardrails, metrics, audit log). This is domain-agnostic.
2. **`com.shortener`** — the actual production-quality URL
   shortener (model, repository, service, rate limiter, HTTP API). This is
   plain, framework-light Java with no dependency on the orchestrator.
3. **`com.agentic`** — the glue: "agents" (Requirements,
   Decomposition) and a `PipelineBuilder` that wires real
   `UrlShortenerService` calls into orchestrator `TaskNode`s, so the
   orchestration run **exercises real code**, not a simulation.

This separation means the orchestration engine is provably generic (you could
point it at a different codebase/service tomorrow), while the shortener
itself is a clean, testable, standalone service you could ship on its own.

## 2. File structure

```
url-shortener-agentic/
├── pom.xml                          # Standalone Maven project (JDK 17, Gson, JUnit 5)
├── README.md                        # This file
├── .github/                         # CI workflow(s) for this module
├── src/
│   ├── main/java/com/
│   │   ├── Main.java                 # Entry point: `scenarios` (default) | `server [port]`
│   │   ├── agentic/                  # com.agentic — glue layer
│   │   │   ├── RequirementsAgent.java        # Parses/normalizes requirements, surfaces ambiguities
│   │   │   ├── RequirementSpec.java          # Value object: normalized requirement + assumptions
│   │   │   ├── DecompositionAgent.java       # Breaks a requirement into DecomposedTasks
│   │   │   ├── DecomposedTask.java           # Value object: a single decomposed unit of work
│   │   │   ├── PipelineBuilder.java          # Wires real UrlShortenerService calls into TaskNodes
│   │   │   ├── BrownfieldEnhancementTasks.java # Impact analysis + tasks for the brownfield scenario
│   │   │   └── ScenarioRunner.java           # Drives the 3 required scenarios end-to-end
│   │   ├── orchestration/            # com.orchestration — generic agentic engine
│   │   │   ├── Orchestrator.java             # Core engine: rounds, parallelism, retries, rollback
│   │   │   ├── TaskNode.java                 # Task definition: deps, gates, retries, approval flag
│   │   │   ├── TaskResult.java                # Outcome of a single task attempt
│   │   │   ├── TaskStatus.java                # PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED/ROLLED_BACK
│   │   │   ├── Stage.java                     # SDLC stage enum (REQUIREMENTS, DESIGN, IMPL, ...)
│   │   │   ├── ExecutionContext.java          # Shared artifact store + safe-stop signaling
│   │   │   ├── PolicyGuardrail.java           # Interface for entry-gate policy checks
│   │   │   ├── DefaultGuardrails.java         # Built-in guardrails (upstream artifact, release gate, ...)
│   │   │   ├── ApprovalGate.java               # Interface for human approval checkpoints
│   │   │   ├── ConsoleApprovalGate.java        # Console-based/auto-approve implementation
│   │   │   ├── AuditLog.java                   # Append-only audit trail of decisions
│   │   │   ├── DecisionLogEntry.java           # Single audit log record
│   │   │   └── MetricsCollector.java           # Success rate, retries, rollbacks, MTTR, latency
│   │   └── shortener/                # com.shortener — the URL shortener product
│   │       ├── api/
│   │       │   └── HttpApiServer.java        # JDK HttpServer-based REST API (shorten/redirect/analytics)
│   │       ├── model/
│   │       │   ├── ShortUrl.java              # Short URL entity (code, longUrl, createdAt, expiry, clicks)
│   │       │   └── ClickEvent.java             # Click analytics record (code, timestamp, referrer, UA)
│   │       ├── ratelimit/
│   │       │   └── FixedWindowRateLimiter.java # Per-client fixed-window rate limiter
│   │       ├── repository/
│   │       │   ├── UrlRepository.java          # Storage interface
│   │       │   └── InMemoryUrlRepository.java  # Default in-memory implementation
│   │       └── service/
│   │           ├── UrlShortenerService.java    # Core business logic (validation, create, resolve, analytics)
│   │           ├── Base62Encoder.java          # Sequence ID <-> short code encoding
│   │           ├── NotFoundException.java
│   │           ├── UrlValidationException.java
│   │           └── RateLimitExceededException.java
│   └── test/java/com/
│       ├── agentic/
│       │   └── PipelineIntegrationTest.java   # End-to-end greenfield/brownfield pipeline assertions
│       ├── orchestration/
│       │   └── OrchestratorTest.java          # Parallelism, retries, rollback, guardrails, approvals
│       └── shortener/
│           ├── ratelimit/FixedWindowRateLimiterTest.java
│           └── service/
│               ├── Base62EncoderTest.java
│               └── UrlShortenerServiceTest.java
└── target/                           # Build output (jar, jar-with-dependencies, classes)
```

> Note: `.java` files declare packages as `com.<subpackage>`
> (e.g., `package com.agentic;`), but there is **no**
> `hcb/sc/load` directory on disk — the actual on-disk paths are
> `src/main/java/com/agentic`, `src/main/java/com/orchestration`, and
> `src/main/java/com/shortener`, directly under `com/`. This is a
> pre-existing mismatch between the package declarations and the folder
> layout, not a documentation error.

## 3. Architecture overview

```
┌───────────────────────────────────────────────────────────────────┐
│                         ScenarioRunner / Main                     │
│  (drives 3 required scenarios: greenfield, brownfield, ambiguous)  │
└───────────────────────────────┬───────────────────────────────────┘
                                 │ builds & runs
                                 ▼
┌───────────────────────────────────────────────────────────────────┐
│                     Orchestrator (agentic engine)                 │
│                                                                     │
│  ExecutionContext (shared artifacts, decision lineage)             │
│  AuditLog (append-only, audit-grade trace of every decision)       │
│  MetricsCollector (success rate, retries, rollbacks, MTTR, latency)│
│                                                                     │
│  TaskNode graph (DAG):                                            │
│    REQUIREMENTS ─┬─► DESIGN(api) ────┐                             │
│                  └─► DESIGN(model) ─┬┴─► IMPL(encoder) ┐           │
│                                     └───► IMPL(repo)   ├─►IMPL(svc)│
│                                                          │          │
│    IMPL(svc) + DESIGN(api) ──► IMPL(api) ──► TEST(unit)             │
│                              └──────────────► TEST(integration)     │
│    IMPL(api) ──► DOC(generate)                                      │
│    TEST(unit)+TEST(integration)+DOC ──► RELEASE_READINESS (gated)  │
│                                                                     │
│  Each task passes an entry gate of PolicyGuardrails, optional       │
│  ApprovalGate (human checkpoint), bounded retries w/ backoff, and   │
│  rollback-on-exhaustion. Independent tasks execute in parallel via  │
│  a thread pool; dependents wait (synchronization) — non-linear.     │
│  replan() lets new tasks be injected mid-run (dynamic re-planning).│
└───────────────────────────────┬───────────────────────────────────┘
                                 │ tasks call into
                                 ▼
┌───────────────────────────────────────────────────────────────────┐
│                  UrlShortenerService (business logic)              │
│  Base62Encoder │ InMemoryUrlRepository │ FixedWindowRateLimiter    │
└───────────────────────────────┬───────────────────────────────────┘
                                 │ exposed by
                                 ▼
┌───────────────────────────────────────────────────────────────────┐
│      HttpApiServer (JDK com.sun.net.httpserver, zero web framework)│
│  POST /api/shorten · GET /r/{code} · GET /api/urls[/{code}[/analytics]]
└───────────────────────────────────────────────────────────────────┘
```

### Orchestration model — how it satisfies the "critical differentiator"

| Requirement | Implementation |
|---|---|
| Explicit dependency graph with entry/exit gates | `TaskNode.dependsOn(...)`; entry gate = `PolicyGuardrail` evaluation before execution |
| Sequential & parallel paths with synchronization | `Orchestrator.run()` computes the *eligible set* of tasks each round (all deps satisfied) and executes them concurrently on a thread pool, then joins before recomputing — parallel within a round, sequential across rounds |
| Cross-stage context & decision lineage | `ExecutionContext` (shared artifact store) + `AuditLog` (append-only `DecisionLogEntry` records with actor/action/rationale/timestamp) |
| Human approval checkpoints | `TaskNode.requiresHumanApproval(true)` + `ApprovalGate` interface (`ConsoleApprovalGate`, auto-approve toggle via `-Dagentic.autoApprove=true/false`) |
| Bounded retries, fallback, rollback, safe-stop | `maxRetries`/`retryBackoffMs` on each task; `rollback(...)` action invoked on exhaustion; `ExecutionContext.requestSafeStop()` halts further scheduling |
| Policy guardrails (security/compliance/change control) | `PolicyGuardrail` interface + `DefaultGuardrails` (require-upstream-artifact, block-destructive-without-approval, release-requires-green-tests) |
| Audit-grade observability & traceability | `AuditLog` records every state transition; printed per scenario |
| Reliability metrics | `MetricsCollector`: success rate, retry/rollback frequency, MTTR, end-to-end latency |
| Dynamic re-planning | `Orchestrator.replan(List<TaskNode>)` merges new/updated tasks into the still-running graph |
| Controlled autonomy | Agents (Requirements/Decomposition/Implementation/Test/Doc "agents", represented as task actions) execute autonomously *within* guardrails; humans gate high-impact release actions |

## 4. The three required scenarios

Run via `ScenarioRunner` (invoked by `Main scenarios`, the default mode):

1. **Greenfield** — brand-new system built from a fairly clear requirement.
   Shows full decomposition + parallel design/implementation + testing +
   docs + release gate (auto-approved, non-high-impact).
2. **Brownfield** — reuses the already-built service and adds an **additive**
   enhancement (bulk analytics summary). `BrownfieldEnhancementTasks` performs
   impact analysis (which modules are touched: service, API, docs — *not*
   data model or storage) before implementing, demonstrating codebase
   reasoning. Marked `highImpactRelease=true` so the release gate requires
   human approval (auto-approved by default for unattended demo runs).
3. **Ambiguous** — a vague one-line requirement ("make it good"). The
   `RequirementsAgent` surfaces concrete ambiguities (no TTL policy? custom
   alias needed? rate limiting? analytics granularity?) and logs the
   assumptions it made to proceed, satisfying "identify ambiguity, normalize
   into a clear engineering problem."

Each scenario prints: normalized problem statement, ambiguities, assumptions,
every task's final status/attempts, reliability metrics, overall
success/failure, and the last 5 audit log entries.

## 5. Setup instructions

**Prerequisites:** JDK 17+, Maven 3.8+ (no other dependencies — Gson is
fetched from Maven Central for JSON in the HTTP API; JUnit 5 for tests).

```powershell
cd url-shortener-agentic
mvn clean package
```

This produces `target/url-shortener-agentic.jar` (with dependencies bundled
via `maven-assembly-plugin`) and `target/url-shortener-agentic-jar-with-dependencies.jar`.

### Run the 3 SDLC scenarios (default)

```powershell
java -jar target/url-shortener-agentic.jar scenarios
```

### Run the orchestrated build, then start the live HTTP API

```powershell
java -jar target/url-shortener-agentic.jar server 8080
```

Then, in another terminal:

```powershell
# Create a short URL
curl -X POST http://localhost:8080/api/shorten -H "Content-Type: application/json" `
  -d '{"longUrl":"https://example.com/some/long/path","ttlSeconds":3600}'

# Follow the redirect (use the returned "code")
curl -i http://localhost:8080/r/<code>

# Metadata & analytics
curl http://localhost:8080/api/urls/<code>
curl http://localhost:8080/api/urls/<code>/analytics
```

### Require interactive human approval instead of auto-approve

```powershell
java -Dagentic.autoApprove=false -jar target/url-shortener-agentic.jar scenarios
```

## 6. Testing approach

- **Unit tests** (`src/test/java/.../shortener/...`): `Base62EncoderTest`,
  `UrlShortenerServiceTest` (validation, custom alias, expiry, analytics,
  rate limiting), `FixedWindowRateLimiterTest`.
- **Orchestration engine tests** (`OrchestratorTest`): parallel execution,
  dependency-skip-on-failure, bounded retry + rollback, guardrail blocking,
  human-approval rejection, safe-stop, dynamic re-planning.
- **Pipeline integration tests** (`PipelineIntegrationTest`): runs the actual
  greenfield/brownfield pipelines end-to-end and asserts they succeed with
  zero test failures recorded as an artifact; verifies ambiguity detection
  for the vague requirement; verifies the release gate is correctly marked
  high-impact.

Run all tests:

```powershell
mvn test
```

Tests use auto-approve mode so the full suite runs unattended/CI-friendly.

## 7. Performance enhancements

A review of the hot paths (HTTP request handling, redirect/click recording,
rate limiting, code encoding) identified and fixed several concurrency
bottlenecks:

| Area | Before | After | Why it matters |
|---|---|---|---|
| `HttpApiServer` | `server.setExecutor(null)` → JDK's default **single-threaded, synchronous** request handler | Bounded fixed thread pool sized to `availableProcessors() * 4` | Previously every request (redirects, shorten, analytics) was serialized on one thread; now concurrent requests are handled in parallel, and the pool is shut down cleanly in `stop()` |
| `ShortUrl.incrementClicks()` | `synchronized` method + plain `long` counter | `LongAdder` | Redirects are the hottest path for a popular link; `synchronized` serializes all concurrent clicks on one lock, while `LongAdder` stripes updates across internal cells for near-linear scalability |
| `InMemoryUrlRepository` click storage | `CopyOnWriteArrayList` (copies the **entire array** on every click) | `ConcurrentLinkedQueue` (O(1) lock-free append) | CoW lists are read-optimized/write-hostile; click recording is write-heavy, so this eliminates an O(n) copy per click |
| `FixedWindowRateLimiter.allow()` | `synchronized (window)` block per request | Atomic CAS on `AtomicLong` window-start + `AtomicInteger` counter | Removes full mutual exclusion per request; only the (rare) window-reset moment needs coordination, via CAS instead of a lock |
| `Base62Encoder.decode()` | `ALPHABET.indexOf(c)` — O(62) linear scan per character, plus `toCharArray()` allocation | Static `int[128]` reverse-lookup table — O(1) per character, no array allocation | Decoding happens on every redirect resolution in some designs; this removes an unnecessary linear scan and allocation from a hot path |

All changes preserve existing behavior/contracts — verified by re-running the
full unit/integration/orchestration test suite (`mvn test`) after each change,
with zero regressions.

## 8. Risks, trade-offs, and validation guardrails

| Risk | Mitigation in this prototype |
|---|---|
| Predictable/guessable short codes enable enumeration of all links | Sequence-based Base62 is simple and collision-free for the demo; production should add a permutation/HMAC step or move to random codes with collision retry |
| In-memory storage is not durable/multi-instance | `UrlRepository` is an interface; swap `InMemoryUrlRepository` for a JDBC/Redis-backed implementation without touching `UrlShortenerService` |
| Rate limiter is per-JVM, fixed-window has boundary burst issue | Documented trade-off; production should use a distributed token-bucket (e.g., Redis) |
| No authentication on the API | Explicitly logged as an assumption by the Requirements agent; out of scope for the prototype |
| Orchestrator's parallel thread pool could mask flaky/order-dependent bugs | Deterministic guardrail evaluation + audit log per task attempt make issues traceable regardless of execution order |
| Human approval could be silently auto-approved in production | `agentic.autoApprove` defaults to `true` only for demo runnability; must default to `false` (and be wired to a real approval system) before production use |
| Retries could mask a persistently broken dependency | Bounded (`maxRetries`), then rollback + dependents SKIPPED rather than silently succeeding |

## 9. Assumptions and limitations

- Requirement interpretation uses transparent heuristic rules (keyword
  detection), not an actual LLM call — this keeps the demo deterministic and
  dependency-free, but a production version would call an LLM for the
  Requirements/Decomposition agents.
- The HTTP layer uses the JDK's built-in `HttpServer` rather than a full
  framework (Spring/Micronaut) to keep this module dependency-light and
  trivially runnable via `java -jar`; the layering (`service` has zero HTTP
  imports) makes swapping in Spring Boot straightforward later.
- Single-node, in-memory storage — acceptable for a prototype/demo, not for
  production scale or HA.
- The orchestrator is intentionally generic; the "codebase reasoning" for
  brownfield is expressed as an explicit impact-analysis task rather than an
  automated static-analysis pass over source files, since the target of the
  enhancement is this same demo codebase.

## 10. Final engineering summary

- **Plan/rationale**: build a small, generic orchestration engine first
  (testable in isolation), then a clean service, then glue them with
  real (not simulated) task actions so the demo's outputs are genuine.
- **Artifacts produced**: orchestration engine, URL shortener service +
  HTTP API, unit/integration/orchestration test suites, this README.
- **Validation performed**: `mvn test` (all green), manual `scenarios` run
  showing all 3 required scenarios completing with full audit trails and
  metrics, manual `server` run smoke-tested via curl.
- **Controlled autonomy**: agents execute the graph autonomously under
  guardrails; the release-readiness gate is the human checkpoint for
  high-impact (brownfield) changes.
