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

## Global entries (§8, §9.4, §12)

These run outside a Check-in session, under `/api/v1/medical-safety`. Every POST needs an `Idempotency-Key`: the
same key and request replays the stored response. The lifecycle period is always the server's.

- **ER0, "I feel very unwell now".** `GET /er0` returns the screen texts and the signs for the user's period: 10
  common signs and up to 3 for pregnancy or postpartum. `POST /er0` handles one tap:
  - an emergency sign goes straight to its screen (E, AG1, PM2, OB1); the other signs stay unanswered;
  - `NONE` ("None of these") records NO for the shown signs;
  - taps of one episode (24 h) add up, which gives screen M;
  - ER0 is never paused;
  - facial swelling (R25) and postpartum headache or vision changes (R17) ask their questions through
    `/routes/{routeId}/answers` and `/blood-pressure`.
- **WB1.** `GET /wb1` says whether to show the card and gives its texts. It is shown once, from day 28 to day 42
  after birth. The day count comes from the onboarding birth date; without one, from the middle of the
  approximate answer. It is never shown after an answer, nor while R18 ran in the last 14 days.
  - `POST /wb1` with `HARD_LATELY` opens R18, with harm first.
  - The other two answers close the card for good.
- **Calendar.** `POST /calendar/signals` takes one signal:
  - `POSTCOITAL_BLEED` (menarche, cycle, planning, perimenopause) → P;
  - `BLEEDING_RECORDED` (menopause) → MB1;
  - `PREGNANCY_VAGINAL_BLEED` → R9.

  There are no daily questions, and missing history is never NO.
- **A shared run.** A global entry on a route whose question is open in a Check-in session joins that session's
  run, so one run and one decision. The response names `checkinSessionId`, and the session's decision is
  updated.
- **My Day** adds the global outcomes completed on its local day or the day before. GLOBAL routes count for every
  period's health overlay; R18 from WB1 is `post_mood_heavy`.
- `GET /catalog` returns the public catalog for online rendering, with no rules or editorial notes.

## Catalog v2.3 (Product decision 28.11.2026, MOMNA-1146)

The catalog is now `medical-safety-v2.3` / `medical-safety-rules-v2.3` (resources `safety/*-v2.3.json`). It changes only the R18 harm question `F_HARM_THOUGHTS`, whatever the entry (PP2, PP18, WB1):

| Answer | Outcome | Screen | My Day | R18 pause |
|---|---|---|---|---|
| «Not now» (skip, L10) | `NOT_ANSWERED` (severity NONE) | H1, no N | nothing | 3 days |
| «Not sure» (L11), nothing higher | `PROMPT` (MEDIUM) | `PM1-H`: PM1 plus the link «Not feeling safe right now? How to get urgent help» | `post_mood_heavy` and the PM1 doctor line | 7 days |
| Every answer NO | `NOT_REPORTED` | H1 | nothing | 14 days |
| YES | `SAFETY_ASSESSMENT` | PM2 | as before | as before |

- A higher outcome (PM2, URGENT, EMERGENCY) always wins.
- PM1 itself is unchanged.
- The policy is data (`policies.harmThoughtsOutcomes` in the catalog JSON).
- **«Not now» outside a Check-in.** `POST /api/v1/medical-safety/routes/{routeId}/skip` gives «Not now» on a route that WB1 or ER0 started. It needs an `Idempotency-Key`. The remaining questions are ABSENT, never NO.

## Not in this change

- The clinical release gates listed in MOMNA-1145 and MOMNA-1146: thresholds, pauses, the gestational week at
  which reduced movements count, and the PM2 intent protocol.
- A signed offline ER0 bundle and a verified directory of regional emergency numbers (§10). Until then, the
  client shows an instruction instead of dialling.
