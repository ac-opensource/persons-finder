# Live AI Evaluation

## Scope

The live evaluator measures whether one explicitly selected provider/model
reliably authors structurally valid bio prose from the application-owned safe
category request. A valid result is one to three sentences with the exact
grounding placeholders, within the deterministic policy and length bounds. The
final policy also rejects standalone model-authored `prompt`, `prompts`,
`instruction`, or `instructions` before persistence. The evaluator does not use
exact-string assertions and does not grade subjective tone, creativity, or
relevance.

The calibrated ceiling is 256 provider output tokens. The application
independently accepts at most 512 model-authored code points and 732 final
grounded code points. Across all reviewed paid evidence, the observed maxima
were 70 output tokens, 222 authored code points, 442 final grounded code points,
and two sentences. The selected provider and authored limits therefore retain
more than 3.6x and 2.3x the corresponding observed maxima. The final limit also
reserves the existing 220-code-point maximum for locally grounded source values.

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

Choose exactly one provider and its exact approved model. The approved OpenAI
reliability protocol uses 25 complete passes over the 12-case corpus. Plan mode
is credential-free and makes no provider call:

```bash
LIVE_AI_PROVIDER=openai \
LIVE_AI_EVAL_REPETITIONS=25 \
LIVE_AI_EVAL_MAX_CALLS=300 \
LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=0 \
LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=0.01 \
OPENAI_LIVE_MODEL='gpt-5.6-luna' \
./gradlew --no-daemon liveAiEval --args='--plan'
```

Plan mode validates the corpus, derives the current Git revision and
application-request fingerprints, checks the maximum-call authorization and
pacing configuration, and prints only sanitized metadata. Its output includes
`pacing_strategy`, `minimum_call_interval_millis`, and the derived
`configured_minimum_call_start_span_millis`. The approved unpaced OpenAI plan
must report exactly 12 cases, 25 repetitions, 300 planned and maximum calls,
and a zero configured start span. Plan mode does not require a provider
credential or the live-run confirmations.

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
precommit the resulting output ceiling and aggregate spend bound. The approved
OpenAI 12-by-25 reliability command is:

```bash
LIVE_AI_PROVIDER=openai \
LIVE_AI_EVAL_REPETITIONS=25 \
LIVE_AI_EVAL_MAX_CALLS=300 \
LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=0 \
LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=0.01 \
RUN_LIVE_AI_TESTS=true \
OPENAI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED=true \
LIVE_AI_AUTOMATIC_TELEMETRY_DISABLED_CONFIRMED=true \
LIVE_AI_APPLICATION_REQUEST_INSPECTION_CONFIRMED=true \
OPENAI_LIVE_MODEL='gpt-5.6-luna' \
OPENAI_API_KEY="$(<.secrets/openai-api-key)" \
./gradlew --no-daemon liveAiEval
```

The provider-specific approval means that the human owner accepts provider
retention, abuse monitoring, human review, and product-improvement use, as
applicable, for only the fixed synthetic smoke fixtures, versioned aggregate
corpus, and fixed application-owned prompt/schema used by this evaluation. It
does not assert that provider logging is disabled. It does not authorize
production or customer-derived content, and it cannot satisfy the separate
production privacy, retention, residency, and subprocessor review.

The approved OpenAI investigation and 300-call reliability run have a hard
cumulative provider-usage cost ceiling of USD 50. Fixed reruns are permitted
when preceding sanitized evidence exposes a material implementation or
compatibility defect; each run keeps its precommitted call budget and never
tops up. Do not combine providers or select a provider after comparing
aggregate results. Anthropic uses the corresponding `ANTHROPIC_*` variables if
separately selected and approved. The evaluator reads only the selected
provider credential and constructs only that provider client. For paid OpenAI
execution, explicitly set
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
control; it is the approved value for the fixed paid OpenAI reliability
protocol. Any resulting rate-limit outcome still counts as a failure and is
never retried.

The three-call live adapter smoke is compatibility and request-boundary
preflight. It is not pooled into the 300 aggregate attempts. Let the applicable
quota window clear between smoke and aggregate runs.

## Reliability gate

The approved target is an overall one-sided 95% Wilson upper failure bound of
1%. Every run precommits its provider, model, call budget, pacing, and spend
bound:

```bash
LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=0.01
```

At 300 attempts, zero failures has an upper bound of approximately 0.8938% and
passes. One failure has an upper bound of approximately 1.4801% and fails.
Thus this exact 12-case-by-25-repetition protocol accepts no observed failure
and never adds replacement calls. A zero-failure per-case sample contains only
25 observations and has an approximately 9.7654% upper bound, so the 0.8938%
claim applies only to the overall equally weighted fixed corpus.

The target remains an explicit environment value rather than a hidden default.
Hard request-boundary violations and harness errors always fail the command
after the sanitized report is written. Distinct-prose and deterministic-catalog
match counts are diagnostics only; they do not affect the reliability gate.

## Report and interpretation

The evaluator writes:

```text
build/reports/live-ai-smoke/report.json
build/reports/live-ai-eval/report.json
build/reports/live-ai-eval/report.md
.agents/evidence/live-ai-smoke/<full-revision>/<timestamp>-<provider>-<model-hash>-report.json
.agents/evidence/live-ai-eval/<full-revision>/<timestamp>-<provider>-<model-hash>-report.json
.agents/evidence/live-ai-eval/<full-revision>/<timestamp>-<provider>-<model-hash>-report.md
```

The evaluator writes interruption-safe sanitized JSON checkpoints only to
ignored scratch/archive paths. It also renders the same sanitized facts into a
human-readable Markdown checkpoint. The ignored archive survives `clean` and
prevents a paid run from being lost before review; every destination is
write-tested before the first provider call. After a privacy and
evidence-integrity review, copy only the final Markdown report to
`docs/evidence/live-ai/` with the provider, full code revision, and run shape in
the filename. Raw JSON, Gradle HTML/JUnit output, prompts, and provider content
remain untracked.

The current harness emits aggregate report schema version 6. With the live
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
- per-round, per-case, per-slice, and overall observed failure rates;
- one-sided 95% Wilson upper failure bounds;
- latency p50, p95, and maximum;
- valid-prose, distinct-valid-prose, and deterministic-catalog-match counts;
- one content-free record per completed attempt containing its sequence,
  one-based round and slot, safe corpus case ID, normalized result, opaque
  per-run output-equivalence ID, sentence and length metrics, catalog-match
  flag, and final grounded code-point count when valid;
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

The two historical schema-version-4 aggregate checkpoints were not backfilled
and did not contain grounded-length fields; absence means unknown, not zero.
Schema version 5 added the already required composition measurement against
synthetic strings at the maximum approved source lengths. Version 6 adds
one-based round/slot evidence, opaque output-equivalence classes, and
per-round aggregates. These are structural worst-case measurements, not
customer profile lengths.

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

## Recorded OpenAI evidence

The historical diagnostic, limit-calibration, and reliability runs are
summarized below.
Their machine-oriented JSON checkpoints are intentionally ignored and
untracked. The stricter final 12-case-by-25-repetition result is preserved as a
reviewed, human-readable, content-safe
[Markdown report](evidence/live-ai/openai-316be4ab57c424aae4fbb5a2ecc9b43e2fb612da-12x25-passed.md).
The earlier
[post-deadline report](evidence/live-ai/openai-7e02d65dc2895e6e618365021053c96f78ec8efb-12x25-passed.md)
remains available as historical evidence.

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

Across all six historical diagnostic and calibration reports, 45 provider calls
reported 11,286 input and 2,243 output tokens. At the
[standard published model rates used for the runs](https://developers.openai.com/api/docs/pricing)
of USD 1.00 per million input tokens and USD 6.00 per million output tokens,
estimated usage was USD 0.024744; `actual_billed_usd` remains unavailable
without a provider billing export. These runs establish live compatibility and
provide limit-calibration evidence. The 42 post-fix calls were all valid, and
the 27 calls at the final 256/512/732 limits were all valid. Each historical
12-case run's zero-failure one-sided 95% Wilson upper bound is approximately
18.4%, so none alone is a reliability result.

### First 300-call aggregate and timeout finding

At clean revision `465c648aebd2445e4f811700a35770af6befa425`, the first
independent 12-case-by-25-repetition aggregate completed exactly 300 calls and
300 delegated HTTP sends. It produced 299 valid results and one timeout, with no
retry, top-up, provider fallback, invalid output, policy rejection, rate limit,
unavailability, cancellation, hard-boundary violation, or harness error. The
single timeout occurred with a 9,999 ms request timeout and was recorded at
10,001 ms by the transport. The resulting one-sided 95% Wilson upper failure
bound was 1.480096%, so the run correctly failed the 1% reliability gate.

The 299 successful results had a 2.022-second p95 and 5.825-second maximum
transport latency. The run recorded maxima of 65 output tokens, 222 authored
code points, and 442 final grounded code points. Provider usage was 74,899 input
tokens and 14,529 output tokens, for an estimated USD 0.162073 at the stated
standard rates.

That evidence showed a deadline-bound transport failure rather than a prose,
policy, schema, or request-boundary failure. The shared generation deadline was
therefore increased from 10,000 ms to 15,000 ms. The fresh protocol binds that
15,000 ms application deadline into its plan and provenance; per-request
timeouts use the remaining deadline and may be one millisecond lower. This
change does not relax the 256-token, 512-authored-code-point,
732-final-code-point, sentence, placeholder, ASCII, policy, or grounding gates.

### Post-deadline 300-call aggregate

At clean revision `7e02d65dc2895e6e618365021053c96f78ec8efb`, the fresh
12-case-by-25-repetition aggregate again completed exactly 300 calls and 300
delegated HTTP sends. All 300 responses were HTTP 200 `completed` and all 300
passed the deterministic application contract. There were 293 distinct valid
outputs and zero failures, retries, top-ups, provider fallbacks, hard-boundary
violations, or harness errors. The one-sided 95% Wilson upper failure bound was
0.893787%, which passes the precommitted 1% overall gate.

Application latency was 1.388158 seconds at p50, 2.275301 seconds at p95, and
5.994904 seconds at maximum; maximum transport latency was 5,993 ms. The run
used the configured 15,000 ms generation deadline and recorded a 14,999 ms
maximum request timeout. It reported 75,150 input tokens and 14,644 output
tokens, for an estimated USD 0.163014. Observed maxima were 63 output tokens,
199 authored code points, 419 final grounded code points, and two sentences,
against the precommitted 256/512/732 limits.

Codex review then identified a policy gap: standalone model-authored
`prompt`, `prompts`, `instruction`, or `instructions` were not independently
rejected. Revision `7e02d65dc2895e6e618365021053c96f78ec8efb` passed its
recorded contract, but its content-safe report intentionally retained no prose,
so its 300 outputs could not be reclassified against the stricter policy.

### Final 300-call aggregate under the stricter policy

After adding the deterministic standalone-word rejection, clean revision
`316be4ab57c424aae4fbb5a2ecc9b43e2fb612da` reran the exact same
12-case-by-25-repetition protocol. This was a fresh reliability run, not another
calibration, a retry, or a top-up. It completed exactly 300 calls and 300
delegated HTTP sends. All 300 responses were HTTP 200 `completed` and all 300
passed the stricter application contract. There were 298 distinct valid
outputs and zero failures, policy rejections, retries, top-ups, provider
fallbacks, hard-boundary violations, or harness errors. The one-sided 95%
Wilson upper failure bound was 0.893787%, which passes the precommitted 1%
overall gate.

Application latency was 1.329511 seconds at p50, 2.704556 seconds at p95, and
6.404460 seconds at maximum; maximum transport latency was 6,402 ms. The run
used the configured 15,000 ms generation deadline. It reported 75,150 input
tokens and 14,595 output tokens, for an estimated USD 0.162720. Observed maxima
were 64 output tokens, 196 authored code points, 416 final grounded code points,
and two sentences, against the precommitted 256/512/732 limits.

All three 300-call aggregates are independent fixed runs. Their samples,
failure counts, and Wilson statistics are not pooled. The final passing result
gates only revision `316be4ab57c424aae4fbb5a2ecc9b43e2fb612da` under its
recorded provider conditions and stricter policy. Including the six historical
diagnostic/calibration reports and all three aggregates, the paid evidence
comprises 945 calls, 236,485 input tokens, and 46,011 output tokens, with
estimated usage of USD 0.512551. `actual_billed_usd` remains unavailable
without a provider billing export.

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
