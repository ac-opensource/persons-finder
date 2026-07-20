\set person_ordinal random(800001, 849999)
BEGIN;
SELECT person_id
FROM last_known_location_projection
WHERE person_id = benchmark.deterministic_uuid('person', :person_ordinal)
FOR UPDATE;
SELECT
    id,
    captured_at,
    received_at,
    location
FROM location_observation
WHERE person_id = benchmark.deterministic_uuid('person', :person_ordinal)
  AND client_update_id = benchmark.deterministic_uuid(
      'client-update',
      (:person_ordinal - 1) * 3 + 1
  );
COMMIT;
