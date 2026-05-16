## GoLinkGone Backend

GoLinkGone is a Spring Boot backend for URL shortening, redirect resolution, analytics, and visual link generation.

## Overview

The service provides:

* Short link creation and redirect handling
* Click tracking and dashboard analytics
* Geo-IP enrichment for visitor location data
* QR code and barcode generation
* Actuator endpoints for health and metrics

## High Performance

The backend is tuned for low-latency redirect handling and analytics processing.

* Uses time-ordered epoch UUIDs for primary keys to improve index locality and insert performance.
* Preloads short keys into an in-memory `ConcurrentHashMap` so collision checks stay fast and invalid URLs can be rejected without a database lookup.
* Uses a custom asynchronous `ThreadPoolTaskExecutor` sized to match the Hikari connection pool and reduce thread exhaustion under load.

## Multi-Layer Caching

Caching is used to reduce repeat database and external service work.

* `ConcurrentMapCacheManager` caches resolved URLs for frequently accessed links.
* Caffeine stores Geo-IP lookup results for 24 hours to reduce external API calls and avoid rate-limiting.

## Requirements

* Java 21
* Maven
* PostgreSQL
* Required environment variables for database, auth, and external service configuration

## Configuration

Runtime settings are loaded from `src/main/resources/application.yml` and environment variables.

Key values include:

* `server.port` set to `8080`
* Database connection from `DB_URL`
* Allowed origins from `ALLOWED_ORIGINS`
* OAuth2 resource server settings from `ISSUER_URI` and `JWK_SET_URI`
* Supabase settings from `PROJECT_URL` and `SUPABASE_SERVICE_ROLE_KEY`
* MaxMind GeoIP settings from `MAXMIND_DB_PATH` and `MAXMIND_LICENSE_KEY`

## Running Locally

Start the application with Maven Wrapper:

```bash
./mvnw spring-boot:run
```

The application runs on `http://localhost:8080` by default.

## Testing

Run the test suite with:

```bash
./mvnw test
```

The test suite covers the main backend flows, including URL shortening, redirect handling, analytics, error handling, and rate limiting.

## Load Testing

A baseline k6 script is available at `load-tests/baseline-test.js`.

Baseline profile:

* Duration: 30 seconds
* Virtual users: 1000

Purpose:

* Validate redirect latency under load
* Exercise caching and async analytics paths

## Project Notes

* The backend uses Spring Data JPA, caching, and async processing for redirect and analytics workflows.
* The wrapper script is present in the repository, but if your local checkout does not mark it executable, run it through `bash` as shown above.
