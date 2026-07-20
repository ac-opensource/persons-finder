# ADR 0002: Geospatial nearby search

- Status: accepted
- Date: 2026-07-20
- Last amended: 2026-07-21
- Delivery state: implemented; focused response-shape and real-PostGIS checks passed

## Context

`GET /persons/nearby` must return every last-known person within an inclusive
radius of at most 100 kilometres, order matches by unrounded distance and then
public UUID, expose one-decimal display distance, and include the canonical
last-known point as nested `location.latitude` and `location.longitude`. The
one-million-row benchmark is a bonus evidence task, not permission to invent a
different product metric or import another system's benchmark.

The existing transaction model remains unchanged: immutable accepted
`location_observation` history transactionally maintains one rebuildable
`last_known_location_projection` row per person. Nearby must query that
projection, not derive latest-per-person state from history on every request.

## Decision

### Distance metric

Membership and ordering use the same WGS84 spheroidal geography semantics:

- `ST_DWithin(last_known.location, query_point, radius_metres, true)` performs
  boundary-inclusive membership.
- `ST_Distance(last_known.location, query_point, true)` supplies the unrounded
  ranking value.
- `ST_Y(last_known.location::geometry)` and
  `ST_X(last_known.location::geometry)` expose latitude and longitude from that
  same canonical projection point; there is no independent response-coordinate
  source.
- Public ordering is that distance followed by PostgreSQL native UUID order.
- One-decimal `distanceKm` rounding happens only after membership and ordering.

Geography KNN `<->` is not used for final ordering because PostGIS documents
that geography KNN distance is spherical, while the accepted contract is
spheroidal. Mixing those metrics would make an exact-order claim false.

### Candidate index and exact containment

A Flyway-owned GiST index covers only
`last_known_location_projection.location`. `ST_DWithin` uses it to identify
candidate projection rows and then performs exact distance refinement. The
query has no application-memory scan, latest-from-history subquery, Haversine
predicate, `LIMIT`, or undocumented result cap.

For exact correctness evidence, the indexed query is compared with a
brute-force query using
`ST_Distance(location, query_point, true) <= radius_metres` and the same
spheroidal ordering. Fixtures cover poles, the antimeridian, the exact
boundary, duplicate coordinates, and tied distances.

Haversine remains an educational baseline and an approximate oracle for small,
non-boundary fixtures only. It cannot establish exact membership or ordering.

### Containment, cells, and routing

H3 and S2 cells are candidate partitioning or aggregation mechanisms, not the
accepted distance metric or exact containment predicate. They remain deferred
unless project-owned measurements falsify the PostGIS design or a distributed
cell workload is approved.

Road distance and ETA require a routable graph, travel profile, snapping, and
separate availability semantics. They answer a different product question and
remain deferred unless the human-owned metric changes.

## Consequences

- Nearby reads scale with the indexed projection and actual result cardinality,
  while projection writes pay the GiST maintenance cost.
- Every returned match discloses its exact last-known point. This is acceptable
  for the unauthenticated loopback-only assessment default, not a general
  Internet-facing deployment contract.
- An unselective query may legitimately use a sequential scan; plan shape must
  be interpreted with dataset distribution and result count.
- With no silent cap, serialization and transfer of a very large legitimate
  result remain unavoidable API costs.
- Query-plan and latency artifacts describe only their recorded dataset,
  software, architecture, and machine. They are not general performance claims.
- The observation, idempotency, transaction, and projection-rebuild rules from
  ADR 0001 are unchanged.

## Falsification gates

Reconsider the candidate strategy only if project-owned representative
measurements miss an approved target after statistics and index health are
verified, projection-write cost misses an approved target, or the workload
requires distributed cell partitioning. Reconsider the distance metric only
through a new human product decision, such as changing nearby to road
distance/ETA. A result-volume bottleneck requires an explicit API decision and
must not be hidden by truncation.

## Primary references

- [PostGIS 3.6 `ST_DWithin`](https://postgis.net/docs/manual-3.6/en/ST_DWithin.html)
- [PostGIS 3.6 `ST_Distance`](https://postgis.net/docs/manual-3.6/en/ST_Distance.html)
- [PostGIS 3.6 spatial-query guidance](https://postgis.net/docs/manual-3.6/en/using_postgis_query.html)
- [PostGIS geography KNN `<->`](https://postgis.net/docs/manual-dev/en/geometry_distance_knn.html)
