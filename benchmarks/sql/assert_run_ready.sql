\set ON_ERROR_STOP on
\ir safety.sql

DO $run_ready$
DECLARE
    benchmark_state text;
BEGIN
    IF to_regclass('benchmark.control') IS NULL THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: benchmark seed is missing or incomplete';
    END IF;

    EXECUTE 'SELECT state FROM benchmark.control'
    INTO STRICT benchmark_state;

    IF benchmark_state <> 'correctness_passed' THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: run requires a fresh seed with passing correctness checks (state: %)',
            benchmark_state;
    END IF;
END
$run_ready$;

UPDATE benchmark.control
SET state = 'running', updated_at = clock_timestamp();
