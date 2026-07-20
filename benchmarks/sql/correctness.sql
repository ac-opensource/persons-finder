\set ON_ERROR_STOP on
\ir safety.sql

DO $correct_seed_state$
BEGIN
    IF (SELECT state FROM benchmark.control) <> 'seeded' THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: correctness requires a newly seeded benchmark database';
    END IF;
END
$correct_seed_state$;

SET statement_timeout = 0;
SET lock_timeout = '5s';
SET client_min_messages = warning;

CREATE UNLOGGED TABLE benchmark.oracle_last_known AS
SELECT DISTINCT ON (person_id)
    person_id,
    id AS observation_id,
    captured_at,
    received_at,
    location
FROM location_observation
ORDER BY person_id, captured_at DESC, received_at DESC, id DESC;

ALTER TABLE benchmark.oracle_last_known
    ADD PRIMARY KEY (person_id);

COMMENT ON TABLE benchmark.oracle_last_known IS
    'Correctness-only winner oracle; deliberately has no spatial index';

\ir scenarios.sql

CREATE TABLE benchmark.correctness_result (
    check_id text PRIMARY KEY,
    production_rows bigint NOT NULL,
    oracle_rows bigint NOT NULL,
    duplicate_person_rows bigint NOT NULL,
    membership_or_distance_mismatches bigint NOT NULL,
    ordering_mismatches bigint NOT NULL,
    passed boolean NOT NULL
);

WITH projection_mismatches AS (
    SELECT coalesce(projection.person_id, oracle.person_id) AS person_id
    FROM last_known_location_projection AS projection
    FULL JOIN benchmark.oracle_last_known AS oracle USING (person_id)
    WHERE projection.person_id IS NULL
       OR oracle.person_id IS NULL
       OR projection.observation_id <> oracle.observation_id
       OR projection.captured_at <> oracle.captured_at
       OR projection.received_at <> oracle.received_at
       OR NOT ST_Equals(projection.location::geometry, oracle.location::geometry)
),
trail_depth_mismatches AS (
    SELECT person_id
    FROM location_observation
    GROUP BY person_id
    HAVING count(*) <> 5
)
INSERT INTO benchmark.correctness_result
SELECT
    'full-projection-winner-and-trail-depth',
    (SELECT count(*) FROM last_known_location_projection),
    (SELECT count(*) FROM benchmark.oracle_last_known),
    (SELECT count(*) FROM trail_depth_mismatches),
    (SELECT count(*) FROM projection_mismatches),
    0,
    (SELECT count(*) FROM trail_depth_mismatches) = 0
        AND (SELECT count(*) FROM projection_mismatches) = 0;

DO $body$
DECLARE
    scenario benchmark.nearby_scenario%ROWTYPE;
    comparison record;
BEGIN
    FOR scenario IN
        SELECT * FROM benchmark.nearby_scenario ORDER BY scenario_id
    LOOP
        WITH search AS (
            SELECT
                ST_SetSRID(
                    ST_MakePoint(scenario.longitude, scenario.latitude),
                    4326
                )::geography AS origin,
                scenario.radius_km * 1000.0 AS radius_metres
        ),
        production_unranked AS (
            SELECT
                person.id,
                ST_Distance(projection.location, search.origin, true)
                    AS distance_metres
            FROM last_known_location_projection AS projection
            JOIN person ON person.id = projection.person_id
            CROSS JOIN search
            WHERE ST_DWithin(
                projection.location,
                search.origin,
                search.radius_metres,
                true
            )
        ),
        production AS (
            SELECT
                id,
                distance_metres,
                row_number() OVER (ORDER BY distance_metres, id) AS result_order
            FROM production_unranked
        ),
        oracle_unranked AS (
            SELECT
                person.id,
                ST_Distance(oracle.location, search.origin, true)
                    AS distance_metres
            FROM benchmark.oracle_last_known AS oracle
            JOIN person ON person.id = oracle.person_id
            CROSS JOIN search
            WHERE ST_Distance(oracle.location, search.origin, true)
                <= search.radius_metres
        ),
        oracle AS (
            SELECT
                id,
                distance_metres,
                row_number() OVER (ORDER BY distance_metres, id) AS result_order
            FROM oracle_unranked
        ),
        membership_or_distance_mismatch AS (
            (
                SELECT id, distance_metres FROM production
                EXCEPT ALL
                SELECT id, distance_metres FROM oracle
            )
            UNION ALL
            (
                SELECT id, distance_metres FROM oracle
                EXCEPT ALL
                SELECT id, distance_metres FROM production
            )
        ),
        ordering_mismatch AS (
            SELECT production.id
            FROM production
            JOIN oracle USING (id)
            WHERE production.result_order <> oracle.result_order
        )
        SELECT
            (SELECT count(*) FROM production) AS production_rows,
            (SELECT count(*) FROM oracle) AS oracle_rows,
            (
                SELECT count(*) - count(DISTINCT id)
                FROM production
            ) AS duplicate_person_rows,
            (
                SELECT count(*)
                FROM membership_or_distance_mismatch
            ) AS membership_or_distance_mismatches,
            (SELECT count(*) FROM ordering_mismatch) AS ordering_mismatches
        INTO comparison;

        INSERT INTO benchmark.correctness_result
        VALUES (
            scenario.scenario_id,
            comparison.production_rows,
            comparison.oracle_rows,
            comparison.duplicate_person_rows,
            comparison.membership_or_distance_mismatches,
            comparison.ordering_mismatches,
            comparison.production_rows = comparison.oracle_rows
                AND comparison.duplicate_person_rows = 0
                AND comparison.membership_or_distance_mismatches = 0
                AND comparison.ordering_mismatches = 0
        );
    END LOOP;
END
$body$;

DO $correctness_gate$
BEGIN
    IF NOT (SELECT bool_and(passed) FROM benchmark.correctness_result) THEN
        RAISE EXCEPTION
            'CORRECTNESS FAILURE: projection or nearby output differs from the brute-force oracle';
    END IF;
END
$correctness_gate$;

CREATE UNLOGGED TABLE benchmark.projection_subset_unindexed AS
SELECT person_id, observation_id, captured_at, received_at, location
FROM benchmark.oracle_last_known
ORDER BY person_id
LIMIT 100000;

CREATE UNLOGGED TABLE benchmark.projection_subset_indexed
(LIKE benchmark.projection_subset_unindexed INCLUDING ALL);

INSERT INTO benchmark.projection_subset_indexed
SELECT * FROM benchmark.projection_subset_unindexed;

CREATE INDEX projection_subset_indexed_location_gist_idx
    ON benchmark.projection_subset_indexed
    USING GIST (location);

CREATE UNLOGGED TABLE benchmark.history_cursor AS
SELECT
    person_id,
    captured_at,
    received_at,
    id AS observation_id
FROM (
    SELECT
        person_id,
        captured_at,
        received_at,
        id,
        row_number() OVER (
            PARTITION BY person_id
            ORDER BY captured_at DESC, received_at DESC, id DESC
        ) AS result_order
    FROM location_observation
) AS ordered
WHERE result_order = 2;

CREATE UNIQUE INDEX history_cursor_person_id_idx
    ON benchmark.history_cursor (person_id);

CREATE SEQUENCE benchmark.write_operation_sequence;

ANALYZE benchmark.oracle_last_known;
ANALYZE benchmark.projection_subset_unindexed;
ANALYZE benchmark.projection_subset_indexed;
ANALYZE benchmark.history_cursor;

UPDATE benchmark.control
SET state = 'correctness_passed', updated_at = clock_timestamp();

SELECT * FROM benchmark.correctness_result ORDER BY check_id;
