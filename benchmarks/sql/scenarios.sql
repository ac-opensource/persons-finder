CREATE TABLE benchmark.nearby_scenario (
    scenario_id text PRIMARY KEY,
    distribution text NOT NULL,
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    radius_km double precision NOT NULL CHECK (radius_km > 0 AND radius_km <= 100)
);

INSERT INTO benchmark.nearby_scenario VALUES
    ('dense-auckland-0_1km', 'dense-cluster', -36.8485, 174.7633, 0.1),
    ('dense-auckland-1km', 'dense-cluster', -36.8485, 174.7633, 1.0),
    ('dense-auckland-5km', 'dense-cluster', -36.8485, 174.7633, 5.0),
    ('dense-auckland-20km', 'dense-cluster', -36.8485, 174.7633, 20.0),
    ('dense-auckland-100km', 'dense-cluster', -36.8485, 174.7633, 100.0),
    ('global-origin-1km', 'global', 0.0, 0.0, 1.0),
    ('global-origin-20km', 'global', 0.0, 0.0, 20.0),
    ('global-origin-100km', 'global', 0.0, 0.0, 100.0),
    ('antimeridian-20km', 'global-edge', 0.0, 179.95, 20.0),
    ('antimeridian-100km', 'global-edge', 0.0, 179.95, 100.0);
