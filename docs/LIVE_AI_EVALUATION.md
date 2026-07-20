# Live AI Evaluation

## Scope

The live evaluator measures whether one explicitly selected provider/model
reliably authors structurally valid bio prose from the application-owned safe
category request. A valid result is one to three sentences with the exact
grounding placeholders, within the deterministic policy and length bounds. The
evaluator does not use exact-string assertions and does not grade subjective
tone, creativity, or relevance.

The normal `test`, `check`, and `build` tasks remain credential-free and make no
provider calls. `liveAiEval` is a separate, explicitly invoked, potentially
billable task. It is not wired into the PR workflow.

## Data and request boundary

The versioned corpus is
`src/test/resources/live-ai/bio-cases-v1.json`. It contains only:

- opaque case IDs;
- fixed structural slice IDs;
- closed `SafeJobCode` values; and
- closed, nonempty `SafeInterestCode` lists.

The corpus contains no names, raw job titles, hobbies, places, coordinates,
identifiers, credentials, prompts, or provider responses. Its loader rejects
unknown fields, duplicate JSON keys, duplicate codes, invalid slices, and
incomplete safe-code coverage before any network authorization.

Before each provider invocation, the evaluator independently checks the
application-owned input allowlist and the prompt, schema, and output-token
fingerprints. Immediately before each HTTP send it checks the fixed HTTPS
destination, method, path, header names, timeout, forbidden body fields, and
the actual HTTP call budget. It never retains HTTP requests: their headers
contain the live credential.

## Plan without network access

Choose exactly one provider and its exact approved model. The approved primary
plan uses Gemini with `12 cases x 38 repetitions = 456` aggregate calls, a
minimum six-second interval between attempt starts, and a one-sided 95% Wilson
upper failure bound of 1%:

```bash
LIVE_AI_PROVIDER=gemini \
LIVE_AI_EVAL_REPETITIONS=38 \
LIVE_AI_EVAL_MAX_CALLS=456 \
LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=6000 \
LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=0.01 \
GEMINI_LIVE_MODEL='gemini-2.5-flash-lite' \
./gradlew liveAiEval --args='--plan'
```

Plan mode validates the corpus, derives the current Git revision and
application-request fingerprints, checks the maximum-call authorization and
pacing configuration, and prints only sanitized metadata. Its output includes
`pacing_strategy`, `minimum_call_interval_millis`, and the derived
`configured_minimum_call_start_span_millis`; for 456 calls at 6,000
milliseconds the minimum first-to-last attempt-start span is 2,730,000
milliseconds. Plan mode does not require a provider credential or the live-run
confirmations.

## Authorized live run

A real run requires a clean Git working tree so its code revision is exact.
Provision the selected API key in the environment before invoking Gradle; do
not put credentials in command arguments, Gradle properties, or JSON files.

For the primary Gemini run:

```bash
LIVE_AI_PROVIDER=gemini \
LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=6000 \
RUN_LIVE_AI_TESTS=true \
GEMINI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED=true \
LIVE_AI_AUTOMATIC_TELEMETRY_DISABLED_CONFIRMED=true \
LIVE_AI_APPLICATION_REQUEST_INSPECTION_CONFIRMED=true \
GEMINI_LIVE_MODEL='gemini-2.5-flash-lite' \
GEMINI_API_KEY="$(<.secrets/gemini-api-key)" \
./gradlew --no-daemon liveAiSmoke
```

The dedicated smoke task requires exactly one supported `LIVE_AI_PROVIDER`.
For that selected provider it fails, rather than skips green, when any
authorization, model, credential, or pacing value is missing or malformed.
After a successful compatibility smoke, run the aggregate separately:

```bash
LIVE_AI_PROVIDER=gemini \
LIVE_AI_EVAL_REPETITIONS=38 \
LIVE_AI_EVAL_MAX_CALLS=456 \
LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=6000 \
LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=0.01 \
RUN_LIVE_AI_TESTS=true \
GEMINI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED=true \
LIVE_AI_AUTOMATIC_TELEMETRY_DISABLED_CONFIRMED=true \
LIVE_AI_APPLICATION_REQUEST_INSPECTION_CONFIRMED=true \
GEMINI_LIVE_MODEL='gemini-2.5-flash-lite' \
./gradlew liveAiEval
```

The provider-specific approval means that the human owner accepts provider
retention, abuse monitoring, human review, and product-improvement use, as
applicable, for only the fixed synthetic smoke fixtures, versioned aggregate
corpus, and fixed application-owned prompt/schema used by this evaluation. It
does not assert that provider logging is disabled. It does not authorize
production or customer-derived content, and it cannot satisfy the separate
production privacy, retention, residency, and subprocessor review.

If the Gemini compatibility smoke or pre-run quota check fails, OpenAI may be
used as a separately precommitted fallback with
`OPENAI_LIVE_MODEL='gpt-4o-mini-2024-07-18'` and
`OPENAI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED=true`. The fallback must
run the complete protocol independently and remain within the approved USD
0.20 spend ceiling. Do not combine providers, top up a partial Gemini run, or
select a provider after comparing aggregate results. Anthropic uses the
corresponding `ANTHROPIC_*` variables if separately selected and approved. The
evaluator reads only the selected provider credential and constructs only that
provider client. For this approved paid OpenAI fallback, explicitly set
`LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=0` in both plan and execution; the human
owner determined that quota pacing is unnecessary for paid calls.

Planned model-provider invocations are exactly `cases x repetitions`. The
runner performs no retry, best-of-N, pass@k selection, provider fallback, or
discarded attempt. The transport counts delegated HTTP send attempts and blocks
an unexpected additional attempt before it reaches the network. An unexpected
harness exception stops the run after its first sanitized failed attempt rather
than spending the remaining budget.

`LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS` is required and accepts an integer from zero
through 60,000. It controls minimum attempt-start spacing: the first attempt
starts immediately, provider latency counts toward the interval, and the runner
waits only for any remaining interval before the next attempt. The approved
Gemini-first plan uses `6000`, which limits attempt starts to at most ten per
minute. Verify the selected project's current RPM, TPM, daily quota, and usage
before the separate three-call smoke. If the active quota requires slower
pacing, precommit a larger interval and rerun plan mode; never change it during
an evidence run. Zero is an explicit unpaced configuration, not a missing
control; it is the approved value for the paid OpenAI fallback. Any resulting
rate-limit outcome still counts as a failure and is never retried.

The three-call live adapter smoke is compatibility and request-boundary
preflight. It is not pooled into the 456 aggregate attempts. Let the applicable
quota window clear between smoke and aggregate runs.

## Approved reliability gate

The human-approved target is an overall one-sided 95% Wilson upper failure
bound of 1%:

```bash
LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=0.01
```

At 456 attempts, zero failures has an upper bound of approximately 0.5898% and
one failure approximately 0.9769%, so both pass. Two failures has an upper bound
of approximately 1.3166% and fails. Thus this exact protocol accepts at most one
failure. The 456 calls are the smallest realizable 12-case multiple for which
one observed failure can still satisfy the approved 1% criterion.

The target remains an explicit environment value rather than a hidden default.
Hard request-boundary violations and harness errors always fail the command
after the sanitized report is written. Distinct-prose and deterministic-catalog
match counts are diagnostics only; they do not affect the reliability gate.

## Report and interpretation

The evaluator writes only:

```text
build/reports/live-ai-eval/report.json
```

`build/` is ignored. Report schema version 3 contains:

- provider, exact model ID, clean Git revision, corpus version and hash;
- derived prompt and output-schema hashes;
- pacing strategy and configured minimum call interval in provenance;
- configured minimum first-to-last attempt-start span in provenance;
- pacing wait-event count and actual wait nanoseconds in a top-level `pacing`
  object;
- planned attempts, provider-client invocations, and delegated HTTP send
  attempts;
- results by normalized failure category;
- per-case, per-slice, and overall observed failure rates;
- one-sided 95% Wilson upper failure bounds;
- latency p50, p95, and maximum;
- valid-prose, distinct-valid-prose, and deterministic-catalog-match counts,
  with only aggregate counts retained;
- sanitized hard-boundary and harness-error counts; and
- the boolean synthetic retention/data-use approval that authorized the run.

It contains no request or response body, exception message, credential, or raw
profile content. Provider clients currently discard usage metadata, so the
report records invocation count, delegated HTTP send attempts, and local
end-to-end latency. It does not claim provider acceptance or billing and cannot
report tokens or dollar cost. Distinct output fingerprints exist only in memory
long enough to produce aggregate counts; no prose or individual fingerprint is
written to the report.

The Wilson bound is conditional on this fixed, equally weighted synthetic
corpus and the provider conditions during the recorded run. It is not a claim
about production traffic or all future provider states. Case order uses a
recorded `cyclic_rotation_v1` strategy to reduce systematic coupling between
one case and its position in repeated runs. Per-case and overlapping-slice
intervals are descriptive; they are not simultaneous 95% guarantees.

Passing this protocol establishes reliable production of prose that satisfies
the application-owned structural and security contract under the recorded
provider conditions. It does not establish that the prose is delightful,
meaningfully personalized beyond the safe categories, or preferred by people;
that would require a separately approved qualitative review protocol.

Do not commit or publish a report without a separate privacy and evidence
review. A scheduled workflow, recurring credential use, retention policy, or
production reliability gate requires separate operational approval.
