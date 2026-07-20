\set ON_ERROR_STOP on
\ir assert_running.sql

WITH selected AS (
    SELECT generate_series(900001, 901000)::bigint AS person_ordinal
)
SELECT
    'client-replay' AS scenario,
    selected.person_ordinal,
    benchmark.deterministic_uuid('person', selected.person_ordinal) AS person_id,
    observation.client_update_id,
    observation.id AS expected_observation_id,
    to_char(
        observation.captured_at AT TIME ZONE 'UTC',
        'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'
    ) AS captured_at,
    ST_Y(observation.location::geometry) AS latitude,
    ST_X(observation.location::geometry) AS longitude
FROM selected
JOIN location_observation AS observation
    ON observation.person_id = benchmark.deterministic_uuid(
        'person',
        selected.person_ordinal
    )
   AND observation.client_update_id = benchmark.deterministic_uuid(
       'client-update',
       (selected.person_ordinal - 1) * 3 + 1
   )
UNION ALL
SELECT
    'no-key-replay',
    selected.person_ordinal,
    benchmark.deterministic_uuid('person', selected.person_ordinal),
    NULL::uuid,
    projection.observation_id,
    NULL::text,
    ST_Y(projection.location::geometry),
    ST_X(projection.location::geometry)
FROM (
    SELECT generate_series(901001, 902000)::bigint AS person_ordinal
) AS selected
JOIN last_known_location_projection AS projection
    ON projection.person_id = benchmark.deterministic_uuid(
        'person',
        selected.person_ordinal
    )
ORDER BY scenario, person_ordinal;
