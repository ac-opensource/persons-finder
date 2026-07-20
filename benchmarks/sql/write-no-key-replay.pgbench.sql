\set person_ordinal random(850001, 899999)
BEGIN;
SELECT
    ST_Y(location::geometry) AS requested_latitude,
    ST_X(location::geometry) AS requested_longitude
FROM last_known_location_projection
WHERE person_id = benchmark.deterministic_uuid('person', :person_ordinal)
FOR UPDATE
\gset

WITH requested AS (
    SELECT
        benchmark.deterministic_uuid('person', :person_ordinal) AS person_id,
        clock_timestamp() AS received_at,
        ST_SetSRID(
            ST_MakePoint(:requested_longitude, :requested_latitude),
            4326
        )::geography AS location
),
current_projection AS (
    SELECT projection.*
    FROM last_known_location_projection AS projection
    JOIN requested USING (person_id)
),
inserted AS (
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
        benchmark.deterministic_uuid(
            'write-no-key-replay-observation',
            :person_ordinal
        ),
        requested.person_id,
        requested.received_at,
        requested.received_at,
        'NO_KEY',
        NULL,
        requested.location
    FROM requested
    JOIN current_projection USING (person_id)
    WHERE NOT ST_Equals(
        current_projection.location::geometry,
        requested.location::geometry
    )
    RETURNING *
),
updated AS (
    UPDATE last_known_location_projection AS projection
    SET
        observation_id = inserted.id,
        captured_at = inserted.captured_at,
        received_at = inserted.received_at,
        location = inserted.location
    FROM inserted
    WHERE projection.person_id = inserted.person_id
      AND (
          projection.captured_at,
          projection.received_at,
          projection.observation_id
      ) < (
          inserted.captured_at,
          inserted.received_at,
          inserted.id
      )
    RETURNING projection.person_id
)
SELECT
    1 / CASE
        WHEN (SELECT count(*) FROM inserted) = 0
         AND (SELECT count(*) FROM updated) = 0
        THEN 1
        ELSE 0
    END AS asserted_no_key_replay_noop;
COMMIT;
