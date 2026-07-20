\set ON_ERROR_STOP on
\ir safety.sql

DO $run_in_progress$
BEGIN
    IF (SELECT state FROM benchmark.control) <> 'running' THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: only a running benchmark can be completed';
    END IF;
END
$run_in_progress$;

UPDATE benchmark.control
SET state = 'completed', updated_at = clock_timestamp();
