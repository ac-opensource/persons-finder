\set ON_ERROR_STOP on

SELECT
    set_config('benchmark.expected_database', :'expected_database', false),
    set_config('benchmark.expected_user', :'expected_user', false),
    set_config('benchmark.expected_marker', :'expected_marker', false)
\gset

DO $safety$
BEGIN
    IF current_database() <> current_setting('benchmark.expected_database') THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: current_database() is %, required benchmark database is %',
            current_database(),
            current_setting('benchmark.expected_database');
    END IF;

    IF current_user <> current_setting('benchmark.expected_user') THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: current_user is %, required benchmark user is %',
            current_user,
            current_setting('benchmark.expected_user');
    END IF;

    IF current_setting('benchmark.expected_marker') <>
        'persons-finder-benchmark-v1' THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: benchmark marker is missing or incorrect';
    END IF;

    IF to_regclass('public.person') IS NULL
        OR to_regclass('public.location_observation') IS NULL
        OR to_regclass('public.last_known_location_projection') IS NULL THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: production Flyway schema is not present in the benchmark database';
    END IF;
END
$safety$;
