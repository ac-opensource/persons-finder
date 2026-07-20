CREATE INDEX last_known_location_projection_location_gist_idx
    ON last_known_location_projection
    USING GIST (location);
