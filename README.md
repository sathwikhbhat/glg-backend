<p align="center">
  <img src="images/GLG_Banner.png" alt="GoLinkGone — Shorter Links. Smarter Insights." width="620" />
</p>

<p align="center">
  <strong>Shorten any URL in one click. See exactly where every click came from.</strong>
</p>

<p align="center">
  <a href="https://golinkgone.com"><strong>golinkgone.com</strong></a> &nbsp;·&nbsp;
  Redirects served at <a href="https://tryglg.ink">tryglg.ink</a>
</p>

---

## What is GoLinkGone?

GoLinkGone turns any long URL into a clean short link under `tryglg.ink` — with a built-in QR code and a full analytics dashboard. No setup required to shorten a link. Sign up to track every click by country, city, device, and visitor uniqueness.

**[→ Try it at golinkgone.com](https://golinkgone.com)**

---

## Features

### For anyone
- **One-click shortening** — paste a URL, hit Generate, get a short link and a print-ready QR code instantly
- **Downloadable QR codes** — every link ships with a 200×200 PNG, ready for print or sharing offline
- **Sub-second redirects** — backed by an in-memory key store so every click lands instantly, no database round-trip

### For signed-in users
- **Per-link analytics dashboard** with four views:
  - 24-hour hourly breakdown
  - 7-day and 30-day daily timelines
  - All-time monthly chart
- **Three visitor metrics per time bucket** — Total Clicks, New Visitors, and Unique Visitors (deduped by hashed IP + User-Agent)
- **Top 50 countries** and **top 15 cities** ranked by clicks
- **Device breakdown** — Phone, Tablet, Desktop, Unknown — shown as a donut chart with percentages
- **Timezone-aware charts** — all buckets align to the browser's local timezone, including half-hour offsets
- **My Links** — paginated table to manage, copy, and delete all your short links
- **Full account deletion** — removes your auth account and cascades through every link and analytics row

---

## How It Works

```
Browser  →  Cloudflare edge  →  Caddy (TLS)  →  Spring Boot JVM  →  Supabase Postgres
```

- **Frontend** — React + Vite, hosted on Cloudflare Pages at `golinkgone.com`
- **Backend API** — Spring Boot on a DigitalOcean droplet, reachable at `api.golinkgone.com`
- **Redirects** — same backend, separate Cloudflare zone at `tryglg.ink`
- **Auth + Database** — Supabase (Postgres + ES256 JWT)
- **Geo-IP** — MaxMind GeoLite2, memory-mapped, refreshed weekly

The JVM is never exposed to the public internet directly — only Caddy listens on 80/443, and the droplet firewall allows inbound traffic only from Cloudflare's IP ranges.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.3 |
| Security | Spring Security · OAuth2 Resource Server (ES256 JWT) |
| Database | Supabase Postgres — Spring Data JPA + JdbcTemplate |
| Connection pool | HikariCP |
| Caching | Caffeine (URL cache · ownership cache · dashboard caches) |
| Rate limiting | Bucket4j — token bucket, per IP |
| Geo-IP | MaxMind GeoLite2-City (memory-mapped · weekly auto-update) |
| QR codes | ZXing |
| Primary keys | UUID v7 (time-ordered) via `uuid-creator` |
| Async / threads | Java 21 virtual threads |
| Frontend | React 18 · Vite · Tailwind CSS · Recharts |
| Frontend hosting | Cloudflare Pages |
| Backend hosting | DigitalOcean droplet · Docker · Caddy |
| CI/CD | GitHub Actions → GitHub Container Registry |

---

## Architecture Highlights

### Zero-latency redirects
All short keys are loaded into a `ConcurrentHashMap` at startup — collision checks and existence lookups are O(1) with no database access. Resolved URLs are cached in Caffeine for 24 hours. A typical redirect touches no database at all.

### Async click ingestion
The redirect handler enqueues a `ClickEventDTO` into a bounded in-memory queue and returns the 302 immediately. A background consumer drains the queue in batches every 3 seconds and writes five upserts in a single transaction:

```
unique_visitors_global  →  lifetime deduplication per (link, visitor)
link_stats_global       →  lifetime totals
link_stats_monthly      →  monthly rollup
link_device_stats       →  device rollup
link_location_stats     →  country / city rollup
unique_visitors_log     →  35-day rolling raw log for time-window queries
```

New-visitor attribution uses `INSERT ... ON CONFLICT DO NOTHING` return codes — `1` = new, `0` = returning — without a separate SELECT.

### Privacy-preserving visitor identity
Raw IPs are never stored. Visitor identity is `murmur3_128(ip + "|" + userAgent)` → UUID, used solely for deduplication counting.

### Dashboard query model
`getDashboard` runs two independent queries in parallel: `getLinkSummary` (4 sequential reads — lifetime totals, top countries, top cities, device breakdown) and `getTimeline` (1 read). Both results are cached for 1 minute. Cold load: 2 simultaneous DB connections. Warm: 0.

### Dual-domain isolation
A servlet filter enforces routing by `Host` header before Spring Security processes the request:
- `tryglg.ink` — only `GET /{key}` and `HEAD /{key}` reach the handler; all other paths return 404
- `api.golinkgone.com` — all API paths pass through; short-key paths return 404

---

## API Reference

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/health` | Public | Health check |
| `GET` | `/actuator/health` | Public | Detailed health (includes GeoIP status) |
| `POST` | `/create` | Public | Shorten a URL → `{ shortUrl, qrCode }` |
| `GET` / `HEAD` | `/{shortKey}` | Public | Redirect + async analytics |
| `GET` | `/my-links` | JWT | Paginated list of user's links |
| `GET` | `/{shortKey}/dashboard` | JWT (owner) | Per-link analytics |
| `DELETE` | `/{shortKey}` | JWT (owner) | Delete link and all analytics |
| `DELETE` | `/account` | JWT | Delete account and cascade all data |

---

## Local Development

### Prerequisites
- Java 21, Maven
- A Supabase project (database + auth)
- A MaxMind GeoLite2 license key (free at maxmind.com)

### Environment

Create a `.env` file at the project root:

```env
DB_URL=jdbc:postgresql://<host>:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=...
ISSUER_URI=https://<project>.supabase.co/auth/v1
JWK_SET_URI=https://<project>.supabase.co/auth/v1/.well-known/jwks.json
PROJECT_URL=https://<project>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=...
MAXMIND_LICENSE_KEY=...
```

### Run

```bash
./mvnw spring-boot:run
```

Starts at `http://localhost:8080`. Point the frontend's `VITE_API_BASE_URL` here.

### Test

```bash
./mvnw test
```

Integration tests use Testcontainers and require Docker.

---

## Deployment

| Service | Platform |
|---|---|
| Backend | DigitalOcean droplet — Docker + Caddy (TLS via Cloudflare Origin Certs) |
| Frontend | Cloudflare Pages at `golinkgone.com` |
| Auth + DB | Supabase |

On every push to `main`, GitHub Actions builds the fat JAR, publishes the Docker image to GHCR, and SSHs into the droplet to pull and restart the container.

Required GitHub secrets: `GHCR_USER` · `GHCR_PAT` · `DROPLET_HOST` · `DROPLET_USER` · `DROPLET_SSH_KEY`

Required env vars on the droplet (`glg.env`):

```env
DB_URL                    # Supabase session pooler JDBC URL (IPv4)
DB_USERNAME / DB_PASSWORD
ISSUER_URI / JWK_SET_URI
PROJECT_URL
SUPABASE_SERVICE_ROLE_KEY
MAXMIND_LICENSE_KEY
```

Cloudflare SSL/TLS mode must be set to **Full (strict)**. The droplet firewall should allow ports 80/443 only from [Cloudflare's published IP ranges](https://www.cloudflare.com/ips/).

The GeoLite2 database (`GeoLite2-City.mmdb`) must be present at `/opt/glg/GeoLite2-City.mmdb` before first start. The app refreshes it automatically every Sunday at 03:00 UTC.

---

## License

MIT