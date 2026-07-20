\set ON_ERROR_STOP on
\ir assert_running.sql

WITH expected_unranked AS (
    SELECT
        scenario.scenario_id,
        person.id,
        ST_Y(oracle.location::geometry) AS latitude,
        ST_X(oracle.location::geometry) AS longitude,
        ST_Distance(
            oracle.location,
            ST_SetSRID(
                ST_MakePoint(scenario.longitude, scenario.latitude),
                4326
            )::geography,
            true
        ) AS distance_metres
    FROM benchmark.nearby_scenario AS scenario
    JOIN benchmark.oracle_last_known AS oracle
        ON ST_Distance(
            oracle.location,
            ST_SetSRID(
                ST_MakePoint(scenario.longitude, scenario.latitude),
                4326
            )::geography,
            true
        ) <= scenario.radius_km * 1000.0
    JOIN person ON person.id = oracle.person_id
),
expected AS (
    SELECT
        scenario_id,
        row_number() OVER (
            PARTITION BY scenario_id
            ORDER BY distance_metres, id
        ) AS result_order,
        id,
        latitude,
        longitude,
        round((distance_metres / 1000.0)::numeric, 1) AS distance_km
    FROM expected_unranked
)
SELECT
    scenario_id,
    result_order,
    id,
    latitude,
    longitude,
    distance_km
FROM expected
ORDER BY scenario_id, result_order;
