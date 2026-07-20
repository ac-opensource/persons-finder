# Live AI Evaluation

## Scope

The live evaluator measures whether one explicitly selected provider/model
reliably authors structurally valid bio prose from the application-owned safe
category request. A valid result is one to three sentences with the exact
grounding placeholders, within the deterministic policy and length bounds. The
evaluator does not use exact-string assertions and does not grade subjective
tone, creativity, or relevance.

The calibrated ceiling is 256 provider output tokens. The application
independently accepts at most 512 model-authored code points and 732 final
grounded code points. Paid calibration observed maxima of 70 output tokens and
195 authored code points. Scaling those two-sentence maxima by 3/2 for the
permitted third sentence gives 105 tokens and 293 code points, so the selected
limits retain substantial explicit headroom. The final limit also reserves the
existing 220-code-point maximum for locally grounded source values.

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
destination, method, path, exact credential/content-type/version header-value
fingerprints, timeout, forbidden body fields, and the actual HTTP call budget.
Only provider-specific model-ID shapes are accepted, and a model value equal to
the selected credential is rejected before reporting or network delegation.
The smoke keeps only already-approved requests in memory long enough to verify
source-value absence; no request URI, body, header value, prompt, schema, or
profile content is serialized to evidence.

## Plan without network access

Choose exactly one provider and its exact approved model. The following
credential-free command preserves the separately designed Gemini reliability
protocol for review. It does not authorize its 456 provider calls:

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

## Live smoke commands

A real run requires a clean Git working tree so its code revision is exact.
Provision the selected API key in the environment before invoking Gradle; do
not put credentials in command arguments, Gradle properties, or JSON files.

This Gemini example requires separate provider, quota, and execution approval:

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

For a three-call OpenAI compatibility smoke:

```bash
LIVE_AI_PROVIDER=openai \
LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=0 \
RUN_LIVE_AI_TESTS=true \
OPENAI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED=true \
LIVE_AI_AUTOMATIC_TELEMETRY_DISABLED_CONFIRMED=true \
LIVE_AI_APPLICATION_REQUEST_INSPECTION_CONFIRMED=true \
OPENAI_LIVE_MODEL='gpt-5.6-luna' \
OPENAI_API_KEY="$(<.secrets/openai-api-key)" \
./gradlew --no-daemon liveAiSmoke
```

The dedicated smoke task requires exactly one supported `LIVE_AI_PROVIDER`.
For that selected provider it fails, rather than skips green, when any
authorization, model, credential, or pacing value is missing or malformed.
Once provider execution begins, it writes a sanitized report on both pass and
fail before surfacing the test result. Preflight failures (for example, missing
authorization, model, credential, pacing, or a dirty revision) happen before a
report can be attributed to a provider execution:

```text
build/reports/live-ai-smoke/report.json
```

After a successful compatibility smoke, first review its observed maxima and
precommit the resulting output ceiling and aggregate spend bound. A separately
approved aggregate can then be run. The designed Gemini reliability command is:

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

The approved OpenAI investigation has a hard cumulative provider-usage cost
ceiling of USD 50. Fixed calibration reruns are permitted when preceding
sanitized evidence leaves a material truncation or compatibility question
unresolved. The 456-call reliability aggregate remains a separate protocol and
is not necessary merely to select a production output ceiling. Do not combine
providers or select a provider after comparing aggregate results. Anthropic uses the corresponding
`ANTHROPIC_*` variables if separately selected and approved. The evaluator
reads only the selected provider credential and constructs only that provider
client. For the paid OpenAI calibration, explicitly set
`LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=0` in both plan and execution; the human
owner determined that quota pacing is unnecessary for paid calls.

Planned model-provider invocations are exactly `cases x repetitions`. The
runner performs no retry, best-of-N, pass@k selection, provider fallback, or
discarded attempt. The transport counts delegated HTTP send attempts and blocks
an unexpected additional attempt before it reaches the network. An unexpected
harness exception stops the run after its first sanitized failed attempt rather
than spending the remaining budget. A clear authentication, permission,
billing, missing-model, or invalid-request response also stops before spending
the remaining planned calls. Ordinary nondeterministic outcomes—including a
200-level incomplete response, refusal, invalid structured output, timeout,
rate limit, I/O failure, or server error—remain recorded attempts; the
three-call smoke collects all three before failing once.

The aggregate evaluator atomically overwrites the same ignored durable report
after every completed paid attempt. Each checkpoint is explicitly marked
unfinalized and cannot pass the gate; normal completion replaces it with the
final report. Cancellation or interruption therefore preserves the sanitized
request, response, usage, validation, and cost inputs from every earlier
completed attempt.

`LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS` is required and accepts an integer from zero
through 60,000. It controls minimum attempt-start spacing: the first attempt
starts immediately, provider latency counts toward the interval, and the runner
waits only for any remaining interval before the next attempt. The illustrated
Gemini plan uses `6000`, which limits attempt starts to at most ten per
minute. Verify the selected project's current RPM, TPM, daily quota, and usage
before the separate three-call smoke. If the active quota requires slower
pacing, precommit a larger interval and rerun plan mode; never change it during
an evidence run. Zero is an explicit unpaced configuration, not a missing
control; it is the approved value for the paid OpenAI calibration. Any resulting
rate-limit outcome still counts as a failure and is never retried.

The three-call live adapter smoke is compatibility and request-boundary
preflight. It is not pooled into the 456 aggregate attempts. Let the applicable
quota window clear between smoke and aggregate runs.

## Separately designed reliability gate

The separately designed target is an overall one-sided 95% Wilson upper failure
bound of 1%. It was not executed for this limit-calibration task; any future run
must precommit its provider, model, call budget, pacing, and spend bound:

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

The evaluator writes:

```text
build/reports/live-ai-smoke/report.json
build/reports/live-ai-eval/report.json
.agents/evidence/live-ai-smoke/<full-revision>/<timestamp>-<provider>-<model-hash>-report.json
.agents/evidence/live-ai-eval/<full-revision>/<timestamp>-<provider>-<model-hash>-report.json
```

Each run writes identical sanitized JSON to the scratch `build/` path and the
timestamped ignored `.agents/evidence/` archive. The latter survives `clean` and
prevents a paid run from being lost before review. Both destinations are
write-tested before the first provider call. After a privacy/evidence review,
copy that exact archived JSON to `docs/evidence/live-ai/` with the provider,
full code revision, and run kind in the filename. Commit only that reviewed
JSON, never Gradle HTML/JUnit output or raw provider content.

The current harness emits aggregate report schema version 5. With the live
execution/evidence extension, it contains:

- provider, exact model ID, clean Git revision, corpus version and hash;
- derived prompt and output-schema hashes;
- the application-owned maximum output-token setting;
- the 512 model-authored, 220 maximum-source, and 732 final-grounded
  code-point limits;
- the explicit `maximum_approved_source_lengths_v1` grounding strategy;
- pacing strategy and configured minimum call interval in provenance;
- configured minimum first-to-last attempt-start span in provenance;
- pacing wait-event count and actual wait nanoseconds in a top-level `pacing`
  object;
- planned attempts, provider-client invocations, and delegated HTTP send
  attempts;
- one sanitized request-settings record per attempted HTTP request: body byte
  size, timeout, approved header presence, exact header-value fingerprint and
  destination/model/configuration matches, prompt/schema fingerprint matches, input-allowlist result,
  structured-output mode, output-token allowance, reasoning/thinking settings,
  sampling/seed/stop settings, and unexpected-field counts;
- results by normalized failure category;
- per-case, per-slice, and overall observed failure rates;
- one-sided 95% Wilson upper failure bounds;
- latency p50, p95, and maximum;
- valid-prose, distinct-valid-prose, and deterministic-catalog-match counts,
  with only those aggregate counts serialized; prose and individual
  fingerprints are not retained;
- one content-free record per completed attempt containing only its sequence,
  safe corpus case ID, normalized result, and final grounded code-point count
  when valid;
- per-case, per-slice, and overall grounded-length measurement counts, maxima,
  and valid-result missing-measurement counts;
- provider HTTP status classes, closed response/finish/incomplete categories,
  closed error code/type/parameter and terminal-failure categories, response
  byte sizes and transport latency, allowlisted scalar rate-limit/retry/provider
  processing headers, hashed request/response-ID evidence, and
  provider-reported input/output/total token usage;
- per-response sanitized metadata plus aggregate sums and maxima for output,
  reasoning/thinking, cached-input, OpenAI cache-write, cache-creation, and
  tool-use prompt tokens where the selected provider supplies those fields;
- output-text item counts and byte/code-point sizes without the output text;
- sanitized hard-boundary and harness-error counts; and
- the boolean synthetic retention/data-use approval that authorized the run.

The two earlier tracked paid aggregate reports below remain unchanged
schema-version-4 evidence and were not backfilled. They do not contain
grounded-length fields; their absence means unknown, not zero. Version 5
measures the already required composition against synthetic strings at the
maximum approved source lengths; these numbers are structural worst-case
measurements, not customer profile lengths.

Smoke report schema version 2 adds its fixed-fixture hash,
planned/attempted/not-attempted
call counts, zero retry/top-up counts, normalized result counts and sequence,
safe case IDs and per-invocation application latency/diagnostics,
per-valid-result sentence and
model-authored/final code-point counts, and the same sanitized provider
request/response/usage evidence. It is written on both pass and fail. Ordinary
model failures do not discard the remaining fixed samples; a terminal
configuration/access failure stops early and records why. Token-limit
responses can therefore be distinguished from malformed JSON, a missing
structured message, refusal, transport failure, or downstream prose
validation.

Neither report contains a request or response body, exception message,
credential, hostname, absolute path, or raw profile content. Provider request
and response IDs are hashed before serialization. Provider-reported usage
proves metered API processing, not the account's eventual monetary charge;
`actual_billed_usd` remains null unless a separate provider billing export is
reviewed. Distinct output fingerprints exist only in memory long enough to
produce aggregate counts; no prose or individual fingerprint is written.

## Recorded OpenAI calibration

The exact reviewed sanitized reports are:

- the [pre-fix smoke](evidence/live-ai/openai-0d53d270729118e11023f2fdbf053accc82f717a-smoke-failed.json);
- the first [post-fix smoke](evidence/live-ai/openai-d7d7345b5f7e8a8946958b2eca82ef5ef1ba1484-smoke-passed.json)
  and [12-case calibration](evidence/live-ai/openai-d7d7345b5f7e8a8946958b2eca82ef5ef1ba1484-eval-12-passed.json)
  under the deliberately high diagnostic cap;
- the final-limit [smoke](evidence/live-ai/openai-369e70c0de131bdd93f54a485d4fb0564439202c-smoke-256-passed.json)
  and [12-case calibration](evidence/live-ai/openai-369e70c0de131bdd93f54a485d4fb0564439202c-eval-12-256-passed.json)
  at 256 output tokens; and
- the schema-version-5 [metric-complete 12-case calibration](evidence/live-ai/openai-82ecbcef51b0eaeee0319704391df7aec6d7a46b-eval-12-256-v5-passed.json)
  at the same final limits.

The first three-call smoke at revision
`0d53d270729118e11023f2fdbf053accc82f717a` received three HTTP 200
`completed` responses, but all three repeated `{{NAME}}`; the application
rejected them as `invalid_output`. There were exactly three sends, no retries or
top-ups, 753 input tokens, 189 output tokens, zero reasoning tokens, a maximum
of 70 output tokens, and a maximum of 195 model-authored code points.

OpenAI's request schema was then constrained to the six valid placeholder
orders while the provider-neutral validator remained unchanged. At revision
`d7d7345b5f7e8a8946958b2eca82ef5ef1ba1484`, the follow-up three-call smoke
returned three valid distinct bios. A separate one-repetition 12-case
calibration returned 12 valid distinct bios and no deterministic-catalog
matches. Across those 15 successful calls, reported reasoning tokens were zero;
the maximum output was 58 tokens, maximum authored length was 188 code points,
and maximum sentence count was two. The three-call smoke additionally recorded
a maximum grounded length of 243 code points; the historical schema-version-4
aggregate did not record grounded lengths. All request-boundary,
canonical-schema, OpenAI placeholder-pattern,
response-envelope, and evidence-completeness checks passed with no retry,
top-up, pacing wait, or harness failure.

At clean revision `369e70c0de131bdd93f54a485d4fb0564439202c`, the
final-limit smoke returned 3/3 valid distinct bios and the one-repetition
12-case calibration returned 12/12 valid distinct bios with no catalog match.
All 15 requests used `max_output_tokens=256`, strict JSON Schema, the expected
placeholder pattern, `store=false`, reasoning effort `none`, and no sampling or
stop configuration. All 15 responses were HTTP 200 `completed`; there was no
refusal, safety result, malformed envelope, transport failure, boundary
violation, harness error, retry, top-up, or pacing wait. The final runs reported
3,756 input and 733 output tokens, zero cached/reasoning/tool tokens, a maximum
of 56 output tokens, 167 authored code points, and two sentences. The three-call
smoke additionally recorded a maximum final grounded length of 202 code points;
the historical schema-version-4 aggregate did not record grounded lengths.
Aggregate latency was 1.480 seconds at p50 and 5.591 seconds at p95/max.

At clean revision `82ecbcef51b0eaeee0319704391df7aec6d7a46b`, a
schema-version-5 one-repetition calibration returned 12/12 valid distinct bios
with no catalog match. Its 12 content-free attempt records account for all 12
grounded measurements under `maximum_approved_source_lengths_v1`: the maximum
was 408 code points against the 732-point contract, with no missing
measurement. All requests and responses passed the same security and structural
gates. The run reported 3,006 input and 589 output tokens, zero
cached/reasoning/tool tokens, a maximum of 59 output tokens, 188 authored code
points, and two sentences. Latency was 1.394 seconds at p50 and 3.060 seconds at
p95/max.

Across all six reports, 45 provider calls reported 11,286 input and 2,243 output
tokens. At the [standard published model rates used for the runs](https://developers.openai.com/api/docs/pricing)
of USD 1.00 per million input tokens and USD 6.00 per million output tokens,
estimated usage was USD 0.024744; `actual_billed_usd` remains unavailable
without a provider billing export. These runs establish live compatibility and
provide limit-calibration evidence. The 42 post-fix calls were all valid, and
the 27 calls at the final 256/512/732 limits were all valid. Each 12-case run's
zero-failure one-sided 95% Wilson upper bound is approximately 18.4%, so none is
a production reliability claim.

The Wilson bound is conditional on this fixed, equally weighted synthetic
corpus and the provider conditions during the recorded run. It is not a claim
about production traffic or all future provider states. Case order uses a
recorded `cyclic_rotation_v1` strategy to reduce systematic coupling between
one case and its position in repeated runs. Per-case and overlapping-slice
intervals are descriptive; they are not simultaneous 95% guarantees.

A passing recorded run establishes only that the observed calls satisfied the
application-owned structural and security contract under those provider
conditions. It does not establish production reliability, that the prose is
delightful, meaningfully personalized beyond the safe categories, or preferred
by people; those claims require separately approved reliability and qualitative
review protocols.

Do not commit or publish a report without a separate privacy and evidence
review. A scheduled workflow, recurring credential use, retention policy, or
production reliability gate requires separate operational approval.
