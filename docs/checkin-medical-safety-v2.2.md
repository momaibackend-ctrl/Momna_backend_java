# Check-in medical safety v2.2 (MOMNA-1145)

Check-in safety for all seven periods runs on the Unified Medical Safety Catalog v2.2: 28 routes, 53 facts.
It replaces the single "safe / urgent" clarification, which was opened only by `safety_score >= 12`.

This is the product implementation. It is not a clinical release approval: thresholds, pauses, combinations and
texts stay `CLINICAL_RELEASE_PENDING` in every step until they are clinically approved.

## Entry (D12, D13)

- **Direct entry.** An item bound to a route opens the route at state 2. There is no score requirement.
  - PP2 is the exception: it opens R18 at state 1 ("emotionally hard"). State 2 ("can't tell yet") never opens it.
- **Cumulative entry.** `safety_score >= 12` still opens the route of the strongest bound item. It is a second,
  independent path.
- **Several routes.** One answer change can open several routes. A fact answered on one route of a session is
  never asked again on another route of that session.
- **New items (D13).** Definitions v4 (`weighted-v3.1`) add two items:
  - P19, pregnancy, morning and evening, opens R10;
  - PP19, postpartum, morning, opens R17.

  Neither item has an approved weight, so both are unscored; the direct entry still works. Sessions started on
  v3 keep reading the v3 definition.

## Pauses (L13)

Pause windows per route: 24 h for acute routes, 72 h for R10 and R27, 7 days for R19 and R22–R24, 14 days for
R18. R25 and ER0 have no pause.

| Situation | Result |
|---|---|
| The same item again, within the window | No new questions |
| A different bound item of the route (new symptom) | Route opens again |
| The item was lowered, then raised again (worsening) | Route opens again |
| A route that ended with a YES, within the window | Not asked again, whatever the trigger; its warning keeps showing in the session's `routes[]` |
| After the window | A fresh run; old answers are not reused |
| One session | Never runs the same route twice |
| An open question when the opening answer is lowered | The question closes; the remaining facts are not recorded as NO |

## API

All actions are under `/api/v1/check-in/sessions/{sessionId}/safety/{routeId}/`. Each needs an
`Idempotency-Key` header and `expectedRevision` in the body.

| Action | Body |
|---|---|
| `answers` | `factCode`, `answer` (YES / NO / UNSURE / ABSENT); only the step's `nextFactCode` |
| `revise` | `factCode`, `answer`. The old answer goes to `medical_safety_fact_revisions` and the route is decided again |
| `checklist` | `mode` (SELECTED / NONE / UNSURE / SKIP), `selectedFactCodes`. QM is one screen |
| `blood-pressure` | `systolic`, `diastolic` or `unknown`. Only when `nextFactCode` is `BP_READING` |
| `skip` | Closes the remaining questions as ABSENT, never as NO |

The session's `safety.routes[]` lists every route step: `routeId`, `routeCode`, `state`, `disposition`,
`screenCode`, `actionCode`, `nextFactCode`, `questionFactCodes`, `conditionStatus` and the versions.

An open question blocks submit with `SAFETY_CLARIFICATION_REQUIRED`. After the window closes, the session finalises
once its questions are answered.

## What My Day reads

The session's SafetyDecision is the strongest outcome of the routes the session opened. Its severity:

| Disposition | Severity | My Day |
|---|---|---|
| EMERGENCY, SAFETY_ASSESSMENT (PM2) | CRITICAL | health overlay, no doctor line (the urgent path shows it) |
| URGENT | HIGH | health overlay, no doctor line |
| PROMPT | MEDIUM | health overlay, PROMPT doctor line |
| UNKNOWN, or a question still open | UNKNOWN | health overlay, UNKNOWN doctor line |
| ROUTINE | LOW | nothing |
| NOT_REPORTED (nothing found) | NONE | nothing |

## Not in this change

- The global surfaces of v2.2: ER0 one-tap emergency (R25, R26), the WB1 postpartum card, and calendar signals.
  They come next. Until then, `GLOBAL_*` routes and WB1 do not reach My Day.
- The clinical release gates listed in MOMNA-1145.
