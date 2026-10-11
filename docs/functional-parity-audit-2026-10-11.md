# Momna Java — functional parity verification (2026-10-11)

## Scope and ownership

This document tracks the migration team's remaining deliverables: functional API parity, onboarding/router and check-in/My Day behavior, regression tests, and fixes for confirmed migration defects.

Excluded from this team's scope: deployment, infrastructure provisioning, operational acceptance, CI/CD ownership, and choosing/operating a Flyway-to-Liquibase migration strategy.

## Baseline and branch safety

- Repository: `momaibackend-ctrl/Momna_backend_java`
- Source of this audit: latest `main` as inspected on 2026-10-11.
- The previous `migration/kotlin-to-java-spring` branch was **116 commits behind main**, with zero commits ahead. Its CheckinController lacked the safety clarification endpoint that exists on main.
- All changes for this audit belong on `audit/functional-parity-2026-10-11`, branched from main. Do not overwrite or reset main.

## Verified by repository inspection (not by running tests)

| Item | Result | Evidence |
| --- | --- | --- |
| Menarche router → onboarding → check-in HTTP test exists | PASS (test presence only) | `src/test/java/com/momna/integration/AuthenticatedApiHttpE2eTest.java` method `menarcheRouterOnboardingAndCheckinEndToEnd` |
| Seven periods start/resume/replay HTTP test exists | PASS (test presence only) | `allSevenPeriodOnboardingRoutesStartResumeAndReplay` |
| Other six periods onboarding completion HTTP test exists | PASS (test presence only) | `remainingSixPeriodOnboardingsCompleteOverHttp` |
| Menarche morning/evening HTTP test exists | PASS (test presence only) | `menarcheMorningAndEveningCheckinEachPersistAndFinalizeOverHttp` |
| Menarche expiry/auto-finalization HTTP tests exist | PASS (test presence only) | Three `menarcheExpired...` tests in same file |
| Menarche safety clarification and emergency HTTP tests exist | PASS (test presence only) | `menarcheHighWeightedRiskOpensClarificationThenResolvesOverHttp`, `menarcheEmergencyClarificationIsIrreversibleOverHttp` |
| Safety clarification response API route exists on main | PASS (route presence only) | `src/main/java/com/momna/modules/checkin/api/CheckinController.java`, POST `/sessions/{sessionId}/safety/clarifications/{clarificationId}/answer` |
| HTTP E2E suite executed successfully in this audit | BLOCKED | Runtime has Java 21 (project targets Java 25), no Docker, and cannot resolve github.com for local checkout |
| Full Kotlin vs Java endpoint and DTO equivalence | NOT VERIFIED | Requires API inventory of both repositories and executable contract comparison |
| All seven periods complete check-in E2E coverage | NOT VERIFIED | Existing inspected method names explicitly exercise Menarche check-in; equivalent full-period check-in tests require investigation |
| My Day end-to-end content/period parity | NOT VERIFIED | Requires explicit scenario-level coverage and actual execution |

**Important:** PASS above verifies existence of named code or tests only. It does **not** mean the assertions passed at runtime or that user flows are functionally complete.

## Required next execution sequence

1. Use a Java 25 + Gradle + isolated PostgreSQL/Testcontainers environment and run the existing HTTP E2E suite. Store command, exit code, logs, and commit SHA.
2. Compare Kotlin/Ktor and Java routes and contracts, plus Postman collections. Record each method, path, payload, status, auth requirements and any discrepancy.
3. Finish Menarche coverage: conditional onboarding branches, morning/evening check-in, safety prompts, blocked/urgent/emergency outcomes, stale revision, idempotency, close-window auto-finalization, and My Day. Preserve negative cases.
4. Extend actual check-in E2E verification to CYCLE, PLANNING, PREGNANCY, POSTPARTUM, PERIMENOPAUSE and MENOPAUSE, one period at a time.
5. Fix proven Java implementation defects on this audit branch, add regression tests and rerun. Never claim success based only on test names or static analysis.
6. Hand over proven defects, configuration requirements and precise evidence to the backend engineer; leave deployment and operational work to that engineer.

## Status

**FUNCTIONAL PARITY: BLOCKED for runtime verification.** Test coverage exists, but has not been executed in this audit. No application code has been modified by this audit document.
