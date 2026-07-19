# Persons Finder Requirements Traceability

This is the human-owned requirements and decision ledger. Product behavior remains planned; completed infrastructure spikes are labeled independently from product delivery.

## Status vocabulary

- `accepted`: human-approved direction; delivery evidence remains `planned` until produced.
- `rejected`: excluded alternative; absence/scope evidence remains `planned`.
- `deferred`: outside mandatory core until its activation gate is approved.
- `open`: must not be resolved by assumption or encoded as a final contract.
- `planned`, `implemented`, and `verified` describe delivery truth independently of decision status.

## Challenge requirements

| ID | README requirement | Decisions | Implementation evidence | Verification evidence |
|---|---|---|---|---|
| CR01 | Implement the REST API in Kotlin/Java-preferred technology. | D07, D08 | `[planned]` | `[planned]` |
| CR02 | `POST /persons` creates a person from name, job title, hobbies, and lat/lon location. | D01-D03, D06 | `[planned]` | `[planned]` |
| CR03 | Creation generates a short, quirky bio based on job and hobbies. | D19, D21, D22 | `[planned]` | `[planned]` |
| CR04 | The AI boundary may use a real model or a mock; architecture matters more than live credentials. | D19, D20 | `[planned]` | `[planned]` |
| CR05 | `PUT /persons/{id}/location` updates the person's location. | D01, D14-D18 | `[planned]` | `[planned]` |
| CR06 | `GET /persons/nearby` accepts query latitude, longitude, and radius. | D01, D04, D12 | `[planned]` | `[planned]` |
| CR07 | Nearby returns persons including bio, sorted by distance. | D04, D12 | `[planned]` | `[planned]` |
| CR08 | AI collaboration is mandatory and `AI_LOG.md` records two or three truthful key interactions. | D27 | `[planned]` | `[planned]` |
| CR09 | Bio generation must resist prompt/instruction injection. | D05, D06, D19, D21, D22 | `[planned]` | `[planned]` |
| CR10 | `SECURITY.md` must explain sanitization, third-party PII risk, and a high-security banking design. | D21, D22 | `[planned]` | `[planned]` |
| CR11 | Code is clean and structured into controller/service/repository responsibilities. | D09, D19 | `[planned]` | `[planned]` |
| CR12 | Storage may vary; Docker Compose is preferred when a database is used. | D09-D12 | `[planned]` | `[planned]` |
| CR13 | Required documentation is a run README, `AI_LOG.md`, and `SECURITY.md`. | D21, D22, D27 | `[planned]` | `[planned]` |
| CR14 | Bonus: seed one million people and benchmark nearby. | D26 | `[planned - deferred]` | `[planned - deferred]` |
| CR15 | Bonus: apply DDD principles where they clarify the core model. | D14, D19 | `[planned]` | `[planned]` |
| CR16 | Bonus: unit-test the AI service despite nondeterministic-provider concerns. | D19, D20 | `[planned]` | `[planned]` |
| CR17 | Push the candidate's own solution to a public repository and submit its link; preserve assessment integrity. | D27, R09 | `[planned]` | `[planned]` |

## Final decision ledger

| ID | Status | Human decision | Implementation evidence | Verification evidence |
|---|---|---|---|---|
| D01 | accepted | README governs; implement only the three exact unversioned routes. | `[planned]` | `[planned]` |
| D02 | accepted | Public person and server observation IDs are UUIDs; `observationId` and `clientUpdateId` are distinct. | `[planned]` | `[planned]` |
| D03 | accepted | POST accepts `name`, `jobTitle`, nonempty `hobbies`, and nested `location.latitude`/`longitude`; duplicate names are allowed. A 201 response sets `Location` and returns `id`, `name`, `jobTitle`, `hobbies`, `bio`, `createdAt`, and `lastKnownLocationAt`, never stored coordinates. | `[planned]` | `[planned]` |
| D04 | accepted | Nearby query units are kilometres with `0 < radius <= 100`; include the exact boundary and distance zero, return every match without a silent cap, and expose D03 public fields plus one-decimal `distanceKm`. Membership/order use unrounded spheroidal distance, then public UUID. | `[planned]` | `[planned]` |
| D05 | accepted | Reject unknown fields, non-finite coordinates, latitude outside `[-90, 90]`, and longitude outside `[-180, 180]`. Return sanitized RFC 9457 Problem Details without internals: 400 `VALIDATION_FAILED`, 404 `PERSON_NOT_FOUND`, 409 `IDEMPOTENCY_KEY_REUSED`, 422 `BIO_INPUT_REJECTED`, or 503 `BIO_GENERATION_UNAVAILABLE`; D06 owns the unresolved 400/422 source boundary. | `[planned]` | `[planned]` |
| D06 | open | Name/job/hobby code-point, field/cardinality/aggregate limits and the deterministic 400-versus-422 source-policy boundary are unresolved; D04's radius limit remains accepted. | `[planned after closure]` | `[planned after closure]` |
| D07 | accepted | Use Spring Boot 4.1.0 rather than retaining the starter's unsupported 2.7.0 stack. The focused comparison passed its approved compatibility gate; container and real-PostGIS evidence remain separate gates. | `[implemented]` Boot 4.1.0 migration spike | `[verified]` clean wrapper test/build and focused runtime smoke |
| D08 | accepted | Pin Spring Boot 4.1.0, Kotlin 2.3.21, Gradle 8.14.5, Java 17, dependency-management plugin 1.1.7, and springdoc 3.0.3. The fallback is unnecessary unless later evidence falsifies this approved combination. | `[implemented]` build and wrapper pins | `[verified]` compatibility spike accepted by the human |
| D09 | accepted | Use direct Spring JDBC, explicit PostGIS SQL, and Flyway as sole DDL owner; H2/JPA are not integration truth. | `[planned]` | `[planned]` |
| D10 | accepted | The Docker-first default must run natively on arm64 and amd64, bind the backend to loopback, keep the database internal without a host port, gate readiness on Flyway/database health, persist in a named volume, and require no host PostgreSQL, OIDC, or external AI credential. | `[planned]` | `[planned]` |
| D11 | open | The exact trustworthy, immutable multi-architecture PostGIS artifact is unresolved. | `[planned after closure]` | `[planned after closure]` |
| D12 | accepted | Use PostGIS `geography(Point,4326)`, GiST, spheroidal `ST_DWithin` for boundary-inclusive membership, and spheroidal `ST_Distance` for unrounded ordering, then public UUID. Require real edge/query-plan evidence; Haversine is reference-only. | `[planned]` | `[planned]` |
| D13 | deferred | H3/S2 and road routing require measured falsification or a changed product metric. | `[planned - deferred]` | `[planned - deferred]` |
| D14 | accepted | Accepted observations are immutable normal-operation history; a one-row-per-person last-known projection is transactionally maintained and rebuildable. Each row has one canonical geographic-point source of truth, never independently divergent coordinate/geography values; D16 owns the representation/algorithm. | `[planned]` | `[planned]` |
| D15 | accepted | Preserve latitude/longitude-only PUT; additive `capturedAt` and `clientUpdateId` appear together. A changed legacy update appends; an immediate identical canonical-coordinate legacy replay returns the existing observation; a unique enriched request may append the same coordinate. Identical idempotency reuse returns the observation, conflicting reuse is 409, and late history cannot rewind the winner ordered by validated `capturedAt`, server `receivedAt`, then observation ID. A 200 identifies the accepted/existing and current last-known observations; D17 owns exact time rules/representation. | `[planned]` | `[planned]` |
| D16 | open | UUID storage/ordering and coordinate canonicalization are unresolved. | `[planned after closure]` | `[planned after closure]` |
| D17 | open | Timestamp precision/skew and final PUT representation are unresolved. | `[planned after closure]` | `[planned after closure]` |
| D18 | deferred | Retention, erasure, purge repair, post-purge replay, restore, and advanced receipt/crypto work need separate approval. | `[planned - deferred]` | `[planned - deferred]` |
| D19 | accepted | Use an application-owned `BioGenerator` and application-owned request/result/failure types. The deterministic credential-free adapter is the test/evaluator default; unknown configuration fails startup/readiness, runtime failures normalize, and silent fallback is forbidden. | `[planned]` | `[planned]` |
| D20 | deferred | A generic routing framework or one network/private adapter needs a separate gate. | `[planned - deferred]` | `[planned - deferred]` |
| D21 | accepted | Remote context is limited to literal `{{NAME}}` instead of the real name, deployment constants `en-NZ`/`NZ`, required broad job category plus mapping version, nonempty broad interests plus mapping version, and `quirky` tone. Use closed/versioned exact-match local mappings with `other`; raw name/job/employer/role/hobbies/place/coordinates/identity/tokens and person-derived transport metadata never egress. Generator output has exactly one `{{NAME}}` and one `{{JOB}}`, no unknown placeholder or forbidden region disclosure. Compose validated originals locally once as opaque values without rescanning; final output is one nonblank sentence of at most 240 Unicode code points. | `[planned]` | `[planned]` |
| D22 | open | Indexed multi-hobby is accepted in direction; exact safe-code catalogs/aliases/mapping and indexed hobby-token syntax, cardinality, and index semantics are unresolved. D21's exact-once `{{NAME}}`/`{{JOB}}` rules remain accepted. | `[planned after closure]` | `[planned after closure]` |
| D23 | deferred | Optional `macro_region` is default-off and omitted. Any separately approved future use is restricted to `North Island`/`South Island`, omitted outside or for unknown NZ coverage, and cannot surface in a bio unless explicitly requested. | `[planned - deferred]` | `[planned - deferred]` |
| D24 | accepted | Preserve compatibility seams for versioned boundaries, rebuildable area/count projections, privacy-safe aggregate maps, normalized name search, authorized last-known/history access, OIDC ownership, sharing grants, and Android foreground-location updates. Always label location `last known`, never `current`. | `[planned]` | `[planned]` |
| D25 | deferred | Auth, sharing, mobile, admin, search, map, and history implementation are outside mandatory core. | `[planned - deferred]` | `[planned - deferred]` |
| D26 | deferred | The one-million-person benchmark follows mandatory correctness and uses only project evidence. | `[planned - deferred]` | `[planned - deferred]` |
| D27 | accepted | Use concise stable agent rules, this single requirements/decision ledger, exactly three empty unlabeled AI case-study slots, ignored local journal/handoff material, and at most two schema-validated read-only reviewers. Preserve truthful evidence only, never copy another candidate's code, require explicit approval before commit/push, and add automation only after real commands pass. | `[planned]` | `[planned]` |
| R01 | rejected | Versioned routes and compatibility aliases. | `[planned - excluded]` | `[planned]` |
| R02 | rejected | Enumerable numeric public identifiers. | `[planned - excluded]` | `[planned]` |
| R03 | rejected | Keeping the starter runtime by inertia or selecting a new major solely because it is newest. | `[planned - excluded]` | `[planned]` |
| R04 | rejected | JPA/Hibernate or H2 as spatial integration truth. | `[planned - excluded]` | `[planned]` |
| R05 | rejected | Host PostgreSQL or amd64 emulation as the evaluator default. | `[planned - excluded]` | `[planned]` |
| R06 | rejected | Application or unindexed SQL Haversine as production nearby search. | `[planned - excluded]` | `[planned]` |
| R07 | rejected | A mutable-only location row or latest-from-history work on every nearby query. | `[planned - excluded]` | `[planned]` |
| R08 | rejected | Direct provider coupling, raw-input egress, network evaluator default, or silent provider fallback. | `[planned - excluded]` | `[planned]` |
| R09 | rejected | Overlapping planning docs, decorative agents, fabricated evidence, TODO-only scripts, or placeholder-green CI. | `[planned - excluded]` | `[planned]` |
| R10 | rejected | The former exactly-one unindexed hobby-token protocol. | `[planned - excluded]` | `[planned]` |

## Open-decision closure gates

| ID | Remains unresolved | Pass gate before dependent implementation | Implementation evidence | Verification evidence |
|---|---|---|---|---|
| D06 | Field/cardinality/aggregate limits and ordinary-validation versus malicious-input classification. | Human records exact, justified, testable rules before controller or bio-policy work. | `[planned after closure]` | `[planned after closure]` |
| D11 | Multi-architecture PostGIS source, maintenance owner, provenance, and immutable pin. | Native arm64 and amd64 evidence plus supply-chain review supports one human-approved artifact. | `[planned after closure]` | `[planned after closure]` |
| D16 | UUID persistence/order and canonical geographic-point algorithm. | Human accepts one schema/canonicalization contract before migrations or replay logic. | `[planned after closure]` | `[planned after closure]` |
| D17 | Timestamp parsing/precision/skew and exact PUT response fields. | Human accepts one externally testable time/response contract before PUT implementation. | `[planned after closure]` | `[planned after closure]` |
| D22 | Safe-code catalogs/aliases and indexed multi-hobby mapping/token grammar. | Human accepts versioned catalogs and a complete parser/composition protocol before bio implementation. | `[planned after closure]` | `[planned after closure]` |

## Rejected alternatives and strongest arguments

| Decision | Rejected alternative | Strongest argument in its favor | Why it remains rejected |
|---|---|---|---|
| R01 | Versioned routes or aliases | Easier future client migration and API evolution. | Unneeded surface conflicts with the exact challenge contract. |
| R02 | Numeric public IDs | Smaller indexes and simpler starter compatibility. | They are enumerable and conflict with the approved public contract. |
| R03 | Keep the starter runtime | Lowest immediate migration effort. | Runtime selection must follow support and compatibility evidence. |
| R03 | Upgrade without a spike | Avoids disposable spike work if migration is inevitable. | It would hide dependency and toolchain risk. |
| R04 | JPA/Hibernate Spatial | Familiar mapping, repository, and transaction abstractions. | Exact spatial SQL and replay/locking behavior are clearer through JDBC. |
| R04 | H2 integration tests | Fast, credential-free local startup. | They do not verify PostGIS geography, GiST, or migrations. |
| R05 | Host PostgreSQL | Low overhead for developers who already run it. | It introduces evaluator and version drift. |
| R05 | amd64 image under emulation | Uses upstream-maintained packaging without owning an image. | Native arm64 is required and evaluator emulation is unknown. |
| R06 | Application Haversine | Transparent math and database portability. | It loads/scans serving rows and duplicates metric semantics. |
| R06 | Unindexed SQL Haversine | Keeps computation near the data without an extension. | It still performs unindexed per-row distance work. |
| R07 | One mutable coordinate row | Minimal schema and transaction logic. | It destroys required accepted history. |
| R07 | Query latest history each time | Avoids projection maintenance. | Nearby repeatedly scans/orders an unbounded trail. |
| R08 | Direct provider SDK | Fastest demonstration against one live model. | Vendor concerns would leak into application contracts. |
| R08 | Raw job/hobby prompt context | More specific model output. | It violates the approved minimization boundary. |
| R08 | Network-backed evaluator default | Demonstrates live nondeterministic behavior. | It sacrifices offline reproducibility and needs network/credentials. |
| R08 | Silent provider fallback | Higher apparent availability. | It conceals outages and makes behavior nondeterministic. |
| R09 | Separate requirements and decision docs | Clearer ownership as documentation grows. | One concise combined ledger is sufficient at assessment scale. |
| R09 | Larger reviewer/orchestration fleet | More perspectives and richer records. | It risks decorative process without proportional evidence. |
| R09 | Fabricated case-study evidence | Synthetic examples can make a sparse narrative look clearer and more complete. | Presenting invented material as actual evidence is deceptive; case studies must remain empty until truthful interactions exist. |
| R09 | Placeholder CI or TODO-only scripts | Makes intended commands visible early. | A green placeholder is false evidence; automation follows known-good commands. |
| R10 | Exactly one unindexed hobby token | Simplest parser and smallest disclosure surface. | It was superseded by indexed multi-hobby direction; exact grammar remains D22-open. |

## Deferred extensions

| ID | Deferred scope | Activation gate | Evidence |
|---|---|---|---|
| D13 | H3/S2 candidate grids or road routing. | PostGIS is falsified by project measurements, or the product metric changes. | `[planned - deferred]` |
| D18 | Retention, erasure, purge repair, post-purge replay, restore, receipts, fingerprints, and crypto lifecycle. | Separate lifecycle, legal, and operational approval. | `[planned - deferred]` |
| D20 | Generic provider router or one network/private bio adapter. | Concrete provider need plus privacy/security/envelope approval. | `[planned - deferred]` |
| D23 | NZ macro-region context. | Approved boundaries, coverage, disclosure rule, and tests. | `[planned - deferred]` |
| D25 | Auth/OIDC, sharing, Android, admin boundaries/counts/maps, name search, and authorized history. | Separate product, privacy, and authorization scope. | `[planned - deferred]` |
| D26 | One-million-person benchmark. | Mandatory correctness and an approved benchmark environment. | `[planned - deferred]` |

## Future verification matrix

| Evidence ID | Covers | Planned executable or artifact evidence | Binary pass condition | Result / link |
|---|---|---|---|---|
| E01 | D01, R01 | `[planned]` HTTP route/mapping contract checks. | Only the three approved routes map; aliases do not. | |
| E02 | D02-D04, R02 | `[planned]` API and persistence contract checks. | UUID/public-field/privacy/radius/order/rounding behavior exactly matches the ledger. | |
| E03 | D05, D06 | `[planned]` Validation and sanitized Problem Details boundary checks. | Every approved boundary maps to the approved status/code without leaking values or internals. | |
| E04 | D07, D08, R03 | Clean wrapper compatibility spike under recorded exact pins. | Accepted runtime compiles and focused checks pass, or the spike stops for human fallback selection. | `[verified]` Boot 4.1.0 `clean test`, `clean build`, application startup, existing-route smoke, and OpenAPI smoke passed; human approved the migration. |
| E05 | D09, R04 | `[planned]` Fresh/reused PostGIS migration and schema inspection. | Flyway alone owns DDL and repeat startup preserves migration integrity. | |
| E06 | D10, D11, R05 | `[planned]` Native arm64/amd64 manifest, build, health, networking, and persistence evidence. | Both architectures run without emulation; DB remains internal and volume behavior is explicit. | |
| E07 | D12, D13, R06 | `[planned]` Real PostGIS edge fixtures and representative query plans. | Boundary, tie, antimeridian, pole, spheroidal order, and expected GiST behavior pass. | |
| E08 | D14-D18, R07 | `[planned]` Transaction, replay, concurrency, late-event, and projection-rebuild checks. | Observation/projection state and returned winners match the approved comparator in every case. | |
| E09 | D19, D20, R08 | `[planned]` Deterministic-adapter, configuration-failure, failure-atomicity, and dependency-boundary checks. | Normal checks use no external model; no vendor leakage, fallback, or partial state occurs. | |
| E10 | D21-D23, R10 | `[planned]` Typed-context snapshots, fake-envelope checks when applicable, parser/property checks, and final-bio boundaries. | Only approved context leaves the process and the approved token/composition contract is enforced. | |
| E11 | D24, D25 | `[planned]` Route/dependency/schema/diff scope review. | No deferred product implementation enters mandatory core. | |
| E12 | D26 | `[planned - deferred]` Reproducible seed, plans, latency distribution, memory, and write-amplification evidence. | Results meet a separately approved benchmark target on a declared environment. | |
| E13 | D27, R09 | `[planned]` Traceability, repository-status, ignore, AI-evidence, and configuration-schema review. | Harness artifacts are truthful, local material is untracked, and AI configuration is schema-valid. | |
