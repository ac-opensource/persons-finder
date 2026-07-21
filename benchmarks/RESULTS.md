# One-million-person benchmark results

## Status and evidence identity

This is a completed local measured run, not a production-capacity result. The
shared validator accepted every declared workload and raw artifact before the
run manifest was marked complete, and the raw-derived summarizer repeated that
validation before producing its summary.

| Evidence | Value |
| --- | --- |
| Seed | `seed-20260721T080324Z` |
| Measured run | `run-20260721T080716Z` |
| Application Git SHA | `7a806cec1ddeadc9f9a56030c2b7a2f84dde3b07` |
| Full source fingerprint | `0afd592bb8d93e6ac66bfc1f682eb00978f1a151c07e24019585e56056af94a2` |
| Run manifest | schema 2, `completed: true` |
| Dataset checksum | `945806b90bcb4cf75bb94b2ccf656905` |

The fingerprint covers benchmark scripts, production source, migrations,
Dockerfiles, build files, Compose configuration, and the Gradle wrapper JAR.
It also covers the Python 3.9 PostgreSQL-timestamp compatibility fix exercised
by this run.

## Environment

- macOS 26.4.1 on arm64, with 12 logical CPUs visible;
- Docker Engine 29.5.2 on Linux arm64 through Colima;
- Docker Compose 5.3.1 and Python 3.9.6;
- PostgreSQL 17.10 and PostGIS 3.6.4;
- PostgreSQL defaults included 128 MiB `shared_buffers`, 4 MiB `work_mem`,
  `random_page_cost=4`, JIT enabled, and I/O timing disabled; and
- relation sizes were 239 MiB for `person`, 1,423 MiB for
  `location_observation`, and 202 MiB for
  `last_known_location_projection`.

No container CPU or memory limit was applied. This environment is suitable for
local comparison only; it is not a production sizing environment.

## Workload completeness

The accepted raw-completeness report contains:

| Workload | Completed evidence |
| --- | ---: |
| Nearby database latency | 10 scenarios x 3 repeats x 200 = 6,000 samples |
| Nearby core HTTP latency | 10 scenarios x 3 repeats x 200 = 6,000 samples |
| Nearby HTTP throughput | 18 x 60-second blocks; 821,382 requests |
| Indexed/unindexed baseline | 2 workloads x 3 x 200 = 1,200 samples |
| Experimental history pagination | 2 workloads x 3 x 200 = 1,200 samples |
| Database writes | 4 workloads x 3 x 8,000 = 96,000 samples |
| Core HTTP writes | 4 cohorts x 1,000 = 4,000 requests |
| Query plans | 5 JSON `EXPLAIN (ANALYZE, BUFFERS)` artifacts |
| State validation | 6 write-count snapshots plus exact artifact hashes |

The figures below use three warmed repeat blocks. Latency percentiles combine
the 600 samples for a scenario; throughput is the median of the three block
rates. There is only one completed full run, so these data show within-run
repetition, not run-to-run confidence intervals.

## Seed and correctness

The sole retained seed contained exactly:

| Measure | Count |
| --- | ---: |
| Persons | 1,000,000 |
| Observations | 5,000,000 |
| Last-known projections | 1,000,000 |
| Initial / no-key / client-update observations | 1,000,000 / 1,000,000 / 3,000,000 |
| Chronological / late-arrival trails | 750,000 / 200,000 |
| Received-at / UUID tiebreak trails | 40,000 / 10,000 |

The full projection check compared all 1,000,000 production winners with the
oracle and found zero duplicate-person, winner, trail-depth, membership,
distance, or ordering mismatches. All ten nearby scenarios also matched the
brute-force spheroidal oracle exactly. The 100-person application-transaction
sample produced 500 observations and 100 projections with zero winner
mismatches.

## Nearby latency and cardinality

### Database-only

| Scenario | Rows | p50 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: |
| Dense Auckland, 0.1 km | 1 | 0.882 | 1.248 | 2.391 |
| Dense Auckland, 1 km | 125 | 1.595 | 2.154 | 3.263 |
| Dense Auckland, 5 km | 3,125 | 17.005 | 21.725 | 29.515 |
| Dense Auckland, 20 km | 50,001 | 180.895 | 196.013 | 230.664 |
| Dense Auckland, 100 km | 50,041 | 108.156 | 121.292 | 165.896 |
| Global origin, 1 km | 0 | 0.489 | 0.697 | 0.884 |
| Global origin, 20 km | 2 | 0.571 | 0.904 | 1.062 |
| Global origin, 100 km | 42 | 0.822 | 1.172 | 1.371 |
| Antimeridian, 20 km | 2 | 0.557 | 0.777 | 0.955 |
| Antimeridian, 100 km | 43 | 0.722 | 0.974 | 1.197 |

### Core HTTP

| Scenario | Rows | Median response | p50 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Dense Auckland, 0.1 km | 1 | 395 B | 2.788 | 3.880 | 6.135 |
| Dense Auckland, 1 km | 125 | 49,620 B | 2.957 | 3.433 | 3.775 |
| Dense Auckland, 5 km | 3,125 | 1,240,488 B | 27.868 | 30.226 | 31.412 |
| Dense Auckland, 20 km | 50,001 | 19,885,361 B | 375.081 | 413.853 | 429.993 |
| Dense Auckland, 100 km | 50,041 | 19,901,263 B | 375.604 | 419.953 | 453.672 |
| Global origin, 1 km | 0 | 2 B | 0.829 | 1.274 | 1.751 |
| Global origin, 20 km | 2 | 797 B | 1.042 | 1.516 | 2.442 |
| Global origin, 100 km | 42 | 16,679 B | 1.515 | 2.164 | 2.612 |
| Antimeridian, 20 km | 2 | 792 B | 2.613 | 3.981 | 8.344 |
| Antimeridian, 100 km | 43 | 17,149 B | 3.116 | 4.543 | 7.429 |

For the predeclared 1-1,000-row band, the worst observed database result was
p95 2.154 ms / p99 3.263 ms, and the worst core HTTP result was p95 4.543 ms /
p99 8.344 ms. Both are below the declared 100/250 ms database and 250/500 ms
HTTP thresholds.

## Nearby HTTP throughput

| Scenario | Rows | Concurrency | Median requests/s | p50 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Dense Auckland, 0.1 km | 1 | 1 | 735.95 | 1.268 | 1.684 | 1.996 |
| Dense Auckland, 0.1 km | 1 | 8 | 2,355.30 | 3.118 | 5.469 | 7.280 |
| Dense Auckland, 1 km | 125 | 1 | 276.00 | 3.312 | 4.090 | 5.033 |
| Dense Auckland, 1 km | 125 | 8 | 1,181.97 | 6.260 | 9.810 | 12.784 |
| Dense Auckland, 20 km | 50,001 | 1 | 2.17 | 357.482 | 398.964 | 406.600 |
| Dense Auckland, 20 km | 50,001 | 8 | 4.30 | 1,723.021 | 1,932.118 | 2,098.967 |

Concurrency 8 improved the 50,001-row rate by only 1.98x while increasing p95
latency to 1.93 seconds. These local rates describe this machine and payload;
they are not service capacity or a safe operating limit.

## Indexed versus unindexed baseline

The controlled comparison used identical 100,000-row subsets and the same
selective query.

| Variant | p50 ms | p95 ms | p99 ms | Median requests/s | Plan |
| --- | ---: | ---: | ---: | ---: | --- |
| GiST indexed | 0.512 | 0.699 | 0.991 | 1,699.51 | GiST index scan |
| Deliberately unindexed | 86.659 | 92.812 | 110.411 | 11.48 | parallel sequential scan |

The indexed p95 was 132.8x faster than the unindexed p95, exceeding the
predeclared 3x threshold.

## Write paths and replay deltas

### Database latency and throughput

| Workload | Samples | p50 ms | p95 ms | p99 ms | Median writes/s |
| --- | ---: | ---: | ---: | ---: | ---: |
| Winning append | 24,000 | 1.440 | 4.605 | 8.053 | 4,184.72 |
| Late append | 24,000 | 1.278 | 4.437 | 7.823 | 4,905.39 |
| Client-key replay | 24,000 | 0.655 | 1.273 | 3.947 | 9,955.50 |
| Same-point no-key replay | 24,000 | 0.799 | 1.489 | 4.438 | 8,463.49 |

Winning-append throughput retained 85.3% of late-append throughput, above the
declared 50% floor.

### Exact measured-block state deltas

| Phase | Observation delta | Client-update delta | Winning-projection delta |
| --- | ---: | ---: | ---: |
| 24,000 winning appends | +24,000 | +24,000 | +24,000 |
| 24,000 late appends | +24,000 | +24,000 | 0 |
| 24,000 client-key replays | 0 | 0 | 0 |
| 24,000 same-point no-key replays | 0 | 0 | 0 |
| HTTP: 1,000 each of four write cohorts | +2,000 | +2,000 | +1,000 |

The HTTP delta is exactly the 1,000 winning plus 1,000 late appends; both
1,000-request replay cohorts inserted no observation and changed no
projection. All 4,000 HTTP writes returned the expected success status.

## Current query-plan set

The run captured five current plans:

- production nearby at dense Auckland / 1 km: GiST scan on
  `last_known_location_projection_location_gist_idx`, then person primary-key
  lookups; 125 rows; no `location_observation` scan;
- indexed 100k baseline: GiST scan on
  `projection_subset_indexed_location_gist_idx`;
- unindexed 100k baseline: parallel sequential scan;
- first history page: index scan on
  `location_observation_client_update_unique`; and
- next history page: `history_cursor_person_id_idx` plus
  `location_observation_client_update_unique`.

The captured production nearby plan executed in 3.933 ms and satisfied the
correctness requirement that nearby read the maintained projection rather than
derive latest state from history. History pagination remains experimental and
is not a core HTTP claim.

## Interpretation and hypothesis outcomes

| Predeclared hypothesis | Outcome |
| --- | --- |
| Projection and nearby correctness | Passed every seed, oracle, shape, and measured-response check. |
| Database nearby p95 <= 100 ms and p99 <= 250 ms for 1-1,000 rows | Passed; worst p95/p99 were 2.154/3.263 ms. |
| Core HTTP nearby p95 <= 250 ms and p99 <= 500 ms for 1-1,000 rows | Passed; worst p95/p99 were 4.543/8.344 ms. |
| Indexed p95 at least 3x faster than unindexed | Passed at 132.8x. |
| Winning append retains at least 50% of late-append throughput | Passed at 85.3%. |
| Replays insert zero observations and update zero projections | Passed for database and HTTP replay cohorts. |
| Production nearby avoids history | Passed in the captured plan. |

The main measured bottleneck is uncapped response cardinality and payload size.
At roughly 50,000 rows, database p50 was 108-181 ms while core HTTP p50 was
about 375 ms and each response was about 19.9 MB. This supports the conclusion
that response construction, JSON serialization, and local transfer together
add substantial end-to-end cost beyond the indexed spatial query. The data do
not isolate those three application/transport components from one another.

The 50,001-row concurrency result also shows saturation: eight clients nearly
doubled throughput rather than increasing it eightfold, while p95 latency rose
to 1.93 seconds. This is a local bottleneck observation, not evidence of a
production scaling limit.

## Limitations and publication boundary

- This is one completed run on a local arm64 Colima environment; no run-to-run
  variance or hardware comparison is available.
- Restart-first evidence is not a proven cold filesystem-cache measurement.
- No CPU, memory, storage, network, or production concurrency model was
  controlled beyond the recorded environment.
- The production route is intentionally uncapped; high-cardinality values are
  a latency/cardinality curve, not a selective-query SLA failure.
- Authenticated owner/trusted-viewer history HTTP remains deferred.
- No production throughput, capacity, SLO, instance-count, or cost claim is
  made from this result.
