SELECT observation.id, observation.captured_at, observation.received_at, observation.source
FROM location_observation AS observation
JOIN benchmark.history_cursor AS cursor
    ON cursor.person_id = observation.person_id
WHERE observation.person_id = benchmark.deterministic_uuid(
        'person',
        mod(:client_id, 1000000) + 1
    )
  AND (
      observation.captured_at,
      observation.received_at,
      observation.id
  ) < (
      cursor.captured_at,
      cursor.received_at,
      cursor.observation_id
  )
ORDER BY observation.captured_at DESC, observation.received_at DESC, observation.id DESC
LIMIT 2;
