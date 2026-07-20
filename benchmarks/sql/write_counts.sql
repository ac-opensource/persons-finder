\set ON_ERROR_STOP on
\ir assert_running.sql

SELECT
    (SELECT count(*) FROM person) AS persons,
    (SELECT count(*) FROM location_observation) AS observations,
    (SELECT count(*) FROM last_known_location_projection) AS projections,
    (
        SELECT count(*)
        FROM location_observation
        WHERE source = 'CLIENT_UPDATE'
    ) AS client_updates,
    (
        SELECT count(*)
        FROM last_known_location_projection
        WHERE captured_at >= timestamptz '2025-02-01 00:00:00+00'
    ) AS benchmark_winning_projection_rows,
    pg_current_wal_lsn() AS current_wal_lsn,
    clock_timestamp() AS captured_at;
