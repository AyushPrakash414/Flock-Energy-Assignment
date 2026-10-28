# Urja Meter Ops API

A clean, documented REST API service that retrieves data from the legacy **Urja Meter Ops** portal and exposes it in structured JSON format for programmatic consumption.

## Architecture

```
┌─────────────────┐     ┌────────────────┐     ┌───────────────────────┐
│   API Consumer   │────▶│  Spring Boot   │────▶│   Urja Meter Ops      │
│  (Postman, App)  │◀────│  REST API      │◀────│   Portal (upstream)   │
└─────────────────┘     └────────────────┘     └───────────────────────┘
                         │ Caffeine Cache │
                         │ HMAC Signer    │
                         │ Session Mgmt   │
                         └────────────────┘
```

**Key design decisions:**
- **Bulk export caching**: The portal has an HMAC-signed export endpoint that returns all 403 meters with full hierarchy and geo data. We cache this dataset (configurable TTL, default 15 min) enabling sub-millisecond search, pagination, and detail lookups.
- **Live consumption**: Energy readings are fetched live from the portal since they change frequently.
- **Automatic session management**: The portal session token (1-hour TTL) is cached and automatically renewed on expiration.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.4.x |
| Language | Java 21 LTS |
| Build | Maven 3.9+ |
| HTTP Client | Spring 6 RestClient |
| Caching | Caffeine |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, MockMvc, Mockito |

## Prerequisites

- **Java 21** (JDK)
- **Maven 3.9+**

## Quick Start

### 1. Clone and configure

```bash
git clone https://github.com/<your-username>/Flock-Energy-Assignment.git
cd Flock-Energy-Assignment
```

Portal credentials are configured via environment variables (defaults are in `application.yml` for development):

```bash
export URJA_PORTAL_BASE_URL=https://urja-ops.flockenergy.tech
export URJA_PORTAL_EMAIL=<your-portal-email>
export URJA_PORTAL_PASSWORD=<your-portal-password>
```

### 2. Build and run

```bash
mvn clean install
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

### 3. Explore the API

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## API Endpoints

### Meters

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/meters` | List/search meters (paginated) |
| `GET` | `/api/meters/{meterId}` | Get full meter details |
| `GET` | `/api/meters/{meterId}/consumption` | Get consumption readings |

#### Query Parameters for `GET /api/meters`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `search` | string | — | Filter by meter ID, serial, make, or DT code |
| `page` | int | 1 | Page number (1-indexed) |
| `size` | int | 20 | Items per page (max 100) |

### Transformers

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/transformers` | List distribution transformers (paginated) |

## Example Requests & Responses

### List meters
```bash
curl http://localhost:8080/api/meters?page=1&size=5
```
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
      "geo": { "lat": 26.938961, "lng": 75.830957 }
    }
  ],
  "total": 403,
  "page": 1,
  "pageSize": 5,
  "totalPages": 81
}
```

### Search meters
```bash
curl "http://localhost:8080/api/meters?search=J100002"
```

### Get meter details
```bash
curl http://localhost:8080/api/meters/J100002
```

### Get consumption
```bash
curl http://localhost:8080/api/meters/J100002/consumption
```
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
    }
  ]
}
```

### List transformers
```bash
curl http://localhost:8080/api/transformers?page=1&size=10
```

## Error Responses

All errors return a consistent JSON structure:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Meter not found: UNKNOWN"
}
```

| Status Code | Meaning |
|-------------|---------|
| `200` | Successful retrieval |
| `400` | Invalid parameters |
| `404` | Meter not found |
| `502` | Upstream portal error |
| `503` | Portal unavailable |

## Project Structure

```
src/main/java/com/flockenergy/meterops/
├── FlockEnergyApplication.java       # Entry point
├── config/                            # Configuration beans
│   ├── AppProperties.java            # Portal connection settings
│   ├── CacheConfig.java              # Caffeine cache setup
│   ├── OpenApiConfig.java            # Swagger metadata
│   └── RestClientConfig.java         # HTTP client config
├── controller/                        # REST endpoints
│   ├── MeterController.java
│   └── TransformerController.java
├── service/                           # Business logic
│   ├── MeterService.java
│   └── TransformerService.java
├── client/                            # Portal integration
│   ├── UrjaAuthService.java          # Session management
│   ├── HmacSignerService.java        # Export signing
│   └── UrjaPortalClient.java         # HTTP client
├── dto/                               # Response models
│   ├── MeterResponse.java
│   ├── ConsumptionReading.java
│   ├── TransformerResponse.java
│   ├── HierarchyDto.java
│   ├── HierarchyNodeDto.java
│   ├── GeoLocationDto.java
│   └── PageResponse.java
└── exception/                         # Error handling
    ├── GlobalExceptionHandler.java
    ├── ErrorResponse.java
    ├── ResourceNotFoundException.java
    └── UpstreamPortalException.java
```

## Running Tests

```bash
mvn test
```

## Credential Safety

Portal credentials are **never** committed to version control. They are configured via either:

1. **Local config file (Git-ignored):**
   Copy `src/main/resources/application-local.yml.example` to `src/main/resources/application-local.yml`:
   ```yaml
   urja:
     portal:
       email: <your-portal-email>
       password: <your-portal-password>
   ```
   *(Note: `application-local.yml` is listed in `.gitignore` and will never be pushed to GitHub).*

2. **Environment variables:**
   ```bash
   export URJA_PORTAL_EMAIL=<your-portal-email>
   export URJA_PORTAL_PASSWORD=<your-portal-password>
   ```

Session tokens and passwords are never logged in application logs.
