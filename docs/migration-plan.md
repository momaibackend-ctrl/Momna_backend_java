# Kotlin/Ktor to Java/Spring Boot migration plan

## Source of truth
Source repository: `AICorn-Rocket-Group/momna-backend` on `main`.

Observed source footprint:
- 374 tracked files
- 238 Kotlin source files
- 30 SQL migration files
- Kotlin 2.0.21 / JDK 21 / Ktor 3.0.3
- PostgreSQL 16, Flyway, Redis 7 and private S3-compatible object storage
- modular monolith with Core, Platform and Product Module boundaries

## Target
Java 25, Spring Boot 4.x, Spring MVC, Spring Security, Spring Data JPA/Hibernate,
PostgreSQL, Liquibase, Gradle, Testcontainers and springdoc OpenAPI.

## Rules
1. Preserve externally observable API behavior and database semantics.
2. Preserve module boundaries.
3. Migrate Flyway SQL in original order into Liquibase-managed changesets.
4. Keep secrets outside the repository.
5. Work on `migration/kotlin-to-java-spring`.
6. Do not call the migration complete while source behavior is missing or stubbed.

## API inventory
The canonical source OpenAPI exposes 37 HTTP operations covering foundation health/version,
authentication/session/account center/profile/lifecycle/billing and universal flow-instance/onboarding APIs.
Check-in is additionally implemented in source code and has its own transport/runtime contracts.

## Domain inventory
Core: auth, billing, context, fields, flows, identity, lifecycle, privacy/data lifecycle, timeline.
Platform: AI, audit, cache/Redis, database, feature flags, health, jobs, localization,
notifications, observability, safety, security, storage/S3.
Modules: agent, calendar, check-in, content, couple, diary, integration, medical,
my-day, my-world, onboarding, scanner.

## Execution order
1. Spring Boot foundation and runtime configuration.
2. Flyway to Liquibase database compatibility.
3. Auth/session and Spring Security.
4. Identity/profile/privacy.
5. Lifecycle and billing.
6. Universal flow engine and onboarding API.
7. Check-in persistence/scoring/safety/My Day orchestration.
8. Platform services and remaining modules.
9. Testcontainers/unit/API compatibility tests.
10. Docker/CI/OpenAPI final verification.
