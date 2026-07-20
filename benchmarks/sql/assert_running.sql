\set ON_ERROR_STOP on
\ir safety.sql

DO $run_in_progress$
BEGIN
    IF (SELECT state FROM benchmark.control) <> 'running' THEN
        RAISE EXCEPTION
            'SAFETY REFUSAL: measurement command requires state=running';
    END IF;
END
$run_in_progress$;
