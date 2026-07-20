\set ON_ERROR_STOP on
\ir safety.sql

BEGIN;

LOCK TABLE
    person,
    location_observation,
    last_known_location_projection
IN SHARE MODE;

DO $run_ready$
DECLARE
    benchmark_state text;
    manifest_rows bigint;
    manifest_person_count bigint;
    manifest_observation_count bigint;
    manifest_projection_count bigint;
    manifest_initial_count bigint;
    manifest_no_key_count bigint;
    manifest_client_update_count bigint;
    manifest_identity_checksum text;
    current_person_count bigint;
    current_observation_count bigint;
    current_projection_count bigint;
    current_initial_count bigint;
    current_no_key_count bigint;
    current_client_update_count bigint;
    current_identity_checksum text;
    projection_mismatches bigint;
    correctness_rows bigint;
    correctness_passed boolean;
BEGIN
    IF to_regclass('benchmark.control') IS NULL
        OR to_regclass('benchmark.seed_manifest') IS NULL
        OR to_regclass('benchmark.correctness_result') IS NULL
        OR to_regclass('benchmark.oracle_last_known') IS NULL
        OR to_regclass('benchmark.nearby_scenario') IS NULL THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: benchmark seed is missing or incomplete';
    END IF;

    EXECUTE 'SELECT state FROM benchmark.control'
    INTO STRICT benchmark_state;

    IF benchmark_state <> 'correctness_passed' THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: run requires a fresh seed with passing correctness checks (state: %)',
            benchmark_state;
    END IF;

    SELECT count(*)
    INTO manifest_rows
    FROM benchmark.seed_manifest;

    IF manifest_rows <> 1 THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: benchmark seed manifest must contain exactly one row';
    END IF;

    SELECT
        person_count,
        observation_count,
        projection_count,
        initial_count,
        no_key_count,
        client_update_count,
        deterministic_identity_checksum
    INTO STRICT
        manifest_person_count,
        manifest_observation_count,
        manifest_projection_count,
        manifest_initial_count,
        manifest_no_key_count,
        manifest_client_update_count,
        manifest_identity_checksum
    FROM benchmark.seed_manifest;

    SELECT
        (SELECT count(*) FROM person),
        (SELECT count(*) FROM location_observation),
        (SELECT count(*) FROM last_known_location_projection),
        (SELECT count(*) FROM location_observation WHERE source = 'INITIAL'),
        (SELECT count(*) FROM location_observation WHERE source = 'NO_KEY'),
        (SELECT count(*) FROM location_observation WHERE source = 'CLIENT_UPDATE'),
        (
            SELECT md5(
                sum(hashtextextended(id::text, 0)::numeric)::text
            )
            FROM person
        )
    INTO
        current_person_count,
        current_observation_count,
        current_projection_count,
        current_initial_count,
        current_no_key_count,
        current_client_update_count,
        current_identity_checksum;

    IF current_person_count <> manifest_person_count
        OR current_observation_count <> manifest_observation_count
        OR current_projection_count <> manifest_projection_count
        OR current_initial_count <> manifest_initial_count
        OR current_no_key_count <> manifest_no_key_count
        OR current_client_update_count <> manifest_client_update_count
        OR current_identity_checksum IS DISTINCT FROM manifest_identity_checksum THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: benchmark data changed after correctness passed; reset and seed again';
    END IF;

    SELECT count(*)
    INTO projection_mismatches
    FROM (
        SELECT coalesce(projection.person_id, oracle.person_id) AS person_id
        FROM last_known_location_projection AS projection
        FULL JOIN benchmark.oracle_last_known AS oracle USING (person_id)
        WHERE projection.person_id IS NULL
           OR oracle.person_id IS NULL
           OR projection.observation_id <> oracle.observation_id
           OR projection.captured_at <> oracle.captured_at
           OR projection.received_at <> oracle.received_at
           OR NOT ST_Equals(
               projection.location::geometry,
               oracle.location::geometry
           )
    ) AS mismatches;

    SELECT count(*), coalesce(bool_and(passed), false)
    INTO correctness_rows, correctness_passed
    FROM benchmark.correctness_result;

    IF projection_mismatches <> 0
        OR correctness_rows <> (
            SELECT count(*) + 1
            FROM benchmark.nearby_scenario
        )
        OR NOT correctness_passed THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: benchmark correctness evidence no longer matches the live seed; reset and seed again';
    END IF;
END
$run_ready$;

UPDATE benchmark.control
SET state = 'running', updated_at = clock_timestamp();

COMMIT;
