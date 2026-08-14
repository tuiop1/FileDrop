# FileDrop frontend

This directory contains the standalone Angular UI for the FileDrop Spring Boot API. It is intentionally small: one API service, standalone feature components, Angular Router, reactive forms, and SCSS.

## Requirements

- Node.js `22.22.3+`, `24.15.0+`, or `26+` (the included `.nvmrc` selects Node `24.15.0`)
- npm `10+`
- Java 25 for the backend
- Docker and Docker Compose for the local infrastructure

From this directory, install the locked frontend dependencies:

```bash
npm install
```

`npm ci` is also suitable for a clean, reproducible install.

## Start the backend and its dependencies

FileDrop depends on:

- PostgreSQL for drop metadata
- MinIO for S3-compatible object storage
- ClamAV for malware scanning

The backend also requires `FILEDROP_MASTER_KEY`, a Base64-encoded 32-byte key. Generate it once for local development and keep it unchanged while reusing existing database and MinIO volumes; changing it makes existing encrypted files unreadable.

From the repository root, create an untracked `.env` file once:

```bash
(umask 077; test -f .env || printf 'FILEDROP_MASTER_KEY=%s\n' "$(openssl rand -base64 32)" > .env)
chmod 600 .env
```

Reuse that file on later runs. Start the infrastructure, wait for its health checks, run the idempotent MinIO setup job to completion, and then start Spring Boot:

```bash
docker compose up -d --wait postgres clamav minio
docker compose run --rm minio-init
cd backend
set -a
. ../.env
set +a
SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw spring-boot:run
```

ClamAV may need a little time on its first start while it prepares its signature database. The backend listens on `http://localhost:8080`.

## Start Angular

In a second terminal, from `frontend/`:

```bash
npm start
```

You can equivalently run `npx ng serve`. Open `http://localhost:4200`.

Development requests beginning with `/api` are proxied to `http://localhost:8080` by [`proxy.conf.json`](proxy.conf.json). The application therefore uses relative API URLs and the Spring backend does not need a CORS configuration. The proxy is configured in `angular.json`, so it applies to both commands above.

## Production build

```bash
npm run build
```

The optimized browser application is written to `dist/frontend/browser/`.

## Run only the frontend with Docker Compose

The Java backend is intentionally not a Compose service. Start the infrastructure and Spring Boot manually using the commands above.

If you prefer to serve the frontend from its production Nginx image instead of using `npm start`, run this from the repository root:

```bash
docker compose --profile frontend up --build frontend
```

Then open `http://localhost:4200`. Nginx serves Angular, falls back to `index.html` for client-side routes, and proxies `/api` through `host.docker.internal` to the manually launched backend on `http://localhost:8080`.

The frontend uses a separate Compose profile so infrastructure-only commands do not start it automatically. Compose never starts the Java backend. The host-backend command above disables Spring Boot's automatic Compose lifecycle because the infrastructure is started and checked explicitly first.

## Frontend routes

- `/` — upload a file and configure expiration, maximum downloads, and an optional password
- `/download/:token` — request the binary file and show the downloads remaining response header
- `/manage/:id?token=:managementToken` — inspect, edit, refresh, or delete a drop

The management URL contains a secret token. Treat the full URL like a password and do not share it with download recipients.

## Password-protected downloads

The backend validates and hashes an optional password during upload. A protected download first returns `DOWNLOAD_PASSWORD_REQUIRED`; the frontend then asks the recipient for the password and submits it as JSON to the same download path. Passwords are never placed in the URL, and an incorrect password does not consume a download slot.

After the token, drop state, and optional password are accepted, the backend reserves a download slot before retrieving and decrypting the object. This prevents concurrent requests from performing expensive storage work after the limit is reached. A storage, integrity, streaming, or client/network failure after reservation can therefore still consume that download attempt.

Use HTTPS outside local development so passwords and file contents are encrypted in transit.

## API integration notes

- Uploads send `file` and a JSON Blob named `metadata` in `multipart/form-data`; the browser supplies the multipart boundary.
- Downloads use GET when no password has been requested and POST a JSON password challenge for protected drops. Both paths use a Blob response and observe the full HTTP response so `Content-Disposition` and `X-Downloads-Remaining` are available.
- Every details, expiration, max-downloads, and delete request sends `X-Management-Token`.
- Local date/time inputs are converted to ISO-8601 instants before they are sent.
- Backend JSON errors, Spring validation fallbacks, text errors, and Blob-wrapped download errors share one presentation format.
