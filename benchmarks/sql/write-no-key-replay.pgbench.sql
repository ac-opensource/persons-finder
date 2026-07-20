\set person_ordinal random(850001, 899999)
BEGIN;
SELECT
    person_id,
    observation_id,
    captured_at,
    received_at,
    ST_Equals(location::geometry, location::geometry) AS same_canonical_point
FROM last_known_location_projection
WHERE person_id = benchmark.deterministic_uuid('person', :person_ordinal)
FOR UPDATE;
COMMIT;
