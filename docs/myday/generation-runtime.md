# My Day generation at runtime (MOMNA-849)

`POST /api/v1/my-day/generate` makes the day's 9 cards with an LLM (prompts v1.2, `Momna-MyDay-LLM-Spec-All7-Periods-v1.2.md`).

## Request path

1. **Template key.** `MyDayTemplateKey` (MOMNA-1325) is built from anonymous inputs only.
2. **Template cache.** The anonymous cache `momna.my_day_template_cache` is checked first. A hit is stored as the user's day without calling the model (`card_source = TEMPLATE_CACHE`).
3. **Model call on a miss.** `LlmMyDayCardSource` → `CanonicalSafetyGuardedAi` → `CanonicalAiGateway` → `OpenAiCompatibleProviderAdapter` (`POST {base-url}/chat/completions`, `response_format: json_object`). The system and user prompts of the life mode go as two messages.
4. **Validation.** The answer must be exactly 3 + 3 + 3 cards, each with 7 non-empty fields and a title of at most 7 words.
5. **Failures.** A timeout (15 s), HTTP 429/5xx or an invalid answer gets **one retry**. HTTP 4xx gets no retry. After that, generation answers 503 `MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE` and stores nothing; the app shows «not ready yet».
6. **Storing.** A valid answer is stored as the user's day (`card_source = LLM`) and in the template cache (TTL 90 days).
7. **Cost.** Every call is recorded in `momna.ai_cost_ledger` with a SHA-256 of the user id. Per-request and per-user-per-day budgets stop the call before the model.

The prompt contains only the template key fields: no user id, email, name, Check-in answer or free text. The template cache therefore holds no personal data, and one template may serve every user with the same key.

## Configuration

Generation is **off by default**. While it is off, there is no card source and My Day answers 503, as before.

| Environment variable | Default | Meaning |
|---|---|---|
| `MOMNA_AI_MYDAY_ENABLED` | `false` | `true` turns on the LLM path |
| `MOMNA_AI_BASE_URL` | — | OpenAI-compatible base URL, e.g. `https://api.openai.com/v1` |
| `MOMNA_AI_API_KEY` | — | Provider key. Set it as a secret in the environment only; it is never logged or stored |
| `MOMNA_AI_MYDAY_MODEL` | `gpt-4.1-mini` | Model name |
| `MOMNA_AI_TIMEOUT` | `15s` | Timeout per attempt |

The following can be overridden as Spring properties under `momna.ai.myday.*`:
- price per 1k tokens (`input-usd-micros-per-1k-tokens` 400, `output-usd-micros-per-1k-tokens` 1600);
- budgets (`max-cost-usd-micros-per-request` 50000, `max-tokens-per-request` 16000, `max-cost-usd-micros-per-user-day` 500000);
- `temperature` 0.7;
- `template-ttl` 90d.

With generation enabled and no key or base URL, the application does not start. This is deliberate: a misconfiguration does not silently turn into 503.

## Logs

The `myday.generation` lines record:
- `cache_hit` / `cache_miss`;
- `llm_latency_ms` with `attempts` and `fallback`;
- `llm_error code=…`.

Only life mode and language are logged: no user id, no prompt and no card text.

## Versions

- Prompts: `myday.prompts.v1.2` (registry version 3), resources `src/main/resources/myday/prompts/v1.2`. `MyDayPromptCatalogTest` checks them word for word against the spec.
- Model policy: `myday.cards:v1`. It is part of the template-cache key, so a new policy version does not reuse old templates.
- Template key: `myday.template-key.v2`.
