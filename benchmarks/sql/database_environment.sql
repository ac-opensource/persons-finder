\set ON_ERROR_STOP on
\ir assert_running.sql

SELECT 'database' AS category, 'version' AS name, version() AS value
UNION ALL
SELECT 'database', 'postgis', PostGIS_Full_Version()
UNION ALL
SELECT 'database', 'current_database', current_database()
UNION ALL
SELECT 'database', 'current_user', current_user
UNION ALL
SELECT 'setting', name, setting || coalesce(' ' || unit, '')
FROM pg_settings
WHERE name IN (
    'shared_buffers',
    'effective_cache_size',
    'work_mem',
    'maintenance_work_mem',
    'max_connections',
    'random_page_cost',
    'effective_io_concurrency',
    'jit',
    'track_io_timing'
)
UNION ALL
SELECT
    'relation_size',
    relation,
    pg_size_pretty(pg_total_relation_size(relation::regclass))
FROM unnest(
    ARRAY[
        'person',
        'location_observation',
        'last_known_location_projection',
        'benchmark.oracle_last_known',
        'benchmark.projection_subset_indexed',
        'benchmark.projection_subset_unindexed'
    ]
) AS relation;
