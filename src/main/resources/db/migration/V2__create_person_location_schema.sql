CREATE TABLE person (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    job_title text NOT NULL,
    hobbies text[] NOT NULL,
    bio text NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT person_hobbies_nonempty CHECK (cardinality(hobbies) > 0),
    CONSTRAINT person_bio_nonblank CHECK (length(btrim(bio)) > 0)
);

CREATE TABLE location_observation (
    id uuid PRIMARY KEY,
    person_id uuid NOT NULL REFERENCES person (id),
    captured_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    source text NOT NULL,
    client_update_id uuid,
    location geography(Point, 4326) NOT NULL,
    CONSTRAINT location_observation_person_id_id_unique UNIQUE (person_id, id),
    CONSTRAINT location_observation_client_update_unique
        UNIQUE (person_id, client_update_id),
    CONSTRAINT location_observation_source_valid CHECK (
        (source = 'INITIAL' AND client_update_id IS NULL)
        OR (source = 'NO_KEY' AND client_update_id IS NULL)
        OR (source = 'CLIENT_UPDATE' AND client_update_id IS NOT NULL)
    ),
    CONSTRAINT location_observation_longitude_canonical CHECK (
        ST_X(location::geometry) >= -180
        AND ST_X(location::geometry) < 180
    ),
    CONSTRAINT location_observation_pole_canonical CHECK (
        abs(ST_Y(location::geometry)) <> 90
        OR ST_X(location::geometry) = 0
    )
);

CREATE TABLE last_known_location_projection (
    person_id uuid PRIMARY KEY REFERENCES person (id),
    observation_id uuid NOT NULL,
    captured_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    location geography(Point, 4326) NOT NULL,
    CONSTRAINT last_known_observation_belongs_to_person
        FOREIGN KEY (person_id, observation_id)
        REFERENCES location_observation (person_id, id),
    CONSTRAINT last_known_longitude_canonical CHECK (
        ST_X(location::geometry) >= -180
        AND ST_X(location::geometry) < 180
    ),
    CONSTRAINT last_known_pole_canonical CHECK (
        abs(ST_Y(location::geometry)) <> 90
        OR ST_X(location::geometry) = 0
    )
);
