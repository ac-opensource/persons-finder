WITH search AS (
    SELECT
        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography AS origin,
        :radius_km::double precision * 1000.0 AS radius_metres
)
SELECT
    person.id,
    person.name,
    person.job_title,
    person.hobbies,
    person.bio,
    person.created_at,
    projection.captured_at AS last_known_location_at,
    ST_Distance(projection.location, search.origin, true) AS distance_metres
FROM last_known_location_projection AS projection
JOIN person ON person.id = projection.person_id
CROSS JOIN search
WHERE ST_DWithin(
    projection.location,
    search.origin,
    search.radius_metres,
    true
)
ORDER BY distance_metres, person.id;
