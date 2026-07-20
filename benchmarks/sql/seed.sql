\set ON_ERROR_STOP on
\ir safety.sql

SET statement_timeout = 0;
SET lock_timeout = '5s';
SET idle_in_transaction_session_timeout = 0;
SET client_min_messages = warning;

DO $empty_check$
BEGIN
    IF (SELECT count(*) FROM person) <> 0
        OR (SELECT count(*) FROM location_observation) <> 0
        OR (SELECT count(*) FROM last_known_location_projection) <> 0 THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: benchmark tables are not empty; run the guarded reset command first';
    END IF;
END
$empty_check$;

BEGIN;

CREATE SCHEMA benchmark;

CREATE TABLE benchmark.control (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    marker text NOT NULL CHECK (marker = 'persons-finder-benchmark-v1'),
    seed_version text NOT NULL,
    git_sha text NOT NULL,
    source_fingerprint text NOT NULL,
    state text NOT NULL CHECK (
        state IN ('seeding', 'seeded', 'correctness_passed', 'running', 'completed')
    ),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO benchmark.control (
    marker,
    seed_version,
    git_sha,
    source_fingerprint,
    state
)
VALUES (
    'persons-finder-benchmark-v1',
    'one-million-v1',
    :'git_sha',
    :'source_fingerprint',
    'seeding'
);

CREATE FUNCTION benchmark.deterministic_uuid(namespace text, ordinal bigint)
RETURNS uuid
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $function$
    WITH digest AS (
        SELECT md5(namespace || ':' || ordinal::text) AS value
    )
    SELECT (
        substr(value, 1, 8) || '-' ||
        substr(value, 9, 4) || '-4' ||
        substr(value, 14, 3) || '-8' ||
        substr(value, 18, 3) || '-' ||
        substr(value, 21, 12)
    )::uuid
    FROM digest
$function$;

CREATE UNLOGGED TABLE benchmark.seed_location (
    person_ordinal bigint PRIMARY KEY,
    final_location geography(Point, 4326) NOT NULL
);

INSERT INTO benchmark.seed_location (person_ordinal, final_location)
SELECT
    ordinal,
    ST_SetSRID(
        ST_MakePoint(
            (ordinal * 137.50776405003785)
                - floor((ordinal * 137.50776405003785) / 360.0) * 360.0
                - 180.0,
            degrees(asin(-1.0 + 2.0 * ((ordinal - 0.5) / 700000.0)))
        ),
        4326
    )::geography
FROM generate_series(1, 700000) AS generated(ordinal);

WITH cluster_centres(cluster_number, latitude, longitude) AS (
    VALUES
        (0, -36.8485::double precision, 174.7633::double precision),
        (1, -41.2865::double precision, 174.7762::double precision),
        (2, -33.8688::double precision, 151.2093::double precision),
        (3, 1.3521::double precision, 103.8198::double precision),
        (4, 51.5074::double precision, -0.1278::double precision),
        (5, 40.7128::double precision, -74.0060::double precision)
)
INSERT INTO benchmark.seed_location (person_ordinal, final_location)
SELECT
    generated.ordinal,
    ST_Project(
        ST_SetSRID(
            ST_MakePoint(centre.longitude, centre.latitude),
            4326
        )::geography,
        20000.0 * sqrt(
            (
                mod(generated.within_cluster::bigint * 48271, 49999)::double precision
                + 0.5
            ) / 50000.0
        ),
        radians(
            (generated.within_cluster * 137.50776405003785)
                - floor((generated.within_cluster * 137.50776405003785) / 360.0)
                * 360.0
        )
    )
FROM (
    SELECT
        ordinal,
        ((ordinal - 700001) / 50000)::integer AS cluster_number,
        mod(ordinal - 700001, 50000) + 1 AS within_cluster
    FROM generate_series(700001, 1000000) AS source(ordinal)
) AS generated
JOIN cluster_centres AS centre USING (cluster_number);

INSERT INTO person (id, name, job_title, hobbies, bio, created_at)
SELECT
    benchmark.deterministic_uuid('person', person_ordinal),
    'Benchmark Person ' || lpad(person_ordinal::text, 7, '0'),
    CASE mod(person_ordinal, 4)
        WHEN 0 THEN 'Software engineer'
        WHEN 1 THEN 'Teacher'
        WHEN 2 THEN 'Designer'
        ELSE 'Carpenter'
    END,
    ARRAY[
        CASE mod(person_ordinal, 5)
            WHEN 0 THEN 'hiking'
            WHEN 1 THEN 'reading'
            WHEN 2 THEN 'pottery'
            WHEN 3 THEN 'gardening'
            ELSE 'chess'
        END
    ]::text[],
    'Benchmark Person ' || lpad(person_ordinal::text, 7, '0') ||
        ' keeps a quirky compass handy for deterministic adventures.',
    timestamptz '2025-01-01 00:00:00+00'
        + mod(person_ordinal, 86400000) * interval '1 millisecond'
FROM benchmark.seed_location;

WITH observation_source AS (
    SELECT
        location.person_ordinal,
        location.final_location,
        slot,
        mod(location.person_ordinal - 1, 100)::integer AS cohort,
        benchmark.deterministic_uuid(
            'observation',
            (location.person_ordinal - 1) * 5 + slot
        ) AS observation_id,
        benchmark.deterministic_uuid(
            'client-update',
            (location.person_ordinal - 1) * 3 + slot - 2
        ) AS client_update_id,
        timestamptz '2025-01-01 00:00:00+00'
            + mod(location.person_ordinal, 86400000) * interval '1 millisecond'
            AS base_time,
        CASE
            WHEN benchmark.deterministic_uuid(
                'observation',
                (location.person_ordinal - 1) * 5 + 4
            ) > benchmark.deterministic_uuid(
                'observation',
                (location.person_ordinal - 1) * 5 + 5
            ) THEN 4
            ELSE 5
        END AS uuid_tie_winner_slot
    FROM benchmark.seed_location AS location
    CROSS JOIN generate_series(1, 5) AS generated(slot)
),
prepared AS (
    SELECT
        observation_id AS id,
        benchmark.deterministic_uuid('person', person_ordinal) AS person_id,
        CASE slot
            WHEN 1 THEN base_time
            WHEN 2 THEN base_time + interval '10 minutes'
            WHEN 3 THEN base_time + interval '20 minutes'
            WHEN 4 THEN base_time + CASE
                WHEN cohort >= 95 THEN interval '40 minutes'
                ELSE interval '30 minutes'
            END
            ELSE base_time + interval '40 minutes'
        END AS captured_at,
        CASE slot
            WHEN 1 THEN base_time
            WHEN 2 THEN base_time + interval '10 minutes'
            WHEN 3 THEN base_time + interval '21 minutes'
            WHEN 4 THEN base_time + CASE
                WHEN cohort BETWEEN 75 AND 94 THEN interval '50 minutes'
                WHEN cohort BETWEEN 95 AND 98 THEN interval '40 minutes'
                WHEN cohort = 99 THEN interval '42 minutes'
                ELSE interval '31 minutes'
            END
            ELSE base_time + CASE
                WHEN cohort = 99 THEN interval '42 minutes'
                ELSE interval '41 minutes'
            END
        END AS received_at,
        CASE slot
            WHEN 1 THEN 'INITIAL'
            WHEN 2 THEN 'NO_KEY'
            ELSE 'CLIENT_UPDATE'
        END AS source,
        CASE WHEN slot >= 3 THEN client_update_id END AS client_update_id,
        CASE
            WHEN (cohort <> 99 AND slot = 5)
                OR (cohort = 99 AND slot = uuid_tie_winner_slot)
            THEN final_location
            ELSE ST_Project(
                final_location,
                (6 - slot) * 150.0,
                radians(
                    (person_ordinal * 53.0 + slot * 71.0)
                        - floor((person_ordinal * 53.0 + slot * 71.0) / 360.0)
                        * 360.0
                )
            )
        END AS observation_location
    FROM observation_source
)
INSERT INTO location_observation (
    id,
    person_id,
    captured_at,
    received_at,
    source,
    client_update_id,
    location
)
SELECT
    id,
    person_id,
    captured_at,
    received_at,
    source,
    client_update_id,
    observation_location
FROM prepared;

INSERT INTO last_known_location_projection (
    person_id,
    observation_id,
    captured_at,
    received_at,
    location
)
SELECT DISTINCT ON (person_id)
    person_id,
    id,
    captured_at,
    received_at,
    location
FROM location_observation
ORDER BY person_id, captured_at DESC, received_at DESC, id DESC;

DROP TABLE benchmark.seed_location;

CREATE TABLE benchmark.seed_manifest (
    seed_version text PRIMARY KEY,
    person_count bigint NOT NULL,
    observation_count bigint NOT NULL,
    projection_count bigint NOT NULL,
    initial_count bigint NOT NULL,
    no_key_count bigint NOT NULL,
    client_update_count bigint NOT NULL,
    normal_trail_count bigint NOT NULL,
    late_arrival_trail_count bigint NOT NULL,
    received_at_tiebreak_trail_count bigint NOT NULL,
    uuid_tiebreak_trail_count bigint NOT NULL,
    deterministic_identity_checksum text NOT NULL,
    seeded_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO benchmark.seed_manifest
SELECT
    'one-million-v1',
    (SELECT count(*) FROM person),
    (SELECT count(*) FROM location_observation),
    (SELECT count(*) FROM last_known_location_projection),
    (SELECT count(*) FROM location_observation WHERE source = 'INITIAL'),
    (SELECT count(*) FROM location_observation WHERE source = 'NO_KEY'),
    (SELECT count(*) FROM location_observation WHERE source = 'CLIENT_UPDATE'),
    750000,
    200000,
    40000,
    10000,
    md5(
        sum(
            hashtextextended(id::text, 0)::numeric
        )::text
    )
FROM person;

UPDATE benchmark.control
SET state = 'seeded', updated_at = clock_timestamp();

COMMIT;

ANALYZE person;
ANALYZE location_observation;
ANALYZE last_known_location_projection;

SELECT
    person_count,
    observation_count,
    projection_count,
    initial_count,
    no_key_count,
    client_update_count,
    normal_trail_count,
    late_arrival_trail_count,
    received_at_tiebreak_trail_count,
    uuid_tiebreak_trail_count,
    deterministic_identity_checksum
FROM benchmark.seed_manifest;

DO $exact_count_check$
DECLARE
    manifest benchmark.seed_manifest%ROWTYPE;
BEGIN
    SELECT * INTO STRICT manifest FROM benchmark.seed_manifest;
    IF manifest.person_count <> 1000000
        OR manifest.observation_count <> 5000000
        OR manifest.projection_count <> 1000000
        OR manifest.initial_count <> 1000000
        OR manifest.no_key_count <> 1000000
        OR manifest.client_update_count <> 3000000 THEN
        RAISE EXCEPTION
            'CORRECTNESS FAILURE: exact seed counts do not match the approved dataset';
    END IF;
END
$exact_count_check$;

DO $winner_cohort_shape_check$
DECLARE
    received_at_tiebreak_trails bigint;
    uuid_tiebreak_trails bigint;
BEGIN
    WITH cohort_person AS (
        SELECT
            person_ordinal,
            mod(person_ordinal - 1, 100)::integer AS cohort
        FROM generate_series(1, 1000000) AS generated(person_ordinal)
        WHERE mod(person_ordinal - 1, 100) >= 95
    ),
    final_pair AS (
        SELECT
            cohort_person.cohort,
            slot_4.id AS slot_4_id,
            slot_4.captured_at AS slot_4_captured_at,
            slot_4.received_at AS slot_4_received_at,
            slot_5.id AS slot_5_id,
            slot_5.captured_at AS slot_5_captured_at,
            slot_5.received_at AS slot_5_received_at
        FROM cohort_person
        JOIN location_observation AS slot_4
            ON slot_4.id = benchmark.deterministic_uuid(
                'observation',
                (cohort_person.person_ordinal - 1) * 5 + 4
            )
        JOIN location_observation AS slot_5
            ON slot_5.id = benchmark.deterministic_uuid(
                'observation',
                (cohort_person.person_ordinal - 1) * 5 + 5
            )
    )
    SELECT
        count(*) FILTER (
            WHERE cohort BETWEEN 95 AND 98
              AND slot_4_captured_at = slot_5_captured_at
              AND slot_4_received_at < slot_5_received_at
        ),
        count(*) FILTER (
            WHERE cohort = 99
              AND slot_4_captured_at = slot_5_captured_at
              AND slot_4_received_at = slot_5_received_at
              AND slot_4_id <> slot_5_id
        )
    INTO received_at_tiebreak_trails, uuid_tiebreak_trails
    FROM final_pair;

    IF received_at_tiebreak_trails <> 40000
        OR uuid_tiebreak_trails <> 10000 THEN
        RAISE EXCEPTION
            'CORRECTNESS FAILURE: winner tiebreak cohorts do not match the approved dataset';
    END IF;
END
$winner_cohort_shape_check$;
