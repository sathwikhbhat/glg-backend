# Deploying GoLinkGone — a step-by-step walkthrough

This guide takes you from nothing to a live deployment. Read it top to bottom
the first time; each section explains *why* it exists, not just what to type.

## What you're building

There are three moving parts and they live in three different places:

- **Backend** (this Spring Boot app) runs in Docker on a single DigitalOcean
  droplet. It serves the dashboard API on `api.golinkgone.com` and the link
  redirects on `tryglg.ink`.
- **Auth + database** live on Supabase (already set up). The backend talks to
  them over the network using the values in `glg.env`.
- **Frontend** (the Vite dashboard) is a separate project that builds to static
  files and is hosted on Cloudflare Pages at `golinkgone.com`.

Cloudflare sits in front of everything. A browser request flows like this:

```
browser  ->  Cloudflare edge  ->  Caddy (on the droplet, TLS)  ->  JVM (Docker)
```

Caddy is a tiny reverse proxy that runs next to the backend on the droplet. It
terminates HTTPS and forwards the request to the JVM over Docker's internal
network. The JVM is never exposed to the public internet directly — the only
way in is through Caddy, which is the only thing listening on ports 80/443.

Why Caddy at all? The backend counts unique visitors and does geo-lookups, both
of which need the *real* client IP. With Cloudflare in front, the connection the
droplet sees comes from a Cloudflare server, not the visitor. Cloudflare passes
the visitor's true IP in a header called `CF-Connecting-IP`; Caddy copies that
into `X-Forwarded-For`, and the backend's Tomcat is already configured to read
the real IP from there. That's the whole reason Caddy is in the picture.

---

## Step 1 — Move both domains to Cloudflare

Cloudflare can only proxy traffic and issue certificates for domains whose DNS
it controls. That means changing each domain's nameservers from Namecheap to
Cloudflare. You do this once per domain.

1. Create a free account at https://dash.cloudflare.com and log in.
2. Click **Add a site**, type `golinkgone.com`, and choose the **Free** plan.
3. Cloudflare scans your existing DNS records and shows them. Don't worry about
   getting them perfect now — you'll set the records you actually need in
   Step 6. Continue to the end.
4. Cloudflare gives you **two nameservers**, something like
   `dana.ns.cloudflare.com` and `rob.ns.cloudflare.com`. Copy them.
5. In a separate tab, log in to **Namecheap** → **Domain List** → **Manage**
   next to `golinkgone.com` → **Nameservers** → switch from "Namecheap
   BasicDNS" to **Custom DNS**, and paste Cloudflare's two nameservers. Save.
6. **Repeat steps 2–5 for `tryglg.ink`.** It becomes a second, independent zone
   in Cloudflare with its own (possibly different) pair of nameservers.

Propagation usually takes minutes but can take a few hours. Cloudflare emails
you when each domain is "Active". You can keep working on the other steps while
you wait — nothing below needs the domains to be active until Step 9.

---

## Step 2 — Create the droplet

1. In DigitalOcean, **Create → Droplets**.
2. Image: **Ubuntu** (latest LTS). Plan: **Basic → Regular**, the
   **`s-2vcpu-4gb`** size ($24/mo — covered by your credit).
3. **Region: pick the same region as your Supabase project.** Every database
   query crosses the network, so co-locating them keeps latency low.
4. Authentication: **SSH key**. If you don't have one, generate it locally with
   `ssh-keygen -t ed25519` and paste the contents of the `.pub` file. The
   matching private key is what GitHub Actions will use later to deploy.
5. Create the droplet and note its **public IP** — you'll need it repeatedly.

---

## Step 3 — Bootstrap the droplet

SSH in as root (`ssh root@<droplet-ip>`) and install Docker plus create the
directories the app expects:

```sh
# Installs docker-ce AND the "docker compose" v2 plugin from Docker's own repo.
curl -fsSL https://get.docker.com | sh

# /opt/glg holds config + the GeoIP database; /var/log/glg holds app logs.
mkdir -p /opt/glg/certs /var/log/glg
```

Everything the deployment needs will live under `/opt/glg`. The next steps fill
that directory.

---

## Step 4 — Get the Origin Certificates (one per domain)

An Origin Certificate is a free TLS certificate Cloudflare issues for the link
between Cloudflare and your server (Caddy). Because `golinkgone.com` and
`tryglg.ink` are two separate Cloudflare zones, **you create one certificate in
each zone** — a single cert can't cover both.

Do this for the **golinkgone.com** zone first:

1. Cloudflare dashboard → select the `golinkgone.com` zone.
2. **SSL/TLS → Origin Server → Create Certificate**.
3. Leave the default "Let Cloudflare generate a private key and CSR". The
   hostnames default to `golinkgone.com` and `*.golinkgone.com` — that's what
   you want (the `*` covers `api.golinkgone.com`). Validity: 15 years. Create.
4. You'll see **two** text blocks:
   - **Origin Certificate** — this is the cert.
   - **Private Key** — this is shown **only once**; if you leave the page
     without copying it, you must start over.
5. On the droplet, save them into `/opt/glg/certs`:
   - the certificate block → `certs/golinkgone.pem`
   - the private key block → `certs/golinkgone-key.pem`

Then **repeat for the `tryglg.ink` zone**, saving:
   - certificate → `certs/tryglg.pem`
   - private key → `certs/tryglg-key.pem`

You can create the files with a text editor over SSH (`nano /opt/glg/certs/
golinkgone.pem`, paste, save). When done you should have four files in
`/opt/glg/certs`. The `Caddyfile` already points each domain at its matching
pair.

---

## Step 5 — Put the config and data files on the droplet

Copy four things into `/opt/glg` (from your machine with `scp`, or paste over
SSH):

**a) `docker-compose.yml`** — from this repo's `deploy/` folder. Open it and
change the image line's `<GHCR_OWNER>` to your **lowercase** GitHub username, so
it reads e.g. `ghcr.io/shreyasnandurkar/glg-backend:latest`.

**b) `Caddyfile`** — from this repo's `deploy/` folder, unchanged.

**c) `glg.env`** — the backend's production environment variables. This file is
secret and is never committed to git. It must define:

```
DB_URL=...                      # Supabase Postgres JDBC URL
DB_USERNAME=...
DB_PASSWORD=...
ISSUER_URI=...                  # Supabase auth issuer
JWK_SET_URI=...                 # Supabase JWKS endpoint
PROJECT_URL=...                 # Supabase project URL
SUPABASE_SERVICE_ROLE_KEY=...
MAXMIND_LICENSE_KEY=...
# MAXMIND_DB_PATH is optional; it defaults to /opt/glg/GeoLite2-City.mmdb
# Leave TRUSTED_PROXIES unset — the default already trusts Docker's network.
```

**d) `GeoLite2-City.mmdb`** — the MaxMind GeoIP database, placed at
`/opt/glg/GeoLite2-City.mmdb`. The app memory-maps it for geo-lookups and
refreshes it weekly on its own once running.

---

## Step 6 — Set DNS records and SSL mode in Cloudflare

Now tell Cloudflare where each hostname points.

In the **golinkgone.com** zone → **DNS → Records**:

- **A** record, name `api`, value `<droplet-ip>`, **Proxy status: Proxied**
  (orange cloud). This is `api.golinkgone.com` → your backend.
- The apex `golinkgone.com` and `www` will be pointed at Cloudflare Pages in
  Step 11 — leave them for now, or Pages will create them for you.

In the **tryglg.ink** zone → **DNS → Records**:

- **A** record, name `@` (the apex `tryglg.ink`), value `<droplet-ip>`,
  **Proxied**. This is where short links resolve and redirect.

Then in **each** zone → **SSL/TLS → Overview**, set the encryption mode to
**Full (strict)**. This makes Cloudflare require the valid Origin Certificate
you installed in Step 4, end-to-end encrypted.

---

## Step 7 — Lock the firewall to Cloudflare (important)

Your droplet has a public IP, so in principle someone could skip Cloudflare and
hit Caddy directly — and forge the `CF-Connecting-IP` header to fake their
location. Closing that hole is one firewall rule away.

In DigitalOcean → **Networking → Firewalls → Create Firewall**, attach it to the
droplet, and set inbound rules:

- **HTTP (80)** and **HTTPS (443)**: allow only from **Cloudflare's IP ranges**
  (the current list is at https://www.cloudflare.com/ips/).
- **SSH (22)**: allow only from your own IP.

Everything else stays blocked. Now the only way to reach the app is through
Cloudflare, exactly as intended.

---

## Step 8 — Set up GitHub for automated builds

The image is published to GitHub Container Registry (ghcr.io). GitHub Actions
pushes it automatically using its built-in token, so the only credential you
need to create is a read-only token for the droplet to *pull* the image.

1. Create a **Personal Access Token (classic)**: GitHub → Settings → Developer
   settings → Personal access tokens → Tokens (classic) → Generate new. Give it
   only the **`read:packages`** scope. Copy the token.
2. In the backend repo → **Settings → Secrets and variables → Actions**, add:

   | Secret | Value |
   |---|---|
   | `GHCR_USER` | your GitHub username |
   | `GHCR_PAT` | the `read:packages` token from step 1 |
   | `DROPLET_HOST` | the droplet's public IP |
   | `DROPLET_USER` | `root` (or a deploy user you created) |
   | `DROPLET_SSH_KEY` | the **private** SSH key matching the droplet's key |

The image package is created automatically on the first push and starts
**private**; the `GHCR_PAT` is what lets the droplet pull it.

---

## Step 9 — Trigger the first deploy

There's no image in ghcr.io yet, and the workflow is what builds it. So the
first deploy is simply your first push to `main` — the pipeline builds the
image, pushes it, then SSHes into the droplet and starts everything. By now the
domains should be "Active" in Cloudflare and Steps 5–8 done (files in
`/opt/glg`, secrets set).

```sh
git push origin main
```

Watch the run under the repo's **Actions** tab. When the `deploy` job finishes,
check it landed on the droplet:

```sh
ssh root@<droplet-ip>
cd /opt/glg
docker compose ps              # backend + caddy should be "running"
docker compose logs -f backend # startup with no stack traces
```

Then from your own machine, visit `https://api.golinkgone.com/actuator/health`
— it should return `{"status":"UP"}`. Create a short link and confirm
`https://tryglg.ink/<key>` redirects.

If the `deploy` job fails at the pull step, log in once on the droplet so Docker
can reach your private image, then re-run the job:

```sh
echo "<GHCR_PAT>" | docker login ghcr.io -u "<GHCR_USER>" --password-stdin
```

---

## Step 10 — Automated deploys from then on

The deploy pipeline lives in `.github/workflows/maven.yml`. On every push to
`main` it:

1. Builds and runs the tests (the `build` job).
2. Only if tests pass, the `deploy` job packages the JAR, builds the Docker
   image, pushes it to `ghcr.io/<you>/glg-backend` tagged with both the git
   commit SHA and `latest`, then SSHes into the droplet and runs
   `docker compose pull && docker compose up -d`.

So your normal workflow is just: commit, push to `main`, done. The site updates
itself a few minutes later.

To **roll back**, point the `image:` tag in `/opt/glg/docker-compose.yml` at a
specific older SHA (e.g. `:a1b2c3d`) and run `docker compose up -d`, or
`docker run` that tag directly.

---

## Step 11 — Deploy the frontend on Cloudflare Pages

The dashboard frontend is a separate Vite project, so it deploys on its own and
isn't part of the backend's pipeline. There's no server — Pages builds the
static files and serves them from Cloudflare's edge for free.

1. Cloudflare dashboard → **Workers & Pages → Create → Pages → Connect to Git**,
   and pick the frontend's GitHub repository.
2. Build settings:
   - **Build command:** `npm run build`
   - **Build output directory:** `dist`
3. Add an environment variable: **`VITE_API_BASE_URL` = `https://api.golinkgone.com`**.
   This is how the frontend knows where the backend lives; the backend's CORS
   config already allows `golinkgone.com` to call it.
4. After the first deploy, go to the Pages project → **Custom domains** and add
   `golinkgone.com` and `www.golinkgone.com`. Pages creates the DNS records and
   issues/renews TLS for these automatically — you do **not** need an origin
   certificate for the frontend.

From then on, every push to the frontend repo's main branch redeploys it.

---

## Quick reference — what ends up in `/opt/glg`

```
/opt/glg/
  docker-compose.yml          # edited: <GHCR_OWNER> set to your username
  Caddyfile
  glg.env                     # secret — Supabase + MaxMind credentials
  GeoLite2-City.mmdb
  certs/
    golinkgone.pem            # Origin cert, golinkgone.com zone
    golinkgone-key.pem
    tryglg.pem                # Origin cert, tryglg.ink zone
    tryglg-key.pem
```
