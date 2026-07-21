# One-million-person benchmark

This harness benchmarks the production PostGIS nearby query against exactly
1,000,000 persons, 5,000,000 location observations, and 1,000,000 last-known
projections. It is intentionally isolated from the normal development Compose
project and database.

The scripts alone imply no result. The reviewed completed local run is
published in [`RESULTS.md`](RESULTS.md); its ignored private raw evidence is
under `benchmarks/results/<run-id>/`. A future source state requires its own
fresh seed, complete run, validation, and review before it can replace that
result.

## Requirements

- Docker Engine with Docker Compose v2
- Python 3.9 or newer, using only the standard library
- `curl` and `openssl`
- enough local disk for PostgreSQL, five million history rows, scratch oracle
  tables, indexes, and raw output

The benchmark uses the repository-owned PostGIS and application images. It
does not require a host PostgreSQL installation or expose a database port.

## Exact commands

Run commands from the repository root.

Seed the isolated database, exercise 100 application-transaction sample trails,
bulk-load the exact deterministic dataset, derive projections with the
production winner expression, and run correctness gates:

```bash
./benchmarks/bin/benchmark seed
```

Run database-only and core HTTP nearby measurements, the controlled
indexed/unindexed comparison, write-path workloads, database-only history
pagination, and query-plan capture. Every Compose `exec` is non-interactive so
it cannot consume the CSV input that drives the scenario loops:

```bash
./benchmarks/bin/benchmark run
```

Stop the benchmark project and delete only its guarded benchmark volume. The
reset relies on benchmark container and volume ownership labels, so it also
works after a build or migration failure leaves the schema incomplete. Raw
results are preserved:

```bash
./benchmarks/bin/benchmark reset
```

Inspect the fully resolved benchmark Compose configuration without starting it:

```bash
./benchmarks/bin/benchmark config
```

Verify the live stack's identity guards and prove that the SQL guard rejects a
wrong expected database:

```bash
./benchmarks/bin/benchmark verify-safety
```

`seed` refuses a non-empty benchmark database. `run` refuses a database that
has not passed correctness or has already been measured. It also refuses to
mark the database or manifest completed unless the complete raw artifact plan
passes exact validation. Repeatable runs therefore use `reset`, `seed`, then
`run`.

Creating or moving a person through the seeded dashboard changes the benchmark
database. The `run` preflight rejects changed seed cardinalities, identity, or
projection state. After an interactive dashboard demo, use `reset` and `seed`
again before measuring. `run` stops both benchmark application services before
its database-only seed and source checks, then starts only the newly verified
application image; do not modify the dashboard while `run` is active.

## Hypotheses and falsification thresholds

These are predeclared targets, not measured results:

- Correctness is falsified by any projection mismatch, missing/extra/reordered
  nearby result, distance mismatch, duplicate person, or production nearby plan
  that reads `location_observation`.
- For warm queries returning 1 through 1,000 rows, database-only p95 must be at
  most 100 ms and p99 at most 250 ms; core HTTP p95 must be at most 250 ms and
  p99 at most 500 ms.
- On the identical controlled 100,000-row subset, indexed p95 must be at least
  three times faster than deliberately unindexed p95 for the selective case.
- At equal concurrency, winning-append throughput must retain at least 50% of
  late-append throughput. Client-key and same-point no-key replays must produce
  zero observation inserts and zero projection updates.

High-cardinality uncapped results are reported as latency/cardinality curves.
They are not forced under the selective-query threshold because serialization
and transfer are required parts of the accepted API contract.

## Published measured run

[`RESULTS.md`](RESULTS.md) documents the completed
`run-20260721T080716Z` measurement against
`seed-20260721T080324Z`. The shared validator accepted all declared database,
HTTP, throughput, baseline, history, write, snapshot, environment, oracle, and
plan artifacts, and the raw-derived summarizer repeated that validation.

The result passed the selective database/HTTP thresholds, measured a 132.8x
indexed p95 improvement over the controlled unindexed baseline, verified exact
zero-delta replay behavior, and captured five current plans. Its local
high-cardinality curve identifies response cardinality/payload as the dominant
end-to-end bottleneck at roughly 50,000 rows. It makes no production capacity,
cold-cache, or cross-hardware claim.

## Isolation contract

The wrapper uses constants rather than caller-provided database targets:

| Resource | Required value |
|---|---|
| Compose file | `benchmarks/compose.yaml` |
| Compose profile | `benchmark` |
| Compose project | `persons-finder-benchmark` |
| Main database | `persons_finder_benchmark` |
| Application sample database | `persons_finder_benchmark_appsample` |
| Database user | `persons_finder_benchmark` |
| Named volume | `persons-finder-benchmark-postgres-data-v1` |
| Main application edge | `127.0.0.1:18081` |
| Sample application edge | `127.0.0.1:18082` |

The wrapper removes `COMPOSE_FILE`, `COMPOSE_PROJECT_NAME`, generic/JDBC/Spring
database URLs, and PostgreSQL connection variables from Compose invocations.
Before any SQL mutation it checks the container's Compose labels and
`current_database()`. Before volume deletion it checks the exact volume name
and Compose ownership labels. The normal `persons-finder` project,
`persons_finder` database, and `persons_finder_postgres_data` volume are never
accepted targets.

Each seed records both the Git SHA and a SHA-256 fingerprint over benchmark
scripts, production source, migrations, Dockerfiles, build files, and the
normal Compose file, including the Gradle wrapper JAR used by the application
image build. For `run`, the database starts alone so the recorded SHA and
fingerprint can be checked before either Spring application is built or allowed
to connect. The application image is then built, the fingerprint is checked
again, and only that verified image is started. `run` refuses if the source
state changes after seeding, including changes that have not been committed.

## Dataset

Bulk data is generated inside PostgreSQL with `generate_series` and deterministic
UUID/location functions. There are no ORM save loops.

- 700,000 winning locations use a global low-discrepancy distribution.
- 300,000 winning locations use six 50,000-person, 20 km dense urban clusters.
- Every person has one `INITIAL`, one `NO_KEY`, and three `CLIENT_UPDATE`
  observations.
- The corrected seed definition assigns 750,000 chronological, 200,000
  late-arrival, 40,000 equal-capture-time/different-received-time, and 10,000
  equal-capture-and-receipt-time trails.
- The projection is rebuilt with:

```sql
ORDER BY person_id, captured_at DESC, received_at DESC, id DESC
```

Correctness stops the run unless the projection matches all five million
history rows, the corrected received-at and UUID tiebreak cohorts have their
declared timestamp shapes, nearby returns at most one row per person, and every
scenario matches an unindexed brute-force oracle using the same winner rule.
Before HTTP timing starts, that brute-force oracle is exported with expected
result order, rounded distance, latitude, and longitude. Every measured HTTP
response must match that oracle and the complete nearby response shape before
its timing is recorded.

## Result layout

Each executed seed or run creates a timestamped directory:

```text
benchmarks/results/<run-id>/
  manifest.json
  raw-completeness.json
  environment.json
  database-environment.csv
  seed.log
  sample-application-path.jsonl
  correctness.csv
  cardinalities.csv
  nearby-http-oracle.csv
  nearby-db/
  nearby-http.jsonl
  nearby-http-throughput.jsonl
  baseline-db/
  writes-db/
  writes-http.jsonl
  history-db/
  plans/
```

Database timings are raw `pgbench` logs. HTTP timings are one JSON object per
request. Before the database and schema-2 run manifest are marked completed,
the wrapper requires:

- all ten nearby scenarios, each with three 200-sample database blocks and
  three 200-sample core-HTTP latency blocks;
- every declared duration-based throughput scenario, concurrency, repeat, and
  worker, with nonempty contiguous worker iterations;
- both baseline and both history workloads with three 200-sample blocks each;
- all four database write workloads with three 8,000-sample blocks each and
  all four 1,000-request HTTP write cohorts;
- the restart-first sample, six write-count snapshots, environment identity,
  exact HTTP oracle coverage, and all five JSON plans.

The deterministic `raw-completeness.json` records those validated counts. No
placeholder summary is created. After `run` has produced a completed raw
manifest, generate a concise raw-derived summary with:

```bash
./benchmarks/bin/benchmark summarize <run-id>
```

The command reruns the same exact validation, requires the recorded
completeness report to match the current raw artifacts, and refuses incomplete,
missing, or subsequently changed output. It writes only
`benchmarks/results/<run-id>/summary.md`; it does not create `RESULTS.md`.

Result directories remain private (`0700`). `pgbench` writes logs first to a
container-private `0700` temporary directory; after a workload succeeds,
`docker cp` copies them into the host-owned private result directory and the
container copy is deleted. The database container never receives write access
to the host result tree.

Run the credential-free benchmark harness checks with:

```bash
python3 -m unittest discover -s benchmarks/tests -p 'test_*.py' -v
```

Warm results use 25 warm-ups followed by three blocks of 200 measurements.
Mutating database workloads complete all four warm-up phases before the
write-count baseline is captured, so warm-up mutations are excluded from every
reported measured-block delta. Those snapshots use exact table-state counts and
WAL positions; they deliberately omit eventually flushed PostgreSQL cumulative
statistics.
Summary throughput is calculated independently for each measured repeat block,
then reported as the median block rate; gaps between separate invocations are
never included in a throughput denominator.
Restart-first observations are labelled `restart-cold`, not true cold-cache
measurements. A true cold-cache claim requires a disposable host or VM with a
documented OS cache reset.

Nearby pagination/limits are not part of the production route and are not
benchmarked as core behavior. Database history pagination is explicitly
experimental. Authenticated owner/trusted-viewer HTTP trail latency remains
deferred until that product extension exists.
