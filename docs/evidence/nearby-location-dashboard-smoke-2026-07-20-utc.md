# Nearby location and dashboard smoke — 2026-07-20 UTC

Executed on 2026-07-21 in Asia/Manila (UTC+08:00), which was
2026-07-20 UTC. The UTC date is used in this artifact's filename so repository
and GitHub timestamps share the same time basis.

## Scope

This evidence covers the corrected `GET /persons/nearby` response location and
the loopback dashboard's dense-map and floating-detail behavior. It was
executed against the then-current dashboard implementation on
`feature/person-dashboard` and the existing isolated benchmark database. That
implementation was later squash-merged through
[PR #10](https://github.com/ac-opensource/persons-finder/pull/10) as commit
`2f5a7c29835d30d0ec946dafce6128b457701c5d`; the smoke predates that squash
commit and remains scoped to the execution state described here.

No `POST` or `PUT` request was made during this verification.

## Executed checks

- Focused `FindNearbyControllerContractTest` and
  `JdbcNearbyPersonRepositoryTest`: passed.
- Full `./gradlew test --console=plain`: passed.
- `node --check src/main/resources/static/assets/dashboard.js`: passed.
- Dashboard location validation/normalization CommonJS smoke: passed.
- `git diff --check`: passed.
- `docker compose build app`: passed.
- Normal app and benchmark app containers were recreated from the corrected
  image and reached healthy readiness.

An internal HTTP smoke against the benchmark application used:

```text
GET /persons/nearby?lat=-36.8485&lon=174.7633&radius=1
```

It returned `200 OK`, 125 items, and nine fields per item. Every inspected item
included exactly nested `location.latitude` and `location.longitude`.

## Browser verification

The loopback benchmark dashboard at `http://127.0.0.1:18081/` loaded the
versioned dashboard assets and reported `API connected`.

For the default Auckland 10 km query:

- the API and dashboard reported 12,504 nearby people;
- the shared Leaflet canvas completed 12,504 read-only nearby markers;
- only one 250-result page was mounted at a time;
- sticky **Previous 250** and **Next 250** controls replaced the mounted page
  instead of growing a long DOM list;
- the first result displayed its returned coordinates rather than a withheld
  location message.

Selecting the first result opened a nonmodal `position: fixed` detail window at
the top-right of the viewport while the result list remained independently
long. The seeded person had no move form. Clicking a visible canvas point
selected a different seeded person and opened the same floating detail without
opening the create dialog. At the mobile breakpoint the detail became a bottom
sheet with 10 px left, right, and bottom insets. Browser warning/error logs were
empty.

## Benchmark database postcondition

The live database is not at the original seed cardinalities:

```text
persons=1000001
observations=5000005
projections=1000001
```

The extra row is a manually created `Sample` / `Carpenter` profile timestamped
`2026-07-20 16:22:34.671026+00`, with five observations. This activity predates
the final read-only browser verification and matches the manually exercised
dashboard profile. It was not deleted or reset.

`benchmark.control` still records `correctness_passed` and the historical
correctness table still contains 11 passing rows, but those records describe
the seed gate before the manual writes. They must not be used to claim that the
current live database remains a pristine one-million-person benchmark seed.
