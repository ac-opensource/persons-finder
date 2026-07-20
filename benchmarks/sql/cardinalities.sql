\set ON_ERROR_STOP on
\ir assert_running.sql

SELECT
    scenario.scenario_id,
    scenario.distribution,
    scenario.latitude,
    scenario.longitude,
    scenario.radius_km,
    count(projection.person_id) AS rows_returned,
    count(DISTINCT projection.person_id) AS distinct_persons
FROM benchmark.nearby_scenario AS scenario
LEFT JOIN last_known_location_projection AS projection
    ON ST_DWithin(
        projection.location,
        ST_SetSRID(
            ST_MakePoint(scenario.longitude, scenario.latitude),
            4326
        )::geography,
        scenario.radius_km * 1000.0,
        true
    )
GROUP BY
    scenario.scenario_id,
    scenario.distribution,
    scenario.latitude,
    scenario.longitude,
    scenario.radius_km
ORDER BY scenario.scenario_id;
