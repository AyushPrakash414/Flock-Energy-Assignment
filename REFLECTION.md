# Project Reflection — Urja Meter Ops API

> **Self-reflection on the design, reverse-engineering discovery, engineering trade-offs, and critical review of the Flock Energy Meter API assignment.**

---

## 1. What assumptions did you make?

- **Upstream Portal as System of Record**: I assumed the legacy Urja Meter Ops portal (`https://urja-ops.flockenergy.tech`) is the single authoritative source of truth. The Spring Boot service was designed strictly as an abstraction, cryptographic mediation, and caching layer without maintaining an independent persistent write database.
- **Data Volatility & Invalidation Strategy**: I assumed smart meter hardware records (the 403 meters across 7 hierarchy levels) and distribution transformer network mappings are relatively static in operational environments. This justified an in-memory Caffeine cache with a 15-minute write-expiration TTL, dramatically reducing load on the fragile legacy portal. In contrast, I assumed energy consumption telemetry (`/portal/meters/{meterId}/energy`) represents live operational telemetry that must always be fetched on-demand without caching.
- **Bulk Export Completeness**: I assumed the portal's `/portal/export` endpoint provides a complete and consistent snapshot of the entire 403-meter fleet with full hierarchy nodes and geographic coordinates in a single payload, removing the need to construct individual meter states from fragmented routes.

---

## 2. Which part was the most difficult, and how did you get unstuck?

- **The Challenge**: The most challenging obstacle was reverse-engineering the portal's proprietary authentication and cryptographic request verification mechanisms without any API documentation or schemas. Initial attempts to fetch bulk meter data via `/portal/export` failed with HTTP 401/403 errors despite presenting a valid session cookie. Similarly, standard HTTP `POST /login` requests failed due to undocumented cross-site origin checks.
- **How I Got Unstuck**: I used Chrome DevTools to analyze the portal's network traffic and inspect the minified client-side JavaScript bundles. By stepping through the frontend code, I uncovered the `/portal/keys` endpoint that dynamically serves the symmetric `signingSecret`. I traced the client-side signing routine to deduce the exact 4-line HMAC-SHA256 payload format (`method\npath\nqueryString\ntimestamp`) and the required `x-timestamp` and `x-signature` headers. Similarly, I noticed the login endpoint checks cross-site origin headers, requiring explicit `Origin: https://urja-ops.flockenergy.tech` and `Referer: https://urja-ops.flockenergy.tech/login` headers. Once replicated in Java using `HmacSignerService` and `UrjaAuthService`, upstream calls succeeded reliably.

---

## 3. If you had another day, what would you improve?

- **WireMock Test Suite**: I would build a comprehensive WireMock integration test suite to simulate upstream portal failure modes, such as upstream 500/502/503 errors, unexpected HTML response bodies, connection drops, and session token expirations under heavy concurrency.
- **Proactive Background Cache Pre-Warming**: Rather than relying purely on lazy evaluation (where the first request after TTL expiry incurs the upstream fetch latency), I would introduce a background scheduled executor (e.g., executing every 14 minutes via Spring's `@Scheduled` annotation) to keep the master dataset warm in JVM memory at all times.
- **Observability & Metrics**: I would integrate Spring Boot Actuator with Micrometer/Prometheus to expose metrics for cache hit/miss ratios, upstream network latency, and auth token renewal cycles, accompanied by a custom health indicator checking upstream portal reachability.

---

## 4. What mistake did you make while solving this?

- **Initial $O(N)$ Request Bottleneck**: Early in the development process, I initially attempted to construct complete meter records by searching the fleet via `/portal/meters/search` and then issuing individual detail and coordinate requests (`/portal/meters/{id}/geo`) for each meter. This introduced an $O(N)$ HTTP request cascade (400+ sequential network calls), which took over a minute to execute and risked triggering upstream rate limits or IP bans. I stepped back, inspected the portal's export functionality in browser network traces, and discovered the HMAC-signed `/portal/export` endpoint. Pivoting to the HMAC export allowed ingesting all 403 meters with complete 7-level hierarchy and geo-coordinates in a single 300 ms call.

---

## 5. If reviewing your own submission, what would you criticise?

- **Single-Node In-Memory Cache**: The Caffeine cache is isolated within a single JVM instance's heap. If this service is scaled horizontally behind a round-robin load balancer, each instance will independently query the upstream portal and maintain separate cache states unless migrated to a distributed cache like Redis.
- **Lazy Cache Warmup Cold-Start Latency**: Because the cache is lazy, whichever user or automated client issues the first request after startup or TTL expiry suffers the full latency of the upstream export call (~300–500 ms).
- **Un-memoized Live Consumption Calls**: While consumption telemetry must reflect fresh readings, rapid successive queries for the same meter (e.g., rapid page reloads or automated polling) directly hit the upstream portal on every call without short-term memoization (e.g., a 15–30 second debounce cache).

