from __future__ import annotations

import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
BENCHMARK_BIN = REPOSITORY_ROOT / "benchmarks" / "bin"
BENCHMARK_COMPOSE = REPOSITORY_ROOT / "benchmarks" / "compose.yaml"
sys.path.insert(0, str(BENCHMARK_BIN))

import summarize_raw  # noqa: E402
import validate_raw  # noqa: E402


GIT_SHA = "1" * 40
SOURCE_FINGERPRINT = "2" * 64
RUN_ID = "run-20260721T000000Z"

SMALL_SPEC = validate_raw.RunSpec(
    nearby_scenarios=("nearby-one", "nearby-empty"),
    nearby_throughput_scenarios=("nearby-one",),
    throughput_concurrencies=(1, 2),
    repeats=2,
    nearby_iterations_per_repeat=2,
    throughput_duration_seconds=1,
    database_workloads=(
        validate_raw.DatabaseWorkload("nearby-db", "nearby-one", 2),
        validate_raw.DatabaseWorkload("nearby-db", "nearby-empty", 2),
        validate_raw.DatabaseWorkload("baseline-db", "indexed", 2),
        validate_raw.DatabaseWorkload("history-db", "first-page", 2),
        validate_raw.DatabaseWorkload("writes-db", "winning-append", 3),
        validate_raw.DatabaseWorkload("writes-db", "late-append", 3),
        validate_raw.DatabaseWorkload("writes-db", "client-replay", 3),
        validate_raw.DatabaseWorkload("writes-db", "no-key-replay", 3),
    ),
    write_http_ordinals=(
        ("client-replay", 1, 2),
        ("no-key-replay", 3, 4),
        ("winning-append", 5, 6),
        ("late-append", 7, 8),
    ),
    write_http_concurrency=2,
    plan_files=("nearby.json",),
)


def write_csv(path: Path, fields: tuple[str, ...], rows: list[dict[str, object]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def write_jsonl(path: Path, records: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8") as output:
        for record in records:
            output.write(json.dumps(record, separators=(",", ":")) + "\n")


def pgbench_line(index: int) -> str:
    return f"0 {index} 1000 0 1700000000 {index + 1}\n"


def build_complete_run(run_dir: Path) -> dict[str, object]:
    run_dir.mkdir()
    (run_dir / "environment.json").write_text(
        json.dumps(
            {
                "run_id": RUN_ID,
                "git_sha": GIT_SHA,
                "source_fingerprint_sha256": SOURCE_FINGERPRINT,
            }
        ),
        encoding="utf-8",
    )
    write_csv(
        run_dir / "database-environment.csv",
        ("category", "name", "value"),
        [
            {"category": "database", "name": "version", "value": "test"},
            {"category": "database", "name": "postgis", "value": "test"},
            {
                "category": "database",
                "name": "current_database",
                "value": "test",
            },
            {"category": "database", "name": "current_user", "value": "test"},
        ],
    )
    write_csv(
        run_dir / "cardinalities.csv",
        validate_raw.CARDINALITY_FIELDS,
        [
            {
                "scenario_id": "nearby-one",
                "distribution": "test",
                "latitude": 0,
                "longitude": 0,
                "radius_km": 1,
                "rows_returned": 1,
                "distinct_persons": 1,
            },
            {
                "scenario_id": "nearby-empty",
                "distribution": "test",
                "latitude": 1,
                "longitude": 1,
                "radius_km": 1,
                "rows_returned": 0,
                "distinct_persons": 0,
            },
        ],
    )
    write_csv(
        run_dir / "nearby-http-oracle.csv",
        validate_raw.ORACLE_FIELDS,
        [
            {
                "scenario_id": "nearby-one",
                "result_order": 1,
                "id": "00000000-0000-4000-8000-000000000001",
                "latitude": 0,
                "longitude": 0,
                "distance_km": 0,
            }
        ],
    )

    for workload in SMALL_SPEC.database_workloads:
        directory = run_dir / workload.category
        directory.mkdir(exist_ok=True)
        for repeat in range(1, SMALL_SPEC.repeats + 1):
            (directory / f"{workload.label}-r{repeat}-summary.txt").write_text(
                "pgbench summary\n",
                encoding="utf-8",
            )
            (directory / f"{workload.label}-r{repeat}-.100").write_text(
                "".join(
                    pgbench_line(index)
                    for index in range(workload.samples_per_repeat)
                ),
                encoding="utf-8",
            )

    restart = run_dir / "restart-cold-db"
    restart.mkdir()
    (restart / "summary.txt").write_text("pgbench summary\n", encoding="utf-8")
    (restart / "restart-cold-.101").write_text(pgbench_line(0), encoding="utf-8")

    latency_records: list[dict[str, object]] = []
    cardinalities = {"nearby-one": 1, "nearby-empty": 0}
    for scenario in SMALL_SPEC.nearby_scenarios:
        for repeat in range(1, SMALL_SPEC.repeats + 1):
            for iteration in range(1, SMALL_SPEC.nearby_iterations_per_repeat + 1):
                latency_records.append(
                    {
                        "started_at_ns": 1,
                        "scenario_id": scenario,
                        "status": 200,
                        "elapsed_ns": 1,
                        "rows_returned": cardinalities[scenario],
                        "response_bytes": 2,
                        "mode": "latency",
                        "repeat": repeat,
                        "iteration": iteration,
                        "concurrency": 1,
                    }
                )
    write_jsonl(run_dir / "nearby-http.jsonl", latency_records)

    throughput_records: list[dict[str, object]] = []
    for concurrency in SMALL_SPEC.throughput_concurrencies:
        for repeat in range(1, SMALL_SPEC.repeats + 1):
            for worker in range(concurrency):
                throughput_records.append(
                    {
                        "started_at_ns": 1,
                        "scenario_id": "nearby-one",
                        "status": 200,
                        "elapsed_ns": 1_000_000_000,
                        "rows_returned": 1,
                        "response_bytes": 2,
                        "mode": "throughput",
                        "repeat": repeat,
                        "iteration": 1,
                        "worker": worker,
                        "concurrency": concurrency,
                        "duration_seconds": 1,
                    }
                )
    write_jsonl(run_dir / "nearby-http-throughput.jsonl", throughput_records)

    write_ranges = {
        scenario: (first, last)
        for scenario, first, last in SMALL_SPEC.write_http_ordinals
    }
    fixture_rows: list[dict[str, object]] = []
    for scenario in ("client-replay", "no-key-replay"):
        first, last = write_ranges[scenario]
        for ordinal in range(first, last + 1):
            fixture_rows.append(
                {
                    "scenario": scenario,
                    "person_ordinal": ordinal,
                    "person_id": f"person-{ordinal}",
                    "client_update_id": "",
                    "expected_observation_id": "expected",
                    "captured_at": "2026-01-01T00:00:00.000Z",
                    "latitude": 0,
                    "longitude": 0,
                }
            )
    write_csv(
        run_dir / "http-write-fixtures.csv",
        validate_raw.HTTP_WRITE_FIXTURE_FIELDS,
        fixture_rows,
    )

    write_records: list[dict[str, object]] = []
    for scenario, first, last in SMALL_SPEC.write_http_ordinals:
        for ordinal in range(first, last + 1):
            write_records.append(
                {
                    "scenario": scenario,
                    "person_ordinal": ordinal,
                    "status": 200,
                    "concurrency": SMALL_SPEC.write_http_concurrency,
                    "started_at_ns": 1,
                    "elapsed_ns": 1,
                    "response_bytes": 2,
                }
            )
    write_jsonl(run_dir / "writes-http.jsonl", write_records)

    write_snapshots = (
        (100, 100, 5),
        (106, 106, 11),
        (112, 112, 11),
        (112, 112, 11),
        (112, 112, 11),
        (116, 116, 13),
    )
    for index, (filename, values) in enumerate(
        zip(validate_raw.WRITE_COUNT_FILES, write_snapshots),
        start=1,
    ):
        observations, client_updates, winning_projections = values
        snapshot = {
            "persons": 20,
            "observations": observations,
            "projections": 20,
            "client_updates": client_updates,
            "benchmark_winning_projection_rows": winning_projections,
            "current_wal_lsn": f"0/{index:X}",
            "captured_at": f"2026-01-01T00:00:0{index}Z",
        }
        write_csv(
            run_dir / filename,
            validate_raw.WRITE_COUNT_FIELDS,
            [snapshot],
        )

    plans = run_dir / "plans"
    plans.mkdir()
    (plans / "nearby.json").write_text('[{"Plan":{}}]\n', encoding="utf-8")

    report = validate_raw.validate_raw_artifacts(
        run_dir,
        RUN_ID,
        GIT_SHA,
        SOURCE_FINGERPRINT,
        SMALL_SPEC,
    )
    (run_dir / "raw-completeness.json").write_text(
        json.dumps(report, sort_keys=True, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    (run_dir / "manifest.json").write_text(
        json.dumps(
            {
                "schema_version": 2,
                "phase": "run",
                "run_id": RUN_ID,
                "git_sha": GIT_SHA,
                "source_fingerprint": SOURCE_FINGERPRINT,
                "completed": True,
                "raw_completeness": "raw-completeness.json",
            },
            separators=(",", ":"),
        )
        + "\n",
        encoding="utf-8",
    )
    return report


class RawRunValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.run_dir = Path(self.temporary.name) / RUN_ID
        self.report = build_complete_run(self.run_dir)

    def test_complete_exact_fixture_passes(self) -> None:
        manifest, report = validate_raw.validate_completed_run(
            self.run_dir,
            SMALL_SPEC,
        )

        self.assertTrue(manifest["completed"])
        self.assertEqual(report, self.report)
        self.assertEqual(report["counts"]["nearby_scenarios"], 2)
        self.assertEqual(report["counts"]["nearby_http_latency_samples"], 8)
        self.assertEqual(
            report["throughput_group_samples"]["nearby-one/c2/r1"],
            2,
        )
        self.assertEqual(report["counts"]["write_http_samples"], 8)

    def test_missing_database_repeat_is_rejected(self) -> None:
        (self.run_dir / "nearby-db" / "nearby-empty-r2-.100").unlink()

        with self.assertRaisesRegex(
            validate_raw.RawRunValidationError,
            r"nearby-db/nearby-empty r2 has 0 samples; expected 2",
        ):
            validate_raw.validate_raw_artifacts(
                self.run_dir,
                RUN_ID,
                GIT_SHA,
                SOURCE_FINGERPRINT,
                SMALL_SPEC,
            )

    def test_duplicate_pgbench_transaction_key_is_rejected(self) -> None:
        path = self.run_dir / "nearby-db" / "nearby-one-r1-.100"
        line = pgbench_line(0)
        path.write_text(line + line, encoding="utf-8")

        with self.assertRaisesRegex(
            validate_raw.RawRunValidationError,
            r"duplicate pgbench client/transaction key",
        ):
            validate_raw.validate_raw_artifacts(
                self.run_dir,
                RUN_ID,
                GIT_SHA,
                SOURCE_FINGERPRINT,
                SMALL_SPEC,
            )

    def test_missing_http_iteration_is_rejected(self) -> None:
        records = (self.run_dir / "nearby-http.jsonl").read_text(
            encoding="utf-8"
        ).splitlines()
        (self.run_dir / "nearby-http.jsonl").write_text(
            "\n".join(records[:-1]) + "\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(
            validate_raw.RawRunValidationError,
            r"latency coverage differs.*missing=1",
        ):
            validate_raw.validate_raw_artifacts(
                self.run_dir,
                RUN_ID,
                GIT_SHA,
                SOURCE_FINGERPRINT,
                SMALL_SPEC,
            )

    def test_recorded_completeness_must_match_current_raw_files(self) -> None:
        recorded = json.loads(
            (self.run_dir / "raw-completeness.json").read_text(encoding="utf-8")
        )
        recorded["counts"]["plans"] = 99
        (self.run_dir / "raw-completeness.json").write_text(
            json.dumps(recorded),
            encoding="utf-8",
        )

        with self.assertRaisesRegex(
            validate_raw.RawRunValidationError,
            r"raw-completeness.json does not match",
        ):
            validate_raw.validate_completed_run(self.run_dir, SMALL_SPEC)

    def test_same_count_timing_mutation_is_rejected_by_artifact_hash(self) -> None:
        path = self.run_dir / "nearby-http.jsonl"
        records = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
        records[0]["elapsed_ns"] = 2
        write_jsonl(path, records)

        with self.assertRaisesRegex(
            validate_raw.RawRunValidationError,
            r"raw-completeness.json does not match",
        ):
            validate_raw.validate_completed_run(self.run_dir, SMALL_SPEC)

    def test_truncated_throughput_duration_is_rejected(self) -> None:
        path = self.run_dir / "nearby-http-throughput.jsonl"
        records = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
        for record in records:
            if record["concurrency"] == 2 and record["repeat"] == 1:
                record["elapsed_ns"] = 1
        write_jsonl(path, records)

        with self.assertRaisesRegex(
            validate_raw.RawRunValidationError,
            r"throughput group .* spans .* expected at least",
        ):
            validate_raw.validate_raw_artifacts(
                self.run_dir,
                RUN_ID,
                GIT_SHA,
                SOURCE_FINGERPRINT,
                SMALL_SPEC,
            )

    def test_malformed_write_snapshot_is_rejected(self) -> None:
        path = self.run_dir / "write-counts-after-http.csv"
        write_csv(
            path,
            validate_raw.WRITE_COUNT_FIELDS,
            [dict.fromkeys(validate_raw.WRITE_COUNT_FIELDS, "garbage")],
        )

        with self.assertRaisesRegex(
            validate_raw.RawRunValidationError,
            r"invalid integer field persons",
        ):
            validate_raw.validate_raw_artifacts(
                self.run_dir,
                RUN_ID,
                GIT_SHA,
                SOURCE_FINGERPRINT,
                SMALL_SPEC,
            )

    def test_summarizer_refuses_when_shared_validator_fails(self) -> None:
        summary = self.run_dir / "summary.md"
        with mock.patch.object(
            summarize_raw,
            "validate_completed_run",
            side_effect=validate_raw.RawRunValidationError("missing repeat"),
        ), mock.patch.object(
            sys,
            "argv",
            ["summarize_raw.py", "--run-dir", str(self.run_dir)],
        ):
            with self.assertRaisesRegex(SystemExit, r"refusing to summarize"):
                summarize_raw.main()
        self.assertFalse(summary.exists())

    def test_production_plan_declares_all_fixed_counts(self) -> None:
        spec = validate_raw.PRODUCTION_SPEC
        nearby = [
            workload
            for workload in spec.database_workloads
            if workload.category == "nearby-db"
        ]
        writes = [
            workload
            for workload in spec.database_workloads
            if workload.category == "writes-db"
        ]

        self.assertEqual(len(spec.nearby_scenarios), 10)
        self.assertEqual(len(nearby), 10)
        self.assertTrue(all(item.samples_per_repeat == 200 for item in nearby))
        self.assertEqual(spec.repeats, 3)
        self.assertEqual({item.samples_per_repeat for item in writes}, {8_000})
        self.assertEqual(len(spec.plan_files), 5)


class BenchmarkWrapperSafetyTest(unittest.TestCase):
    def test_shell_wrapper_is_syntactically_valid(self) -> None:
        completed = subprocess.run(
            ["bash", "-n", str(BENCHMARK_BIN / "benchmark")],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(completed.returncode, 0, completed.stderr)

    def test_all_compose_exec_calls_detach_stdin(self) -> None:
        source = (BENCHMARK_BIN / "benchmark").read_text(encoding="utf-8")

        self.assertEqual(source.count("compose exec"), 1)
        self.assertIn(
            'compose exec --no-TTY "$@" </dev/null',
            source,
        )
        self.assertIn(
            "compose_exec database sh -c 'umask 077; exec \"$@\"'",
            source,
        )
        self.assertNotIn("chmod 0777", source)
        self.assertNotIn("chmod 0733", source)
        self.assertIn('chmod 0700 "${run_dir}"', source)
        self.assertIn('docker cp "${container_id}:${container_directory}/."', source)

    def test_database_has_no_host_results_write_mount(self) -> None:
        compose_source = BENCHMARK_COMPOSE.read_text(encoding="utf-8")

        self.assertNotIn("./results:/benchmarks/results", compose_source)


if __name__ == "__main__":
    unittest.main()
