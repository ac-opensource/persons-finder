# Nearby default-stack HTTP smoke

- Captured: 2026-07-20T07:19:33Z
- Environment: native arm64 Docker runtime
- Scope: one functional smoke run against the default assessment-local Compose stack
- Result: passed

## Contract provenance

This artifact preserves what was observed on its capture date. Its response-
shape assertions predate the 2026-07-21 contract correction that added nested
canonical last-known `location` to every nearby item. The membership, ordering,
loopback, migration, logging, and teardown observations remain historical
evidence; the eight-field count and coordinate-omission observations below do
not verify the corrected response shape. That shape requires fresh executable
evidence against the amended implementation.

## Procedure

The application image was built from the current source inside Docker. A fresh
Compose project was started with the backend published only on loopback, and
Compose waited for the configured health checks:

```bash
PERSONS_FINDER_PORT=18082 \
  docker compose -p persons-finder-pr8-reverify-20260720 build

PERSONS_FINDER_PORT=18082 \
  docker compose -p persons-finder-pr8-reverify-20260720 up --detach --wait
```

Seven synthetic people were then created through `POST /persons`. Their
locations were constructed with PostGIS spheroidal `ST_Project` at these target
distances from the query point:

```text
0 m
0 m
5,000 m
5,000 m
9,999.999 m
10,000 m
10,000.001 m
```

The required route was called through the loopback-published backend:

```http
GET /persons/nearby?lat=-36.8485&lon=174.7633&radius=10
```

## Observed result

- All seven `POST /persons` requests returned `201 Created`.
- The nearby request returned `200 OK` and a bare array of six people.
- Flyway reported successful V1, V2, and V3 migrations.
- Both zero-distance people were included.
- Both 5 km people were included and their equal-distance order followed
  ascending public UUID.
- The 9,999.999 m person was included.
- The exact 10,000 m boundary person was included.
- The 10,000.001 m person was excluded.
- Every nearby item contained the eight approved public fields, including the
  display-rounded `distanceKm`; no latitude or longitude was returned.

The synthetic input coordinates were retained by the local verification harness
and joined to returned person IDs only to visualize the result. They were not
obtained from or added to the public nearby response.

The stack was stopped without deleting its named database volume:

```bash
PERSONS_FINDER_PORT=18082 \
  docker compose -p persons-finder-pr8-reverify-20260720 down
```

The application and database logs contained none of the synthetic profile
values or coordinates checked by the verification command. Teardown retained
the named database volume.

## Limits

This is functional evidence for the current arm64 default-stack path, not a
benchmark, production claim, native-amd64 runtime result, or continuously
executed CI check. Exact membership and ordering are independently covered by
the real-PostGIS indexed-versus-brute-force integration tests.
