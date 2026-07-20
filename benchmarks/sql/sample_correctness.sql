\set ON_ERROR_STOP on
\ir safety.sql

DO $sample_correctness$
DECLARE
    person_count bigint;
    observation_count bigint;
    projection_count bigint;
    mismatch_count bigint;
BEGIN
    SELECT count(*) INTO person_count FROM person;
    SELECT count(*) INTO observation_count FROM location_observation;
    SELECT count(*) INTO projection_count FROM last_known_location_projection;

    WITH winners AS (
        SELECT DISTINCT ON (person_id)
            person_id,
            id AS observation_id,
            captured_at,
            received_at,
            location
        FROM location_observation
        ORDER BY person_id, captured_at DESC, received_at DESC, id DESC
    ),
    mismatches AS (
        SELECT
            coalesce(projection.person_id, winner.person_id) AS person_id
        FROM last_known_location_projection AS projection
        FULL JOIN winners AS winner USING (person_id)
        WHERE projection.person_id IS NULL
           OR winner.person_id IS NULL
           OR projection.observation_id <> winner.observation_id
           OR projection.captured_at <> winner.captured_at
           OR projection.received_at <> winner.received_at
           OR NOT ST_Equals(projection.location::geometry, winner.location::geometry)
    )
    SELECT count(*) INTO mismatch_count FROM mismatches;

    IF person_count <> 100
        OR observation_count <> 500
        OR projection_count <> 100
        OR mismatch_count <> 0 THEN
        RAISE EXCEPTION
            'CORRECTNESS FAILURE: application sample persons %, observations %, projections %, mismatches %',
            person_count,
            observation_count,
            projection_count,
            mismatch_count;
    END IF;
END
$sample_correctness$;

\echo 'application transaction sample: 100 persons, 500 observations, 100 projections, 0 mismatches'
