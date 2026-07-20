\set ON_ERROR_STOP on
\ir assert_running.sql

EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON)
WITH search AS (
    SELECT
        ST_SetSRID(
            ST_MakePoint(
                :'lon'::double precision,
                :'lat'::double precision
            ),
            4326
        )::geography AS origin,
        :'radius_km'::double precision * 1000.0 AS radius_metres
)
SELECT
    projection.person_id,
    ST_Distance(
        projection.location,
        search.origin,
        true
    ) AS distance_metres
FROM benchmark.projection_subset_indexed AS projection
CROSS JOIN search
WHERE ST_DWithin(
    projection.location,
    search.origin,
    search.radius_metres,
    true
)
ORDER BY distance_metres, projection.person_id;
