# Telecom Service Management - Backend

Spring Boot 4 (Java 21+), Spring Security (JWT), Spring Data JPA, MySQL 8.

## Run locally
1. Copy `.env.example` to `.env` and fill in the values (`.env` is git-ignored — never commit it).
2. Make sure MySQL is running and the DB user in `.env` can access `telecom_db`.
3. `./mvnw spring-boot:run` — API on http://localhost:8080 (health: `/actuator/health`).
4. Tests: `./mvnw test`

The first start creates the tables, an admin account (from `ADMIN_EMAIL` / `ADMIN_PASSWORD`) and, if `SEED_DEMO=true`, fake demo data.

## Configuration (environment variables)
| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection |
| `JWT_SECRET` | >= 32 random characters |
| `JWT_EXPIRY_MINUTES` | token lifetime (default 30) |
| `CORS_ALLOWED_ORIGINS` | comma-separated exact frontend origins |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first admin, created if no admin exists |
| `SEED_DEMO`, `DEMO_PASSWORD` | local dev only: fake demo data |

## Deploy on Render
Step-by-step guide (Aiven MySQL + Render + Vercel): `../docs/deployment.md`. This repo includes `Dockerfile` and `render.yaml` (Render Blueprint); health check path is `/actuator/health`.

API reference: `../docs/api-contract.md`. Security rules: `../docs/security.md`.
