# Momna Backend Java

Java/Spring Boot migration of the canonical Momna backend.

## Runtime

- Java 25
- Spring Boot 4
- Spring MVC and Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Liquibase
- Redis through Lettuce
- private S3-compatible object storage
- Gradle
- Testcontainers
- springdoc OpenAPI

The canonical source for this migration is the Kotlin/Ktor Momna backend. The Java implementation preserves the externally observable API contract, database semantics, module boundaries, privacy/safety rules, flow definitions and infrastructure behavior rather than translating Kotlin syntax file-for-file.

## Build

```bash
./gradlew clean test bootJar
```

## Run locally

Create local configuration from `.env.example`. Do not commit real credentials.

Required database settings:

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/momna
DATABASE_USER=...
DATABASE_PASSWORD=...
```

Then run:

```bash
./gradlew bootRun
```

## Container

```bash
docker build -t momna-backend-java .
docker run --rm -p 8080:8080 --env-file .env momna-backend-java
```

## API contracts

The source contracts are preserved at:

- `openapi.json` — canonical public Core/Foundation API
- `openapi-onboarding.json` — onboarding compatibility contract

CI includes a reflection-based contract gate that verifies every path in both OpenAPI files has a Java controller mapping.

## Database migrations

All canonical migrations `V1` through `V30` are preserved under:

`src/main/resources/db/liquibase/sql`

Liquibase executes them through `db.changelog-master.yaml`. CI verifies that every migration version is present exactly once.

## Main functional areas

Core and application behavior includes authentication/session management, profile and consent state, lifecycle state and transitions, billing/entitlements, universal flows and seven onboarding periods, check-in persistence/scoring/My Day orchestration, context/field registry, privacy and data lifecycle.

Platform behavior includes AI gateway and safety gating, audit, cache/Redis, database transaction/readiness boundaries, feature flags, jobs, localization/time/country policy, notifications, observability, security and private object storage.

Product module boundaries for Agent, Calendar, Couple, Diary, Medical, My Day, My World, Onboarding and Scanner are preserved as Java integration contracts where the canonical Kotlin source exposes contract-only module boundaries.

## Health

- `GET /` service identity
- `GET /health` legacy liveness
- `GET /health/live` process liveness
- `GET /health/ready` dependency-aware readiness
- `GET /version` public API version

## Status and verification

This repository is the working Momna backend from here on; the Kotlin/Ktor implementation
(`AICorn-Rocket-Group/momna-backend`) is kept as the reference it was ported from.

Behavioural parity with the working Kotlin backend is proven over HTTP, not asserted. Backend
Autopilot built and started both implementations in throwaway environments (PostgreSQL and
Redis), ran every scenario of the Postman collections in `postman/` against both, and compared
every response:

- Core, Onboarding and Check-in: 44 of 44 documented operations exercised, 47 of 47 requests passing;
- 47 of 47 compared steps identical (status, content type and body), 0 differences;
- the Kotlin reference passes the same scenarios on its own.

What is covered, and what is not yet:

- The public HTTP behaviour above is the contract; any change to it should keep `openapi.json`,
  `openapi-onboarding.json` and the collections in step.
- Unit and property tests: 38 here against about 480 in the Kotlin reference. Porting the Kotlin
  Check-in tests (weighted scoring, safety route catalog, baseline context, My Day orchestration,
  session state machine) is the first thing to strengthen.
- `AuthenticatedApiHttpE2eTest` uses Testcontainers and needs a running Docker.

Next product work tracked in Qira and prepared as pull requests: MOMNA-1135 (My Day evening
binding, PR #28) and MOMNA-1145 (weighted-v3.1 and Medical Safety v2.2, PR #29 with its Liquibase
follow-up #31).

## Working on it

- Branch from `main`, open a pull request, merge when the build and tests are green.
- Keep error bodies to the `ErrorEnvelope` schema (`code`, `message`, `requestId`, optional
  `fieldErrors`); use `ApiError`.
- Schema changes go in a new Liquibase SQL file under `src/main/resources/db/liquibase/sql`;
  never edit an applied one.
