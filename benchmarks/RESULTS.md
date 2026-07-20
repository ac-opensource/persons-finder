# One-million-person benchmark results

## Status

This is an incomplete, seed-and-correctness-only result. The supplied raw
artifacts contain no completed measured benchmark run and no query plans.
Consequently, this document makes no database-latency, HTTP-latency,
throughput, indexed-versus-unindexed, plan-shape, or production-capacity
claim.

Both completed raw manifests record:

- application Git SHA:
  `2a594887558f3f456290f2ec4d62ce691ae7c8a9`
- benchmark source fingerprint:
  `5002b19b5ec155b709e014ae6d25963a269f5b5c3cef27a0e14cb279a2310be1`

The captured Git status says `benchmarks/` was untracked. The Git SHA therefore
identifies the application source but does not independently identify or
reproduce the benchmark harness. The source fingerprint is the available
identity for the harness content used by the completed seeds.

## Evidence analyzed

Only these local raw directories were analyzed:

| Raw directory | Classification | Reason |
| --- | --- | --- |
| `results/seed-20260720T153013Z/` | Invalid overall seed | The bulk seed stopped with `integer out of range`; it has no manifest, seed manifest, or full correctness output. |
| `results/seed-20260720T153509Z/` | Valid for seed correctness only | It has a seed manifest and all correctness gates passed. It has no measured-run artifacts. |
| `results/seed-20260720T154141Z/` | Valid for seed correctness only | It has a seed manifest and all correctness gates passed. It has no measured-run artifacts. |

No `run-*` directory, `cardinalities.csv`, `database-environment.csv`, pgbench
log, measured nearby HTTP log, measured write HTTP log, or `plans/` directory
was supplied.

## Measured facts

### Dataset and winner correctness

The two completed seeds reported identical deterministic counts and identity
checksums:

| Measure | `seed-20260720T153509Z` | `seed-20260720T154141Z` |
| --- | ---: | ---: |
| Persons | 1,000,000 | 1,000,000 |
| Observations | 5,000,000 | 5,000,000 |
| Last-known projections | 1,000,000 | 1,000,000 |
| Initial observations | 1,000,000 | 1,000,000 |
| No-key observations | 1,000,000 | 1,000,000 |
| Client-update observations | 3,000,000 | 3,000,000 |
| Chronological trails | 750,000 | 750,000 |
| Late-arrival trails | 200,000 | 200,000 |
| Received-at tiebreak trails | 40,000 | 40,000 |
| UUID-tiebreak trails | 10,000 | 10,000 |
| Deterministic identity checksum | `945806b90bcb4cf75bb94b2ccf656905` | `945806b90bcb4cf75bb94b2ccf656905` |

The full winner/trail-depth check compared 1,000,000 production projection
rows with 1,000,000 oracle rows in each completed seed. Both reported:

- zero duplicate-person rows;
- zero membership or distance mismatches;
- zero ordering mismatches.

Each completed seed also exercised 100 persons through the application
transaction path. Each sample produced 500 observations and 100 projections
with zero winner mismatches.

### Nearby correctness and cardinality

Both completed seeds produced the same result cardinalities. In every scenario,
the production last-known-projection query and brute-force same-winner-rule
oracle returned the same rows in the same order, with no duplicate person:

| Scenario | Radius | Rows returned | Duplicate persons | Membership/distance mismatches | Ordering mismatches |
| --- | ---: | ---: | ---: | ---: | ---: |
| Dense Auckland | 0.1 km | 1 | 0 | 0 | 0 |
| Dense Auckland | 1 km | 125 | 0 | 0 | 0 |
| Dense Auckland | 5 km | 3,125 | 0 | 0 | 0 |
| Dense Auckland | 20 km | 49,994 | 0 | 0 | 0 |
| Dense Auckland | 100 km | 50,041 | 0 | 0 | 0 |
| Global origin | 1 km | 0 | 0 | 0 | 0 |
| Global origin | 20 km | 2 | 0 | 0 | 0 |
| Global origin | 100 km | 42 | 0 | 0 | 0 |
| Antimeridian | 20 km | 2 | 0 | 0 | 0 |
| Antimeridian | 100 km | 43 | 0 | 0 | 0 |

### Application-path diagnostic timing

The seed process recorded elapsed time for its sequential 100-person
application-path correctness sample. These requests had no declared warm-up,
repeat blocks, concurrency control, or throughput interval. They are reported
only as diagnostic timing and are excluded from the benchmark latency
hypothesis.

Percentiles below use the nearest-rank method. `CV` is population standard
deviation divided by the mean.

| Seed | Operation | Samples | p50 ms | p95 ms | p99 ms | Maximum ms | CV |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `153509Z` | Create | 100 | 4.030 | 7.075 | 12.671 | 92.804 | 1.776 |
| `153509Z` | Update | 400 | 3.537 | 6.408 | 13.851 | 45.116 | 0.766 |
| `154141Z` | Create | 100 | 2.937 | 4.645 | 7.656 | 97.314 | 2.267 |
| `154141Z` | Update | 400 | 2.749 | 4.256 | 5.036 | 18.385 | 0.347 |

All 1,000 diagnostic requests across the two completed seeds returned the
expected success status.

### Captured environment

Both completed seed snapshots reported the same environment:

- macOS 26.4.1 on arm64;
- 12 logical CPUs visible to the capture process;
- Docker client 29.6.2;
- Docker Engine 29.5.2 on Linux arm64 through the `colima` context;
- Docker Compose 5.3.1;
- Python 3.9.6.

No container CPU limit, container memory limit, host memory, storage medium,
PostgreSQL configuration, PostgreSQL/PostGIS version, table/index size, cache
state, or database statistics snapshot was supplied.

## Variance

The deterministic dataset counts, identity checksum, winner correctness,
nearby cardinalities, and mismatch counts had no observed variation between
the two completed seeds.

The application-path diagnostic timings did vary. The second seed had lower
p50, p95, and p99 for both creates and updates, while its maximum create
latency was slightly higher. Each sample's first create was approximately
93-97 ms, far above its median. Because the sample was sequential and
unwarmed, this is evidence of a first-request outlier, not evidence of a
specific database or application bottleneck.

There are no repeated measured database or core HTTP blocks from which to
calculate benchmark run-to-run variance, confidence intervals, or throughput
variance.

## Query plans and bottlenecks

No query-plan artifacts were supplied. Plan changes, index use, row-estimate
quality, buffer activity, sort/spill behavior, latest-history access, and
indexed-versus-brute-force execution therefore cannot be assessed.

The failed `153013Z` seed identifies a correctness defect in that attempt:
integer overflow occurred during dense-cluster generation after 700,000
global seed locations had been inserted. It is an invalid run, not a measured
performance bottleneck.

The completed seed logs do not enable SQL timing, and no component timing or
query plan accompanies the application-path outliers. The supplied evidence
does not support attributing a bottleneck to PostgreSQL, PostGIS, the
application, serialization, networking, or container resources.

## Interpretation

The repeatable counts, checksum, and oracle comparisons support the narrow
conclusion that the completed seed data was deterministic and that nearby
correctness held for the recorded scenarios over the one-row-per-person
last-known projection.

The dense scenario demonstrates a deliberate cardinality range from 1 row at
0.1 km to 49,994 rows at 20 km. No latency data exists to show how cost changed
with that cardinality.

The diagnostic HTTP timing shift and first-request outliers show why warm-up
and repeated measured blocks are necessary. They do not establish normal
service latency or production behavior.

## Hypothesis status

| Hypothesis or threshold | Status from supplied evidence |
| --- | --- |
| Projection and nearby correctness | Not falsified by either completed seed; all recorded oracle checks passed. |
| Database nearby p95 <= 100 ms and p99 <= 250 ms for 1-1,000 rows | Not evaluated; no measured database run. |
| Core HTTP nearby p95 <= 250 ms and p99 <= 500 ms for 1-1,000 rows | Not evaluated; the application-path seed sample is not the warmed nearby benchmark. |
| Indexed query at least 3x faster than unindexed baseline | Not evaluated; no baseline timings or plans. |
| Winning-append throughput retains at least 50% of late-append throughput | Not evaluated; no write throughput run. |
| Replays insert/update zero rows | Not evaluated by measured write-delta artifacts. |

## Limitations and publication boundary

- This evidence is from a local arm64 Colima environment, not production.
- No production extrapolation is made.
- No true cold-cache run was supplied; the diagnostic first request must not be
  labelled a cold-cache measurement.
- No warm measured run, throughput run, pagination run, write-path run, or
  indexed/unindexed comparison was supplied.
- Database-only and core HTTP latency cannot be separated from these files.
- No query plan exists to prove that a measured nearby query used the
  last-known projection index or avoided a history scan.
- The benchmark harness was untracked, so the Git SHA alone is insufficient
  for reproduction.
- Secure/authenticated HTTP trail performance remains outside this evidence.

A performance result should not be published from this artifact set. A
completed raw `run-*` directory with its manifest, repeated samples,
cardinalities, database environment, write deltas, and query plans is required
before the performance hypotheses or bottlenecks can be evaluated.
