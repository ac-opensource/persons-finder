SELECT nextval('benchmark.write_operation_sequence') AS sequence_value \gset
BEGIN;
SELECT person_id
FROM last_known_location_projection
WHERE person_id = benchmark.deterministic_uuid(
    'person',
    750001 + mod(:sequence_value, 50000)
)
FOR UPDATE;
INSERT INTO location_observation (
    id,
    person_id,
    captured_at,
    received_at,
    source,
    client_update_id,
    location
)
VALUES (
    benchmark.deterministic_uuid('write-late-observation', :sequence_value),
    benchmark.deterministic_uuid('person', 750001 + mod(:sequence_value, 50000)),
    timestamptz '2024-12-01 00:00:00+00'
        + :sequence_value * interval '1 microsecond',
    timestamptz '2025-02-01 00:00:00+00'
        + :sequence_value * interval '1 microsecond',
    'CLIENT_UPDATE',
    benchmark.deterministic_uuid('write-late-client', :sequence_value),
    ST_SetSRID(
        ST_MakePoint(
            103.80 + mod(:sequence_value, 1000) * 0.000001,
            1.35 + mod(:sequence_value, 1000) * 0.000001
        ),
        4326
    )::geography
);
UPDATE last_known_location_projection
SET
    observation_id = benchmark.deterministic_uuid(
        'write-late-observation',
        :sequence_value
    ),
    captured_at = timestamptz '2024-12-01 00:00:00+00'
        + :sequence_value * interval '1 microsecond',
    received_at = timestamptz '2025-02-01 00:00:00+00'
        + :sequence_value * interval '1 microsecond',
    location = ST_SetSRID(
        ST_MakePoint(
            103.80 + mod(:sequence_value, 1000) * 0.000001,
            1.35 + mod(:sequence_value, 1000) * 0.000001
        ),
        4326
    )::geography
WHERE person_id = benchmark.deterministic_uuid(
        'person',
        750001 + mod(:sequence_value, 50000)
    )
  AND (captured_at, received_at, observation_id) < (
      timestamptz '2024-12-01 00:00:00+00'
          + :sequence_value * interval '1 microsecond',
      timestamptz '2025-02-01 00:00:00+00'
          + :sequence_value * interval '1 microsecond',
      benchmark.deterministic_uuid('write-late-observation', :sequence_value)
  );
COMMIT;
