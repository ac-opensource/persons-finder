SELECT id, captured_at, received_at, source
FROM location_observation
WHERE person_id = benchmark.deterministic_uuid(
    'person',
    mod(:client_id, 1000000) + 1
)
ORDER BY captured_at DESC, received_at DESC, id DESC
LIMIT 2;
