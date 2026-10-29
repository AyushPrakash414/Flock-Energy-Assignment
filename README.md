

# Flock Energy — Meter API

A production-grade, documented Spring Boot REST API that provides programmatic access to smart-meter, electrical distribution hierarchy, and time-series consumption data from the legacy **Urja Meter Ops** portal.

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot 3.4.5](https://img.shields.io/badge/Spring_Boot-3.4.5-6DB33F?style=flat&logo=springboot&logoColor=white)
![Caffeine Cache](https://img.shields.io/badge/Cache-Caffeine-orange?style=flat)
![Maven](https://img.shields.io/badge/Build-Maven_3.9+-C71A36?style=flat&logo=apachemaven&logoColor=white)
![OpenAPI 3.0](https://img.shields.io/badge/API_Docs-OpenAPI_3.0-85EA2D?style=flat&logo=swagger&logoColor=black)

---

The legacy Urja Meter Ops portal (`urja-ops.flockenergy.tech`) houses critical operational infrastructure data—smart meters, distribution transformer assignments, and electrical telemetry. However, it lacks a public REST API. Upstream access requires form-based session cookie authentication guarded by Origin checks, multi-page web scraping for transformers, and HMAC-SHA256 request signing for bulk data exports.

This service acts as an abstraction and mediation layer. It handles upstream authentication, cryptographic request signing, data transformation, and in-memory caching—exposing a predictable, paginated, and fully documented REST API.

---

## Demo Walkthrough



https://github.com/user-attachments/assets/ca6e9e7c-aefc-43e4-a76b-b1cc01594ff2


---

## Table of Contents

- [Demo Walkthrough](#demo-walkthrough)
- [Key Features](#key-features)
- [System Architecture](#system-architecture)
- [How Caching Works](#how-caching-works)
- [Technology Stack](#technology-stack)
- [Implemented API Endpoints](#implemented-api-endpoints)
- [Prerequisites and Configuration](#prerequisites-and-configuration)
- [Installation and Running Locally](#installation-and-running-locally)
- [Error Handling and Reliability](#error-handling-and-reliability)
- [Assumptions and Design Decisions](#assumptions-and-design-decisions)
- [What Was Intentionally Left Out](#what-was-intentionally-left-out)
- [Testing](#testing)
- [OpenAPI Documentation](#openapi-documentation)
- [Reflection](#reflection)
- [Future Improvements](#future-improvements)
- [Supporting Documentation](#supporting-documentation)

---

## Key Features

- **Five Working REST Endpoints**: Paginated meter listing, full-text multi-field search, single meter details, real-time consumption telemetry, and paginated distribution transformers.
- **Robust Upstream Authentication**: Automated login against the legacy portal (`POST /login`) with `Origin` and `Referer` headers, session cookie capture (`__Secure-better-auth.session_token`), and thread-safe session caching.
- **HMAC-SHA256 Request Signing**: Implements the portal's client-side signing protocol (`method\npath\nqueryString\ntimestamp`) using signing keys obtained from `/portal/keys` to ingest the complete 403-meter master catalog in a single call.
- **In-Memory Caffeine Caching**: Local caching of the master meter export and transformer records with a configurable 15-minute TTL, eliminating repeated heavy upstream exports.
- **Normalized JSON Responses**: Consistent, type-safe response wrappers with 1-indexed pagination metadata (`data`, `total`, `page`, `pageSize`, `totalPages`) and centralized HTTP error formatting.

---

## System Architecture

![System Architecture](./Assets/SystemArch.png)

### Component Responsibilities

- **Controllers (`MeterController`, `TransformerController`)**: Expose the outward REST surface, validate query and path parameters (e.g., rejecting non-positive page indices or out-of-bounds page sizes), and serialize domain DTOs into HTTP responses.
- **Service Layer (`MeterService`, `TransformerService`)**: Coordinate data retrieval, apply `@Cacheable` directives, execute in-memory filtering (case-insensitive substring matching on meter ID, serial number, make, and DT code), and calculate page slices.
- **Cache (`CacheConfig` with Caffeine)**: Holds the deserialized 403-meter master list and the complete transformer collection in JVM heap with eviction policies.
- **Portal Client Layer (`UrjaPortalClient`, `UrjaAuthService`, `HmacSignerService`)**: Encapsulates all upstream interactions:
  - `UrjaAuthService`: Acquires and holds the session cookie in an `AtomicReference<String>`, protected by a `ReentrantLock` to avoid concurrent redundant logins.
  - `HmacSignerService`: Constructs and signs HMAC-SHA256 payloads with unix epoch timestamps for export verification.
  - `UrjaPortalClient`: Dispatches authenticated requests via Spring 6 `RestClient`, checks status codes, and transparently re-authenticates on upstream 401/403 session expiration.

---

## How Caching Works

### Caffeine Cache Integration

[Caffeine](https://github.com/ben-manes/caffeine) is a high-performance, near-optimal Java caching library providing in-memory key-value storage. Spring Cache's `CaffeineCacheManager` is configured in `CacheConfig.java` to manage two primary cache names:

1. **`meters-export`** (key: `'all'`): Contains the complete list of all 403 meters along with full 7-level electrical hierarchy and geographic coordinates.
2. **`transformers`** (key: `'all'`): Contains the full inventory of distribution transformers compiled across all upstream DT pages.

> [!NOTE]
> Consumption readings (`GET /api/meters/{meterId}/consumption`) are **never cached**. Energy readings reflect real-time operational state and are fetched live from `/portal/meters/{meterId}/energy` on every call.

### Cache Hit vs. Cache Miss Lifecycle

![Cache Hit vs. Cache Miss Lifecycle](./Assets/Cache.png)

- **Configured TTL**: Set to **15 minutes** (`expireAfterWrite(props.getCacheTtlMinutes(), TimeUnit.MINUTES)`), sourced from `urja.portal.cache-ttl-minutes: 15` in `application.yml`.
- **Refresh Mechanism**: **Lazy on-demand evaluation**. Cache entries are not refreshed on a scheduled timer or background daemon. When a key expires or is requested for the first time, the next incoming API call triggers the upstream network fetch.
- **Max Cache Size & Stats**: Configured with a `maximumSize(5)` limit and `recordStats()` enabled for observability.

### Why In-Memory Caching Reduces Upstream Load

Fetching full meter metadata via the portal requires invoking an HMAC-signed bulk export, while fetching transformers requires iterating through multiple paginated endpoints (`/portal/dts?page=1..N`). Caching the master datasets in memory satisfies search, pagination, and meter detail queries instantly from the JVM heap, preventing redundant network requests against the legacy portal.

### Engineering Trade-Offs

| Aspect | Rationale & Trade-Off |
|:---|:---|
| **Data Freshness** | Changes made directly on the upstream portal (e.g., meter status change) can take up to 15 minutes to reflect in the API. Deemed an acceptable balance to protect the legacy portal from export load. |
| **Heap Memory Usage** | Retaining 403 full meter objects and distribution transformers in JVM memory consumes minimal RAM (< 5 MB), but this strategy would require partitioned or distributed storage if dataset size grew to millions. |
| **Process Restarts** | Caffeine is purely in-memory. Restarting the Spring Boot application clears the cache, causing the first subsequent request to experience cold-start latency while fetching from upstream. |
| **Horizontal Scalability** | Separate application replicas maintain distinct Caffeine caches. Without a shared distributed cache (e.g., Redis), each replica must independently fetch and warm its own cache from the portal. |

---

## Technology Stack

The project relies strictly on verified dependencies declared in `pom.xml`:

| Component | Technology | Version | Purpose |
|:---|:---|:---|:---|
| **Language** | Java | 21 (LTS) | Modern Java runtime, records, and pattern matching |
| **Framework** | Spring Boot | 3.4.5 | Application framework, dependency injection, auto-configuration |
| **Web Layer** | Spring Web MVC | 6.2.6 | REST controllers, HTTP request mapping, parameter validation |
| **HTTP Client** | Spring 6 `RestClient` | 6.2.6 | Synchronous HTTP client configured with `JdkClientHttpRequestFactory` |
| **Caching** | Caffeine | Managed by Spring Boot | In-memory caching provider for Spring Cache abstraction |
| **API Docs** | Springdoc OpenAPI UI | 2.8.6 | Runtime OpenAPI 3.0 generation and Swagger UI interface |
| **JSON Parser** | Jackson (`jackson-databind`) | Managed by Spring Boot | Object-to-JSON serialization and dynamic `JsonNode` traversal |
| **Boilerplate** | Project Lombok | Optional | Annotation processing for concise code |
| **Testing** | JUnit 5 & Spring Test | Managed by Spring Boot | Unit testing, MockMvc controller slicing, and Mockito mocks |
| **Build Tool** | Apache Maven | 3.9+ | Build lifecycle, dependency resolution, packaging |

---

## Implemented API Endpoints

All endpoints are hosted under the `/api` prefix and return JSON payloads.

| HTTP Method | Endpoint | Description | Query / Path Parameters |
|:---|:---|:---|:---|
| `GET` | `/api/meters` | Paginated listing of all smart meters with full 7-level hierarchy and geo-coordinates | `page` (int, default: `1`, min: `1`)<br/>`size` (int, default: `20`, max: `100`) |
| `GET` | `/api/meters?search={term}` | Filter meters by case-insensitive search across meter ID, serial number, make, and DT code | `search` (string)<br/>`page` (int, default: `1`)<br/>`size` (int, default: `20`) |
| `GET` | `/api/meters/{meterId}` | Retrieve complete metadata record for an individual meter | `meterId` (path, string, e.g., `J100002`) |
| `GET` | `/api/meters/{meterId}/consumption` | Fetch live time-series energy consumption readings directly from the portal | `meterId` (path, string, e.g., `J100002`) |
| `GET` | `/api/transformers` | Paginated listing of electrical distribution transformers | `page` (int, default: `1`, min: `1`)<br/>`size` (int, default: `20`, max: `100`) |

---

### Endpoint Usage & Copyable Examples

#### 1. List Meters (Paginated)

```bash
curl -X GET "http://localhost:8080/api/meters?page=1&size=5" \
  -H "Accept: application/json"
```

**PowerShell equivalent:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/meters?page=1&size=5" -Method Get | ConvertTo-Json -Depth 6
```

**Sample Response (`200 OK`):**
```json
{
  "data": [
    {
      "meterId": "J100000",
      "serialNo": "SE33962",
      "make": "HPL",
      "phaseType": "single",
      "installStatus": "Decommissioned",
      "installType": "Whole Current",
      "build": "legacy",
      "dtCode": "DT-001",
      "hierarchy": {
        "zone": { "name": "Jaipur Zone 1", "code": "Z-01" },
        "circle": { "name": "Circle 1", "code": "C-01" },
        "division": { "name": "Division 1", "code": "D-01" },
        "subdivision": { "name": "Subdivision 1", "code": "SD-01" },
        "substation": { "name": "Substation 1", "code": "SS-01" },
        "feeder": { "name": "Feeder 1", "code": "F-001" },
        "dt": { "name": "Malviya Nagar DT 1", "code": "DT-001" }
      },
      "geo": {
        "lat": 26.938961,
        "lng": 75.830957
      }
    }
  ],
  "total": 403,
  "page": 1,
  "pageSize": 5,
  "totalPages": 81
}
```

#### 2. Search Meters

```bash
curl -X GET "http://localhost:8080/api/meters?search=J100002" \
  -H "Accept: application/json"
```

Matches against `meterId`, `serialNo`, `make`, or `dtCode`. Slices the matched results according to `page` and `size`.

#### 3. Get Meter Details

```bash
curl -X GET "http://localhost:8080/api/meters/J100002" \
  -H "Accept: application/json"
```

**Sample Response (`200 OK`):**
```json
{
  "meterId": "J100002",
  "serialNo": "SE33964",
  "make": "Schneider",
  "phaseType": "three",
  "installStatus": "Installed",
  "installType": "Whole Current",
  "build": "modern",
  "dtCode": "DT-002",
  "hierarchy": {
    "zone": { "name": "Jaipur Zone 1", "code": "Z-01" },
    "circle": { "name": "Circle 1", "code": "C-01" },
    "division": { "name": "Division 1", "code": "D-01" },
    "subdivision": { "name": "Subdivision 2", "code": "SD-02" },
    "substation": { "name": "Substation 2", "code": "SS-02" },
    "feeder": { "name": "Feeder 3", "code": "F-003" },
    "dt": { "name": "Vaishali Nagar DT 2", "code": "DT-002" }
  },
  "geo": {
    "lat": 26.912434,
    "lng": 75.743122
  }
}
```

#### 4. Get Meter Consumption Readings

```bash
curl -X GET "http://localhost:8080/api/meters/J100002/consumption" \
  -H "Accept: application/json"
```

**Sample Response (`200 OK`):**
```json
{
  "meterId": "J100002",
  "count": 336,
  "readings": [
    {
      "timestamp": "23/06/2026 23:30",
      "kwh": "6850.32",
      "kvah": "7398.35",
      "voltR": "227"
    },
    {
      "timestamp": "24/06/2026 00:00",
      "kwh": "6850.78",
      "kvah": "7398.84",
      "voltR": "236"
    }
  ]
}
```

#### 5. List Distribution Transformers

```bash
curl -X GET "http://localhost:8080/api/transformers?page=1&size=10" \
  -H "Accept: application/json"
```

**Sample Response (`200 OK`):**
```json
{
  "data": [
    {
      "code": "DT-001",
      "name": "Malviya Nagar DT 1",
      "feederCode": "F-001",
      "capacityKva": 100
    },
    {
      "code": "DT-002",
      "name": "Vaishali Nagar DT 2",
      "feederCode": "F-003",
      "capacityKva": 250
    }
  ],
  "total": 45,
  "page": 1,
  "pageSize": 10,
  "totalPages": 5
}
```

---

### Key Data Fields

- **Meter Metadata**:
  - `meterId`: Unique alphanumeric system identifier (e.g., `J100000`).
  - `serialNo`: Physical hardware serial number (e.g., `SE33962`).
  - `make`: Meter manufacturer (e.g., `HPL`, `Schneider`, `Secure`).
  - `phaseType`: Electrical phase setup (`single` or `three`).
  - `installStatus`: Operational deployment lifecycle status (`Installed`, `Decommissioned`).
  - `dtCode`: Assigned Distribution Transformer code (`DT-001`).
- **Hierarchy (`HierarchyDto`)**: Full 7-level electrical distribution topology (`zone` → `circle` → `division` → `subdivision` → `substation` → `feeder` → `dt`), each with `name` and `code`.
- **Coordinates (`GeoLocationDto`)**: WGS-84 decimal coordinates (`lat`, `lng`).
- **Consumption Telemetry (`ConsumptionReading`)**:
  - `timestamp`: Interval timestamp string (`dd/MM/yyyy HH:mm`).
  - `kwh`: Active energy reading in kilowatt-hours.
  - `kvah`: Apparent energy reading in kilovolt-ampere hours.
  - `voltR`: Phase R RMS voltage in Volts.

---

## Prerequisites and Configuration

### Prerequisites

- **Java Development Kit (JDK) 21** or later installed and configured on your `PATH`.
- **Apache Maven 3.9+** installed and available via `mvn`.
- Active network access to the upstream portal: `https://urja-ops.flockenergy.tech`.

### Application Configuration Properties

All properties are bound under the `urja.portal` namespace via `AppProperties.java` and `application.yml`:

| Configuration Property | Environment Variable Override | Default Value | Purpose |
|:---|:---|:---|:---|
| `server.port` | `SERVER_PORT` | `8080` | Local HTTP listening port |
| `urja.portal.base-url` | `URJA_PORTAL_BASE_URL` | `https://urja-ops.flockenergy.tech` | Base URL of the upstream Urja portal |
| `urja.portal.email` | `URJA_PORTAL_EMAIL` | *(empty)* | Portal login credential (email) |
| `urja.portal.password` | `URJA_PORTAL_PASSWORD` | *(empty)* | Portal login credential (password) |
| `urja.portal.connect-timeout-ms` | `URJA_PORTAL_CONNECT_TIMEOUT_MS` | `5000` | HTTP connection timeout in milliseconds |
| `urja.portal.read-timeout-ms` | `URJA_PORTAL_READ_TIMEOUT_MS` | `15000` | HTTP socket read timeout in milliseconds |
| `urja.portal.cache-ttl-minutes` | `URJA_PORTAL_CACHE_TTL_MINUTES` | `15` | Caffeine cache eviction TTL in minutes |

### Configuring Portal Credentials Securely

> [!WARNING]
> Never commit portal usernames, passwords, cookies, or session tokens to source control. Secret files matching `application-local.yml`, `*.env`, and `cookies.txt` are explicitly ignored by `.gitignore`.

#### Method 1: Local Configuration File (Recommended for Local Dev)

Copy the provided example template to an active local configuration file:

```bash
# Linux / macOS
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

```powershell
# Windows PowerShell
Copy-Item src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

Edit `src/main/resources/application-local.yml` with your assigned portal credentials:

```yaml
urja:
  portal:
    email: YOUR_PORTAL_EMAIL
    password: YOUR_PORTAL_PASSWORD
```

Since the default profile is set to `local` (`spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`), Spring Boot automatically loads these settings on startup.

#### Method 2: Environment Variables

Alternatively, supply the credentials via shell environment variables:

```bash
# Linux / macOS
export URJA_PORTAL_EMAIL="YOUR_PORTAL_EMAIL"
export URJA_PORTAL_PASSWORD="YOUR_PORTAL_PASSWORD"
```

```powershell
# Windows PowerShell
$env:URJA_PORTAL_EMAIL="YOUR_PORTAL_EMAIL"
$env:URJA_PORTAL_PASSWORD="YOUR_PORTAL_PASSWORD"
```

---

## Installation and Running Locally

Follow these step-by-step instructions from a clean repository clone:

### 1. Clone the Repository

```bash
git clone https://github.com/AyushPrakash414/Flock-Energy-Assignment.git
cd Flock-Energy-Assignment
```

### 2. Configure Credentials

Create `src/main/resources/application-local.yml` using the template as described in [Prerequisites and Configuration](#prerequisites-and-configuration), and input your portal email and password.

### 3. Build the Project

Compile the source code and package the executable JAR:

```bash
mvn clean package
```

*(To compile while skipping unit tests: `mvn clean package -DskipTests`)*

### 4. Start the Application

You can run the application directly with the Spring Boot Maven plugin:

```bash
mvn spring-boot:run
```

Or run the built standalone JAR with Java 21:

```bash
java -jar target/urja-meter-ops-1.0.0.jar
```

The service initializes on **`http://localhost:8080`**.

### 5. Verify the Service

Run a quick health check against the meters endpoint:

```bash
curl -X GET "http://localhost:8080/api/meters?page=1&size=2"
```

---

## Error Handling and Reliability

### Error Response Format

All error conditions return a predictable JSON structure mapped by `GlobalExceptionHandler.java`:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Meter not found: UNKNOWN_METER"
}
```

### HTTP Status Codes

| Status Code | Trigger Condition | Handler |
|:---|:---|:---|
| **`200 OK`** | Request succeeded and returned requested data or page slice. | Controller mappings |
| **`400 Bad Request`** | Invalid input parameter: `page < 1`, `size < 1` or `size > 100`, missing required query params, or type mismatch. | `handleBadRequest` (`IllegalArgumentException`) |
| **`404 Not Found`** | Requested `meterId` does not exist in the master meter catalog. | `handleNotFound` (`ResourceNotFoundException`) |
| **`502 Bad Gateway`** | Upstream portal returned an error status (e.g., 500), authentication failure, unparseable payload, or connection breakdown. | `handleUpstream` (`UpstreamPortalException`) |
| **`503 Service Unavailable`** | Upstream portal explicitly responded with HTTP 503. | `handleUpstream` (`UpstreamPortalException`) |
| **`500 Internal Error`** | Unhandled generic server exception. | `handleGeneric` (`Exception`) |

### Reliability & Resiliency Features

- **Session Self-Healing**: `UrjaPortalClient` intercepts upstream `401 Unauthorized` or `403 Forbidden` responses, invalidates the cached session token, triggers fresh re-authentication, and replays the original request once.
- **Thundering-Herd Protection**: `UrjaAuthService` synchronizes authentication calls using a `ReentrantLock`. When the session token expires, only one thread executes the login HTTP handshake; concurrent requests await completion and reuse the newly acquired token.
- **Timeout Isolation**: Network calls to the legacy portal are bounded with strict connect (5,000 ms) and read (15,000 ms) timeouts via JDK's `HttpClient` to prevent worker thread exhaustion.

---

## Assumptions and Design Decisions

### Assumptions

1. **Legacy Portal is System of Record**: All meter, network, and consumption telemetry originates from the Urja portal. The API service acts as a mediator and cache layer without maintaining an independent write database.
2. **Read-Only Scope**: The assignment scope requires exposing query endpoints for operational inspection and telemetry retrieval; no mutation or provisioning operations against the portal are supported.
3. **Master Export Completeness**: The portal's `/portal/export` endpoint returns the entire meter fleet (403 meters) with complete 7-level hierarchy and geolocations, removing the need for 400+ sequential per-meter requests.
4. **Data Volatility Differentiation**: Meter inventories and transformer allocations change infrequently (suitable for a 15-minute cache), whereas energy consumption readings change rapidly and must be fetched live.

### Design Decisions and Trade-offs

- **Spring Boot 3.4.5 & Java 21**:
  - *Rationale*: Enterprise-grade ecosystem, robust type safety, record classes for immutable DTOs, and built-in validation support.
  - *Trade-off*: Higher memory footprint and JVM bootstrap time compared to lightweight Go or Rust binaries, but significantly faster development iteration and mature OpenAPI tooling.
- **Strict Layered Architecture (Controller → Service → Client)**:
  - *Rationale*: Isolates upstream quirks (form authentication, cookie headers, HMAC signing strings) inside client services, guaranteeing clean domain DTO contracts to API consumers.
  - *Trade-off*: Requires explicit DTO mapping layers (`HierarchyDto`, `GeoLocationDto`, `PageResponse`), but prevents upstream schema drift from breaking outward contracts.
- **Caffeine In-Memory Caching vs. Redis**:
  - *Rationale*: Zero external operational dependencies. Keeps local developer setup trivial (`mvn spring-boot:run` works out of the box without spinning up Docker or Redis containers).
  - *Trade-off*: Cache is local to the JVM instance and lost on restart. Suitable for this dataset scale (403 meters), but multi-instance production deployments would warrant Redis.
- **15-Minute Cache TTL**:
  - *Rationale*: Dramatically relieves the legacy portal from compute-heavy export operations while ensuring reasonably fresh meter status data.
  - *Trade-off*: An administrative change on the portal (e.g., de-energizing a meter) may take up to 15 minutes to reflect in `/api/meters`.

---

## What Was Intentionally Left Out

To keep the codebase focused, maintainable, and aligned with the backend engineering assignment:

- **Frontend Application**: The assignment specifies building a backend REST API service. Consumers can interact via Swagger UI, Postman, or third-party web apps.
- **Custom User Registration / Authentication**: Client authentication on the REST endpoints was omitted to keep API testing straightforward for reviewers.
- **External Persistent Database (Postgres / MySQL)**: Avoided introducing an external database container because the upstream portal already serves as the canonical persistent store.
- **Distributed Cache Cluster (Redis / Memcached)**: Avoided external cache infrastructure overhead; Caffeine handles in-process caching cleanly.
- **Complex Geospatial Indexing (PostGIS / R-Trees)**: Basic coordinates (`lat`, `lng`) are passed through as DTO fields rather than indexed for spatial bounding-box queries.

---

## Testing

The project includes unit and slice tests written in JUnit 5 using MockMvc and Mockito.

### Running the Test Suite

Execute the Maven test lifecycle:

```bash
mvn test
```

### Existing Test Suite Breakdown

All 8 tests execute cleanly and cover the core business layers:

- **`HmacSignerServiceTest`** (2 unit tests):
  - Validates that HMAC-SHA256 signature generation produces a valid 64-character hexadecimal string with an epoch timestamp.
  - Verifies that different signing secrets generate distinct cryptographic signatures.
- **`MeterControllerTest`** (4 slice tests via `@WebMvcTest`):
  - `listMeters_returnsPagedResult`: Verifies pagination wrapper, HTTP 200, and JSON structure.
  - `getMeterDetail_returnsNotFoundForUnknown`: Verifies that querying a non-existent meter returns HTTP 404 with structured error JSON.
  - `getMeterConsumption_returnsReadings`: Verifies consumption telemetry retrieval and reading array formatting.
  - `listMeters_badPageParameter_returns400`: Verifies that passing invalid parameters (`page=0`) triggers HTTP 400 Bad Request.
- **`TransformerControllerTest`** (1 slice test via `@WebMvcTest`):
  - `listTransformers_returnsPaged`: Verifies paginated transformer response and field mapping.
- **`FlockEnergyApplicationTests`** (1 integration test via `@SpringBootTest`):
  - Verifies that the Spring Boot application context bootstraps and configures all beans without errors.

### Testing Gaps to Address

While the present suite verifies controller contracts and crypto logic, it lacks:
- Upstream integration tests against mock HTTP responses (e.g., using WireMock to simulate portal 500s, 503s, and token expirations).
- Concurrency testing for `UrjaAuthService` lock contention during simultaneous session timeouts.

---

## OpenAPI Documentation

API documentation is generated dynamically at runtime using `springdoc-openapi-starter-webmvc-ui` (v2.8.6):

- **Interactive Swagger UI**: Explore and execute requests directly in your browser:
  ```
  http://localhost:8080/swagger-ui.html
  ```
- **OpenAPI 3.0 Specification**: Download or inspect the raw JSON schema:
  ```
  http://localhost:8080/v3/api-docs
  ```

To generate a static `openapi.json` file on disk while the server is running:

```bash
curl -s http://localhost:8080/v3/api-docs > openapi.json
```

A pre-configured Postman collection is also provided in the repository root: [`postman_collection.json`](./postman_collection.json).

---

## Reflection

### 1. What assumptions did you make?
- Assumed the legacy Urja portal is the sole authoritative data store; local caching is an optimization and resilience layer rather than a primary store.
- Assumed meter metadata (403 records) and transformer network mapping are relatively static (justifying a 15-minute TTL), whereas consumption readings change continuously and must be fetched live.
- Assumed the portal's `/portal/export` endpoint reliably returns all 403 meters with complete 7-level hierarchy and coordinates, avoiding the latency of 400+ per-meter requests.

### 2. Which part was the most difficult, and how did you get unstuck?
- **The Challenge**: Reverse-engineering the portal's proprietary authentication and export signing mechanisms without official documentation. Specifically, discovering why `/portal/export` rejected standard session cookie requests with 401/403.
- **How I Got Unstuck**: Analyzed the portal's frontend network bundles, identified the signing secret retrieval from `/portal/keys`, and deduced the exact HMAC-SHA256 signature payload format (`method\npath\nqueryString\ntimestamp`) and required headers (`x-timestamp`, `x-signature`). Replicated this in `HmacSignerService` and verified it against live requests.

### 3. If you had another day, what would you improve?
- Implement **WireMock** integration tests to thoroughly simulate edge cases (flaky portal responses, unexpected HTML error pages, and session expiration under high concurrency).
- Add **Spring Boot Actuator** cache metrics (`/actuator/metrics/cache.gets`, `/actuator/metrics/cache.evictions`) and a dedicated health check indicator for upstream portal reachability.
- Introduce a **proactive background cache refresh** (e.g., scheduled task every 14 minutes) so end users never experience cache-miss latency.

### 4. What mistake did you make while solving this?
- Initially attempted to construct meter hierarchy and coordinates by issuing individual per-meter detail requests (`/portal/meters/{id}/geo`). This introduced an $O(N)$ HTTP request bottleneck (400+ sequential network calls), which caused severe latency and risked rate-limiting. I stepped back, inspected the portal's bulk export feature, and pivoted to the HMAC-signed `/portal/export` endpoint to ingest all 403 meters in a single HTTP call.

### 5. If reviewing your own submission, what would you criticize?
- **Lazy Cache Warmup**: Because the cache is lazy, whichever user issues the first request after startup or TTL expiry suffers the full latency of the upstream export call. A background pre-warming task would ensure zero-latency reads.
- **Single-Node In-Memory Cache**: Caffeine is isolated to a single JVM instance. If this service is scaled horizontally behind a round-robin load balancer, each instance will independently query the portal and maintain separate cache states unless migrated to Redis.
- **Live Consumption Latency**: Because consumption data is fetched live on every request without short-term memoization (e.g., a 30-second TTL), rapid successive queries for the same meter could place unnecessary strain on the portal.

---

## Future Improvements

1. **WireMock Test Harness**: Add full HTTP mock tests for `UrjaPortalClient` to simulate network partitions and malformed portal JSON.
2. **Spring Boot Actuator & Metrics**: Expose Prometheus metrics for cache hit/miss ratios, upstream response times, and auth token renewal cycles.
3. **Resilience4j Circuit Breaker**: Prevent cascading thread exhaustion when the upstream portal is degraded or undergoing maintenance.
4. **Scheduled Background Pre-Warming**: Replace lazy loading with a background scheduled executor to keep the master dataset warm at all times.
5. **Consumption Query Parameters**: Extend `/api/meters/{meterId}/consumption` with `startDate` and `endDate` range filters once supported by the upstream API.

---

## Supporting Documentation

- **Postman Collection**: [`postman_collection.json`](./postman_collection.json) — Ready-to-import Postman collection with parameterized requests for all 5 endpoints, search filters, and error tests.
- **OpenAPI Schema**: Accessible live at `/v3/api-docs` when the service is running, or exportable to `openapi.json` via curl.
- **Protocol & Integration Details**: Detailed reverse-engineering insights, session cookie lifecycles, and HMAC signing payload structures are fully documented in the client implementation source code:
  - [`UrjaAuthService.java`](src/main/java/com/flockenergy/meterops/client/UrjaAuthService.java) (Session acquisition and lock handling)
  - [`HmacSignerService.java`](src/main/java/com/flockenergy/meterops/client/HmacSignerService.java) (HMAC-SHA256 signature generation)
  - [`UrjaPortalClient.java`](src/main/java/com/flockenergy/meterops/client/UrjaPortalClient.java) (HTTP routing and transparent session recovery)
