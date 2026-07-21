# AI Log

## Model-authored prose and live reliability

**Challenge and goal:** The remote bio path was secure, but it only asked a
model to select an application-owned template. The goal was to make the
model genuinely author quirky prose while retaining deterministic privacy,
prompt-injection, output-validation, and failure-atomicity controls, and to
design a credible way to test nondeterministic responses without asserting
exact wording.

**Prompt excerpt:** “we are just selecting templates and not creating a
prose, that's not hitting our goal for this test”

**AI proposal or contribution:** AI replaced closed template selection with
a provider-authored `bio_template`. It researched how to test a
nondeterministic model using invariant and property checks rather than exact
strings, then helped define deterministic placeholder, sentence, character,
policy, grounding, and size validation. It also proposed and implemented a
fixed synthetic corpus, plan-only mode, exact call-budget accounting,
independent runs with no retries or top-ups, and a precommitted one-sided
Wilson failure-bound gate. The evaluator recorded content-safe structural,
transport, latency, usage, and statistical evidence without retaining model
prose or profile values.

**My judgment:** I rejected the safer-looking template-selection design
because it missed the assessment's AI-generation goal. I required genuinely
nondeterministic prose without weakening security tests, retries, budget
accounting, privacy boundaries, or local grounding. I accepted structural
validity as the reliability gate while keeping output diversity diagnostic,
because novelty alone does not prove correctness. I approved a fixed
12-case-by-25-repetition plan with exactly 300 authorized calls and a 1%
upper failure-bound threshold. I also accepted that a passing run could not
be reused after a stricter output policy was added, because the intentionally
content-free evidence could not reclassify its old outputs.

**How we resolved the challenge:** The application kept raw profile values
local, gave the provider only approved broad context, validated model prose
before one-pass opaque local insertion, and performed generation before
persistence. Before paid execution, plan-only and fake-provider tests proved
corpus identity, call count, pacing, classification, statistics, sanitized
checkpointing, interruption behavior, and no-retry semantics without making
network calls. The first reliability run then exposed a deadline-bound
timeout, so the deadline increased from 10 to 15 seconds without relaxing
content limits. After review found that standalone model-authored
prompt/instruction meta-language was still accepted, that policy was
tightened and the complete 300-call protocol was rerun independently.

**Verification evidence:** The implementation and protocol are documented
in [`docs/LIVE_AI_EVALUATION.md`](docs/LIVE_AI_EVALUATION.md) and the
[content-safe final report](docs/evidence/live-ai/openai-316be4ab57c424aae4fbb5a2ecc9b43e2fb612da-12x25-passed.md).
Credential-free evaluator tests covered the corpus, plan, Wilson
calculation, runner phases, per-attempt classification, reporting,
evidence completeness, redaction, and failure/interruption paths.
`./gradlew --no-daemon liveAiEval` at revision `465c648` completed exactly
300 sends but produced 299 valid results and one timeout, correctly failing
the precommitted 1% Wilson gate. Revision `7e02d65` passed 300/300 after the
deadline change but preceded the stricter policy. The fresh final-policy run
at clean working revision `316be4ab57c424aae4fbb5a2ecc9b43e2fb612da`
completed 300/300 valid results, 298 distinct outputs, zero retries, top-ups,
fallbacks, boundary violations, or harness errors, and a one-sided 95% Wilson
upper failure bound of 0.8938%. Those source changes were later squash-merged
through [PR #9](https://github.com/ac-opensource/persons-finder/pull/9) as
commit `a5baebace9a68e05838344b9bb5462c41a69e04a`; the squash commit is the
mainline provenance, while the original working revision is not itself part
of `main` history. The final branch also passed 320 tests with zero failures
and `./scripts/verify.sh`.

**Resulting lesson:** For nondeterministic AI, correctness should be an
application-owned property contract rather than an exact string assertion.
The evaluation protocol itself should be researched, planned, and tested
offline before paid execution. Failed runs and superseded evidence must
remain visible, and any material policy change requires fresh evidence
instead of retroactively upgrading an earlier pass.

## Location algorithm research and architecture

**Challenge and goal:** The nearby feature needed an exact, scalable
location algorithm and a data model that could support both last-known
searches and future location history without overstating benchmark or
production evidence.

**Prompt excerpt:** “Replacing the current coordinates would lose useful
history. I may eventually want location trails and 'last known location'
features”

**AI proposal or contribution:** AI compared an H2/in-memory Haversine scan
with PostgreSQL/PostGIS geography queries, researched `ST_DWithin`, GiST,
spheroidal `ST_Distance`, and geography KNN behavior, and compared mutable
coordinates with immutable observations plus a serving projection.

**My judgment:** I rejected H2 and an application scan as weak spatial proof
and rejected overwriting a person's coordinates because it destroyed useful
history. I selected append-only accepted observations with a transactionally
maintained, rebuildable last-known projection. I required inclusive,
unrounded spheroidal membership and ordering, UUID tie-breaking, and kept
stored coordinates out of POST and PUT responses. The approved nearby
contract instead returns the exact canonical last-known projection point used
for membership and ordering in the loopback assessment. I also kept the
one-million-row benchmark deferred until mandatory correctness was proven.

**How we resolved the challenge:** The persistence model stores canonical
`geography(Point,4326)` observations and a one-row-per-person projection.
Nearby uses indexed `ST_DWithin(..., true)` for candidate membership and
exact `ST_Distance(..., true)` for final order. Geography KNN `<->` was not
used for final ordering because its spherical metric would not prove the
accepted spheroidal contract. The design and its falsification gates are
recorded in [ADR 0001](docs/decisions/0001-api-and-domain-contract.md) and
[ADR 0002](docs/decisions/0002-geospatial-search.md).

**Verification evidence:** `./gradlew test --console=plain` on the updated
nearby branch reported 241 tests, three intentional credential-gated skips,
and zero failures or errors. Real-PostGIS tests compared the indexed query
with a brute-force spheroidal query across boundary, pole, antimeridian,
duplicate, and tied-distance fixtures. The
[query-plan evidence](docs/evidence/nearby-query-plan.txt) used 20,000
deterministic projection rows plus two local matches and showed an index scan
on `last_known_location_projection_location_gist_idx`; it is explicitly not
presented as a benchmark. The
[historical Docker HTTP smoke](docs/evidence/nearby-docker-smoke.md) created
seven people and observed six results: the exact 10 km point was included,
10,000.001 m was excluded, and ties followed UUID order. Its then-observed
coordinate omission predates the approved nearby response correction. POST
and PUT still omit coordinates, while the corrected
[nearby/dashboard smoke](docs/evidence/nearby-location-dashboard-smoke-2026-07-20-utc.md)
verified that nearby returns the exact nested last-known projection point.
The write model merged as `606527e`; indexed nearby search merged as
`2a59488`.

**Resulting lesson:** Location correctness depends on aligning the product
metric, storage semantics, index strategy, and exact ordering algorithm.
Approximate formulas and query plans are useful research tools, but they
should not be promoted into correctness, scalability, or benchmark claims
beyond the evidence actually executed.

## Evidence-based reviewer pushback

**Challenge and goal:** A reviewer report classified several integrity,
privacy, and recovery concerns as P1/P2 defects. The goal was to avoid both
reflexive agreement and defensive dismissal by checking each claim against
the approved contract and shipped implementation.

**Prompt excerpt:** “push back from over-analyzation by a reviewer but accept
truly valid points”

**AI proposal or contribution:** AI audited the review against the README,
traceability ledger, ADRs, production code, and tests. It traced timestamp
precision through the clock, application comparator, and JDBC persistence;
checked bio failure atomicity and the remote request boundary; and separated
verified behavior from deployment hardening and operator-tooling gaps.

**My judgment:** I required evidence-based classification rather than
accepting severity labels at face value. I rejected the claimed
nanosecond-versus-microsecond winner-selection defect because the production
clock is already microsecond-ticked. I accepted narrower gaps such as the
absence of production database-role separation and a packaged projection
rebuild command. I also corrected “wire proof” to the more accurate
“application-owned request” evidence.

**How we resolved the challenge:** Each claim was assigned one of three
outcomes: disproved by code and tests, already implemented but described too
strongly, or a valid hardening/deployment gap outside the mandatory defect
severity claimed by the review. No product code was changed merely to
satisfy rhetoric; truthful limitations remained visible in the traceability
ledger.

**Verification evidence:** The audit traced
`TimeConfiguration.kt` to
`Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000))`, confirmed in
`CreatePersonService.kt` that bio generation and validation precede the
database transaction, inspected the real HTTP construction boundary in
`ProviderHttpTransport.kt`, and checked
[`docs/REQUIREMENTS_TRACEABILITY.md`](docs/REQUIREMENTS_TRACEABILITY.md) for
partial and pending claims. `./gradlew test --console=plain --rerun-tasks`
completed successfully with 230 discovered tests, 227 executed, three
credential-gated skips, and zero failures or errors. This was a read-only
review interaction, so no commit SHA applies.

**Resulting lesson:** Good review judgment is not measured by how many
findings are accepted. It requires tracing claims through the actual runtime
path, distinguishing product defects from operational hardening, correcting
overstated evidence in either direction, and preserving valid residual risks
without inflating their severity.
