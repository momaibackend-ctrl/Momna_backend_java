# MOMNA-1145: Java Check-in v3.1 + Medical Safety v2.2

**State: implementation PR, NOT clinical release approval.**
Base branch: `migration/kotlin-to-java-spring`; feature: `feature/momna-1145-safety-v22`.
Do not deploy clinical recommendations while `CLINICAL_RELEASE_NOT_APPROVED` gates remain.

## Java changes

- Canonical weighted v3 JSON remains inside the existing zip resource; `WeightedCheckinDefinitionCatalog.get(..., 3)` reads legacy sessions.
- Current `latest(...)` exposes version **4**, pool **weighted-v3.1**. Three additional phase occurrences: `P19` morning/evening, `PP19` postpartum morning. They are unscored until product supplies numeric weights. **State 2 still opens R10/R17.**
- Existing scoring calculation, adjustment thresholds and global `safety_score >= 12` candidate remain unchanged. Direct entry is separate.
- `MedicalSafetyCatalog` loads 28 routes and 53 facts from `medical-safety-catalog-v2.2.json`.
- `CanonicalSafetyService.evaluateMedical` is reused by the Check-in orchestration and emergency/calendar/WB1 adapters; medical decisions must not be inferred by the frontend.
- Durable route state and answer amendments are private user-owned data. Never export `F_HARM_THOUGHTS`, WB1, PM1/PM2 or any R18 data to Couple Mode.
- `V31` adds safety state, optional WB1 one-time response, and explicit correction audit.

## Authenticated API additions

**Check-in (under `/api/v1/check-in`):**

| Method | Path | Purpose |
|---|---|---|
| GET | `/definitions/{phase}` | Current period definition, items and RU/US-EN states |
| PATCH | `/sessions/{sessionId}` | Existing endpoint; patch now starts qualifying safety routes |
| GET | `/sessions/{sessionId}` | Existing endpoint; `safety.routes[]` now contains server result |
| POST | `/sessions/{sessionId}/safety/{routeId}/answers` | `{"factCode":"F_FLUID_LEAK","answer":"NO"}` |
| POST | `/sessions/{sessionId}/safety/{routeId}/revise` | Explicit correction of existing answer, audited |
| POST | `/sessions/{sessionId}/safety/{routeId}/skip` | Close remaining questions as ABSENT |
| POST | `/sessions/{sessionId}/safety/R17/blood-pressure` | `{"systolic":120,"diastolic":80,"unknown":false}` |

**Global medical safety (under `/api/v1/medical-safety`):**

| Method | Path | Purpose |
|---|---|---|
| GET | `/catalog` | Read current facts, routes and localized screens for online rendering; **not a signed offline bundle** |
| POST | `/er0` | `{"selectionCode":"F_CHEST_PAIN"}` opens one-tap ER0 |
| POST | `/er0/{routeId}/answers` | Answer R25/R17 follow-up |
| POST | `/er0/R17/blood-pressure` | Input BP1 on ER0 follow-up |
| GET | `/wb1` | Check one-time WB1 and cooldown; product timing window remains unset |
| POST | `/wb1` | `{"answer":"HARD_LATELY"}` starts R18; other allowed values `MOSTLY_GOOD`, `PREFER_NOT_TO_SAY` |
| POST | `/wb1/answers` | Follow-up Q-MH facts for R18 |
| POST | `/calendar/signals` | Persist explicit signal from saved calendar entry; `POSTCOITAL_BLEED`, `BLEEDING_RECORDED` (only in MENOPAUSE), `PREGNANCY_VAGINAL_BLEED` |

The caller must supply an authenticated user; no unauthenticated call or unverified emergency phone number is introduced. ER0 should remain accessible from all approved surfaces via the existing session.

**MedicalStep shape**, returned from the canonical resolver:

```json
{
  "routeId": "R10",
  "routeCode": "PREGNANCY_PRETERM_LABOR",
  "catalogVersion": "medical-safety-v2.2",
  "ruleVersion": "medical-safety-rules-v2.2",
  "state": "QUESTION",
  "disposition": "PENDING",
  "screenCode": "Q",
  "actionCode": "ANSWER_FACT",
  "nextFactCode": "F_FLUID_LEAK",
  "conditionStatus": "CLINICAL_RELEASE_PENDING"
}
```

When `state=COMPLETE`, use `screenCode` and `actionCode`; do not invent a second frontend triage decision. For `nextFactCode` use catalog text and allowed YES/NO/UNSURE/ABSENT values. For BP1 use the separate measured-pressure endpoint; the clinician's numeric thresholds remain UNAPPROVED.

## Tests and release gates

Automated Java unit coverage validates 14 current definitions, legacy v3 retention, direct route entries, PP2 special semantics, all 28 catalog entries and resolver completion paths, dangerous symptom combinations, cooldown boundaries, saved YES, amendment, ER0 and Calendar screens. GitHub CI executes `./gradlew clean test bootJar --no-daemon` and `docker build`.

**This does not satisfy all acceptance criteria yet:**

- No 28/28 authenticated **HTTP E2E** suite with PostgreSQL, lifecycle fixtures and concurrent requests. The 28-route resolver tests are unit tests, not HTTP E2E tests.
- No integration from actual persisted Calendar diary events, full derived `D_*` completeness/UNKNOWN, or corrections/recomputation. The explicit calendar entry adapter exists.
- No signed offline catalog, verified country phone directory, provisional reconciliation and no-downgrade guarantee.
- No approved WB1 28-day date window or scheduling rules; do not display automatically from `stageAndCooldownEligible` until `displayWindowStatus` is approved.
- No clinically approved BP1 thresholds, postpartum heavy-bleeding timing, gestational-movement start week, conditional warning gates, safe PM2 intent escalation, cross-combination releases or route cooldown review. The app must not claim clinical approval.
- Full original `F_MOVEMENTS_ESTABLISHED` 14-day NO cache, precise L3 stopping on every possible answer, 121-code v2.0 trace-audit, user-level retry concurrency, accessibility and end-to-end privacy tests need separate verification.
- Check-in `safety.routes[]` is an additive API change; KMP frontend consumers must be tested for compatibility.

**Merge policy:** draft PR only. Technical CI green does NOT enable production medical release.
