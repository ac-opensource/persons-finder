#!/usr/bin/env python3
"""Fail closed unless a benchmark run contains its complete raw evidence set."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


class RawRunValidationError(RuntimeError):
    """Raised when raw benchmark evidence is missing, malformed, or partial."""


@dataclass(frozen=True)
class DatabaseWorkload:
    category: str
    label: str
    samples_per_repeat: int


@dataclass(frozen=True)
class RunSpec:
    nearby_scenarios: tuple[str, ...]
    nearby_throughput_scenarios: tuple[str, ...]
    throughput_concurrencies: tuple[int, ...]
    repeats: int
    nearby_iterations_per_repeat: int
    throughput_duration_seconds: int
    database_workloads: tuple[DatabaseWorkload, ...]
    write_http_ordinals: tuple[tuple[str, int, int], ...]
    write_http_concurrency: int
    plan_files: tuple[str, ...]


PRODUCTION_SPEC = RunSpec(
    nearby_scenarios=(
        "dense-auckland-0_1km",
        "dense-auckland-1km",
        "dense-auckland-5km",
        "dense-auckland-20km",
        "dense-auckland-100km",
        "global-origin-1km",
        "global-origin-20km",
        "global-origin-100km",
        "antimeridian-20km",
        "antimeridian-100km",
    ),
    nearby_throughput_scenarios=(
        "dense-auckland-0_1km",
        "dense-auckland-1km",
        "dense-auckland-20km",
    ),
    throughput_concurrencies=(1, 8),
    repeats=3,
    nearby_iterations_per_repeat=200,
    throughput_duration_seconds=60,
    database_workloads=(
        *(
            DatabaseWorkload("nearby-db", scenario, 200)
            for scenario in (
                "dense-auckland-0_1km",
                "dense-auckland-1km",
                "dense-auckland-5km",
                "dense-auckland-20km",
                "dense-auckland-100km",
                "global-origin-1km",
                "global-origin-20km",
                "global-origin-100km",
                "antimeridian-20km",
                "antimeridian-100km",
            )
        ),
        DatabaseWorkload("baseline-db", "indexed-100k", 200),
        DatabaseWorkload("baseline-db", "unindexed-100k", 200),
        DatabaseWorkload("history-db", "first-page-size-2", 200),
        DatabaseWorkload("history-db", "next-page-size-2", 200),
        DatabaseWorkload("writes-db", "winning-append", 8_000),
        DatabaseWorkload("writes-db", "late-append", 8_000),
        DatabaseWorkload("writes-db", "client-replay", 8_000),
        DatabaseWorkload("writes-db", "no-key-replay", 8_000),
    ),
    write_http_ordinals=(
        ("client-replay", 900_001, 901_000),
        ("no-key-replay", 901_001, 902_000),
        ("winning-append", 902_001, 903_000),
        ("late-append", 903_001, 904_000),
    ),
    write_http_concurrency=8,
    plan_files=(
        "nearby-dense-1km.json",
        "baseline-indexed-100k.json",
        "baseline-unindexed-100k.json",
        "history-first-page.json",
        "history-next-page.json",
    ),
)


CARDINALITY_FIELDS = (
    "scenario_id",
    "distribution",
    "latitude",
    "longitude",
    "radius_km",
    "rows_returned",
    "distinct_persons",
)
ORACLE_FIELDS = (
    "scenario_id",
    "result_order",
    "id",
    "latitude",
    "longitude",
    "distance_km",
)
WRITE_COUNT_FILES = (
    "write-counts-before.csv",
    "write-counts-after-winning.csv",
    "write-counts-after-late.csv",
    "write-counts-after-client-replay.csv",
    "write-counts-after-db.csv",
    "write-counts-after-http.csv",
)
WRITE_COUNT_FIELDS = (
    "persons",
    "observations",
    "projections",
    "client_updates",
    "benchmark_winning_projection_rows",
    "current_wal_lsn",
    "captured_at",
)
HTTP_WRITE_FIXTURE_FIELDS = (
    "scenario",
    "person_ordinal",
    "person_id",
    "client_update_id",
    "expected_observation_id",
    "captured_at",
    "latitude",
    "longitude",
)
PGBENCH_LOG = re.compile(r"(?P<label>.+)-r(?P<repeat>\d+)-\.\d+(?:\.\d+)?$")


def invalid(message: str) -> RawRunValidationError:
    return RawRunValidationError(message)


def require_file(path: Path) -> None:
    if not path.is_file():
        raise invalid(f"required raw artifact is missing: {path.name}")
    if path.stat().st_size == 0:
        raise invalid(f"required raw artifact is empty: {path.name}")


def load_json(path: Path) -> dict[str, Any]:
    require_file(path)
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise invalid(f"invalid JSON artifact {path.name}: {error}") from error
    if not isinstance(document, dict):
        raise invalid(f"JSON artifact must be an object: {path.name}")
    return document


def load_csv(path: Path, expected_fields: Iterable[str]) -> list[dict[str, str]]:
    require_file(path)
    try:
        with path.open(newline="", encoding="utf-8") as source:
            reader = csv.DictReader(source)
            if tuple(reader.fieldnames or ()) != tuple(expected_fields):
                raise invalid(
                    f"unexpected CSV fields in {path.name}: {reader.fieldnames}"
                )
            return list(reader)
    except (OSError, UnicodeDecodeError, csv.Error) as error:
        raise invalid(f"invalid CSV artifact {path.name}: {error}") from error


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    require_file(path)
    records: list[dict[str, Any]] = []
    try:
        with path.open(encoding="utf-8") as source:
            for line_number, line in enumerate(source, start=1):
                if not line.strip():
                    raise invalid(f"blank JSONL record in {path.name}:{line_number}")
                try:
                    record = json.loads(line)
                except json.JSONDecodeError as error:
                    raise invalid(
                        f"invalid JSONL record in {path.name}:{line_number}: {error}"
                    ) from error
                if not isinstance(record, dict):
                    raise invalid(
                        f"JSONL record must be an object in {path.name}:{line_number}"
                    )
                records.append(record)
    except (OSError, UnicodeDecodeError) as error:
        raise invalid(f"invalid JSONL artifact {path.name}: {error}") from error
    return records


def require_int(record: dict[str, Any], field: str, context: str) -> int:
    value = record.get(field)
    if isinstance(value, bool) or not isinstance(value, int):
        raise invalid(f"{context}: {field} must be an integer")
    return value


def require_positive_int(record: dict[str, Any], field: str, context: str) -> int:
    value = require_int(record, field, context)
    if value <= 0:
        raise invalid(f"{context}: {field} must be positive")
    return value


def validate_environment(
    run_dir: Path,
    run_id: str,
    git_sha: str,
    source_fingerprint: str,
) -> None:
    environment = load_json(run_dir / "environment.json")
    expected = {
        "run_id": run_id,
        "git_sha": git_sha,
        "source_fingerprint_sha256": source_fingerprint,
    }
    for field, expected_value in expected.items():
        if environment.get(field) != expected_value:
            raise invalid(f"environment.json {field} does not match the run identity")

    rows = load_csv(
        run_dir / "database-environment.csv",
        ("category", "name", "value"),
    )
    present = {(row["category"], row["name"]) for row in rows}
    required = {
        ("database", "version"),
        ("database", "postgis"),
        ("database", "current_database"),
        ("database", "current_user"),
    }
    if not required.issubset(present):
        raise invalid("database-environment.csv lacks required database identity rows")


def validate_cardinalities(run_dir: Path, spec: RunSpec) -> dict[str, int]:
    rows = load_csv(run_dir / "cardinalities.csv", CARDINALITY_FIELDS)
    expected = set(spec.nearby_scenarios)
    actual = [row["scenario_id"] for row in rows]
    if len(actual) != len(expected) or set(actual) != expected:
        raise invalid("cardinalities.csv does not contain each declared scenario exactly once")

    cardinalities: dict[str, int] = {}
    for row in rows:
        scenario = row["scenario_id"]
        try:
            rows_returned = int(row["rows_returned"])
            distinct_persons = int(row["distinct_persons"])
            float(row["latitude"])
            float(row["longitude"])
            radius = float(row["radius_km"])
        except ValueError as error:
            raise invalid(f"cardinalities.csv has invalid numerics for {scenario}") from error
        if rows_returned < 0 or distinct_persons != rows_returned or radius <= 0:
            raise invalid(f"cardinalities.csv has invalid counts for {scenario}")
        cardinalities[scenario] = rows_returned
    return cardinalities


def validate_oracle(run_dir: Path, cardinalities: dict[str, int]) -> int:
    rows = load_csv(run_dir / "nearby-http-oracle.csv", ORACLE_FIELDS)
    by_scenario: dict[str, list[int]] = defaultdict(list)
    ids_by_scenario: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        scenario = row["scenario_id"]
        if scenario not in cardinalities:
            raise invalid(f"nearby HTTP oracle contains unknown scenario {scenario}")
        try:
            order = int(row["result_order"])
            float(row["latitude"])
            float(row["longitude"])
            float(row["distance_km"])
        except ValueError as error:
            raise invalid(f"nearby HTTP oracle has invalid values for {scenario}") from error
        if row["id"] in ids_by_scenario[scenario]:
            raise invalid(f"nearby HTTP oracle duplicates a person for {scenario}")
        ids_by_scenario[scenario].add(row["id"])
        by_scenario[scenario].append(order)

    for scenario, expected_rows in cardinalities.items():
        orders = sorted(by_scenario.get(scenario, []))
        if orders != list(range(1, expected_rows + 1)):
            raise invalid(f"nearby HTTP oracle is incomplete for {scenario}")
    return len(rows)


def validate_pgbench_line(
    line: str,
    path: Path,
    line_number: int,
) -> tuple[int, int]:
    fields = line.split()
    if len(fields) < 6:
        raise invalid(f"malformed pgbench log line in {path.name}:{line_number}")
    try:
        client_id = int(fields[0])
        transaction_number = int(fields[1])
        float(fields[2])
        int(fields[4])
        int(fields[5])
    except ValueError as error:
        raise invalid(f"malformed pgbench values in {path.name}:{line_number}") from error
    if client_id < 0 or transaction_number < 0:
        raise invalid(f"negative pgbench key in {path.name}:{line_number}")
    return client_id, transaction_number


def pgbench_log_keys(path: Path) -> set[tuple[int, int]]:
    require_file(path)
    keys: set[tuple[int, int]] = set()
    try:
        with path.open(encoding="utf-8") as source:
            for line_number, line in enumerate(source, start=1):
                if not line.strip():
                    raise invalid(f"blank pgbench log line in {path.name}:{line_number}")
                key = validate_pgbench_line(line, path, line_number)
                if key in keys:
                    raise invalid(
                        f"duplicate pgbench client/transaction key in "
                        f"{path.name}:{line_number}"
                    )
                keys.add(key)
    except (OSError, UnicodeDecodeError) as error:
        raise invalid(f"invalid pgbench log {path.name}: {error}") from error
    return keys


def validate_database_measurements(run_dir: Path, spec: RunSpec) -> dict[str, int]:
    expected_by_category: dict[str, dict[str, int]] = defaultdict(dict)
    for workload in spec.database_workloads:
        expected_by_category[workload.category][workload.label] = (
            workload.samples_per_repeat
        )

    measured: dict[str, int] = {}
    for category, expected_workloads in expected_by_category.items():
        directory = run_dir / category
        if not directory.is_dir():
            raise invalid(f"required raw artifact directory is missing: {category}")

        log_keys: dict[tuple[str, int], set[tuple[int, int]]] = defaultdict(set)
        summaries: set[tuple[str, int]] = set()
        for path in directory.iterdir():
            if not path.is_file():
                raise invalid(f"unexpected non-file artifact in {category}: {path.name}")
            summary_match = re.fullmatch(r"(.+)-r(\d+)-summary\.txt", path.name)
            if summary_match:
                label = summary_match.group(1)
                repeat = int(summary_match.group(2))
                if label not in expected_workloads or not 1 <= repeat <= spec.repeats:
                    raise invalid(f"unexpected pgbench summary: {category}/{path.name}")
                require_file(path)
                key = (label, repeat)
                if key in summaries:
                    raise invalid(f"duplicate pgbench summary: {category}/{path.name}")
                summaries.add(key)
                continue

            log_match = PGBENCH_LOG.fullmatch(path.name)
            if log_match:
                label = log_match.group("label")
                repeat = int(log_match.group("repeat"))
                if label not in expected_workloads or not 1 <= repeat <= spec.repeats:
                    raise invalid(f"unexpected pgbench log: {category}/{path.name}")
                key = (label, repeat)
                file_keys = pgbench_log_keys(path)
                overlap = log_keys[key] & file_keys
                if overlap:
                    raise invalid(
                        f"duplicate pgbench client/transaction keys across "
                        f"{category}/{label} r{repeat} logs"
                    )
                log_keys[key].update(file_keys)
                continue
            raise invalid(f"unexpected raw artifact in {category}: {path.name}")

        for label, expected_samples in expected_workloads.items():
            total = 0
            for repeat in range(1, spec.repeats + 1):
                key = (label, repeat)
                if key not in summaries:
                    raise invalid(f"missing pgbench summary for {category}/{label} r{repeat}")
                observed_samples = len(log_keys[key])
                if observed_samples != expected_samples:
                    raise invalid(
                        f"{category}/{label} r{repeat} has {observed_samples} "
                        f"samples; expected {expected_samples}"
                    )
                total += observed_samples
            measured[f"{category}/{label}"] = total
    return measured


def validate_restart_measurement(run_dir: Path) -> int:
    directory = run_dir / "restart-cold-db"
    if not directory.is_dir():
        raise invalid("required raw artifact directory is missing: restart-cold-db")
    require_file(directory / "summary.txt")
    log_files = [
        path
        for path in directory.iterdir()
        if re.fullmatch(r"restart-cold-\.\d+(?:\.\d+)?", path.name)
    ]
    unexpected = {
        path.name
        for path in directory.iterdir()
        if path.name != "summary.txt" and path not in log_files
    }
    if unexpected:
        raise invalid(f"unexpected restart-first artifacts: {sorted(unexpected)}")
    keys: set[tuple[int, int]] = set()
    for path in log_files:
        file_keys = pgbench_log_keys(path)
        if keys & file_keys:
            raise invalid("restart-first measurement contains duplicate sample keys")
        keys.update(file_keys)
    if len(keys) != 1:
        raise invalid(
            f"restart-first measurement has {len(keys)} samples; expected 1"
        )
    return len(keys)


def validate_http_common(
    record: dict[str, Any],
    context: str,
    cardinalities: dict[str, int],
) -> str:
    scenario = record.get("scenario_id")
    if not isinstance(scenario, str) or scenario not in cardinalities:
        raise invalid(f"{context}: unknown scenario_id")
    if require_int(record, "status", context) != 200:
        raise invalid(f"{context}: status must be 200")
    if require_int(record, "rows_returned", context) != cardinalities[scenario]:
        raise invalid(f"{context}: rows_returned does not match cardinalities.csv")
    require_positive_int(record, "started_at_ns", context)
    require_positive_int(record, "elapsed_ns", context)
    require_positive_int(record, "response_bytes", context)
    return scenario


def validate_http_latency(
    run_dir: Path,
    spec: RunSpec,
    cardinalities: dict[str, int],
) -> int:
    records = load_jsonl(run_dir / "nearby-http.jsonl")
    seen: set[tuple[str, int, int]] = set()
    for index, record in enumerate(records, start=1):
        context = f"nearby-http.jsonl record {index}"
        scenario = validate_http_common(record, context, cardinalities)
        if record.get("mode") != "latency" or require_int(
            record, "concurrency", context
        ) != 1:
            raise invalid(f"{context}: expected latency mode at concurrency 1")
        repeat = require_int(record, "repeat", context)
        iteration = require_int(record, "iteration", context)
        key = (scenario, repeat, iteration)
        if key in seen:
            raise invalid(f"{context}: duplicate scenario/repeat/iteration")
        seen.add(key)

    expected = {
        (scenario, repeat, iteration)
        for scenario in spec.nearby_scenarios
        for repeat in range(1, spec.repeats + 1)
        for iteration in range(1, spec.nearby_iterations_per_repeat + 1)
    }
    if seen != expected:
        missing = len(expected - seen)
        extra = len(seen - expected)
        raise invalid(
            f"nearby HTTP latency coverage differs from the plan "
            f"(missing={missing}, extra={extra})"
        )
    return len(records)


def validate_http_throughput(
    run_dir: Path,
    spec: RunSpec,
    cardinalities: dict[str, int],
) -> tuple[int, dict[str, int]]:
    records = load_jsonl(run_dir / "nearby-http-throughput.jsonl")
    iterations: dict[tuple[str, int, int, int], set[int]] = defaultdict(set)
    group_windows: dict[tuple[str, int, int], list[tuple[int, int]]] = defaultdict(
        list
    )
    for index, record in enumerate(records, start=1):
        context = f"nearby-http-throughput.jsonl record {index}"
        scenario = validate_http_common(record, context, cardinalities)
        concurrency = require_int(record, "concurrency", context)
        repeat = require_int(record, "repeat", context)
        worker = require_int(record, "worker", context)
        iteration = require_positive_int(record, "iteration", context)
        if (
            record.get("mode") != "throughput"
            or scenario not in spec.nearby_throughput_scenarios
            or concurrency not in spec.throughput_concurrencies
            or not 1 <= repeat <= spec.repeats
            or not 0 <= worker < concurrency
            or require_int(record, "duration_seconds", context)
            != spec.throughput_duration_seconds
        ):
            raise invalid(f"{context}: throughput plan fields are invalid")
        key = (scenario, concurrency, repeat, worker)
        if iteration in iterations[key]:
            raise invalid(f"{context}: duplicate worker iteration")
        iterations[key].add(iteration)
        started_at_ns = require_positive_int(record, "started_at_ns", context)
        elapsed_ns = require_positive_int(record, "elapsed_ns", context)
        group_windows[(scenario, concurrency, repeat)].append(
            (started_at_ns, started_at_ns + elapsed_ns)
        )

    expected_workers = {
        (scenario, concurrency, repeat, worker)
        for scenario in spec.nearby_throughput_scenarios
        for concurrency in spec.throughput_concurrencies
        for repeat in range(1, spec.repeats + 1)
        for worker in range(concurrency)
    }
    if set(iterations) != expected_workers:
        missing = len(expected_workers - set(iterations))
        extra = len(set(iterations) - expected_workers)
        raise invalid(
            f"nearby HTTP throughput coverage differs from the plan "
            f"(missing_workers={missing}, extra_workers={extra})"
        )
    for key, observed in iterations.items():
        if observed != set(range(1, max(observed) + 1)):
            raise invalid(f"throughput worker iterations are not contiguous: {key}")

    minimum_span_ns = int(spec.throughput_duration_seconds * 1_000_000_000 * 0.95)
    for group, windows in group_windows.items():
        observed_span_ns = max(end for _start, end in windows) - min(
            start for start, _end in windows
        )
        if observed_span_ns < minimum_span_ns:
            raise invalid(
                f"throughput group {group} spans {observed_span_ns} ns; "
                f"expected at least {minimum_span_ns} ns"
            )

    group_counts: Counter[tuple[str, int, int]] = Counter()
    for key, values in iterations.items():
        group_counts[key[:3]] += len(values)
    rendered = {
        f"{scenario}/c{concurrency}/r{repeat}": count
        for (scenario, concurrency, repeat), count in sorted(group_counts.items())
    }
    return len(records), rendered


def validate_http_writes(run_dir: Path, spec: RunSpec) -> int:
    fixtures = load_csv(
        run_dir / "http-write-fixtures.csv",
        HTTP_WRITE_FIXTURE_FIELDS,
    )
    write_ranges = {
        scenario: (first, last)
        for scenario, first, last in spec.write_http_ordinals
    }
    fixture_expected = {
        scenario: set(range(write_ranges[scenario][0], write_ranges[scenario][1] + 1))
        for scenario in ("client-replay", "no-key-replay")
    }
    fixture_actual: dict[str, set[int]] = defaultdict(set)
    for row in fixtures:
        try:
            ordinal = int(row["person_ordinal"])
        except ValueError as error:
            raise invalid("http-write-fixtures.csv contains an invalid ordinal") from error
        scenario = row["scenario"]
        if ordinal in fixture_actual[scenario]:
            raise invalid(
                f"http-write-fixtures.csv duplicates ordinal {ordinal} for {scenario}"
            )
        fixture_actual[scenario].add(ordinal)
    if dict(fixture_actual) != fixture_expected:
        raise invalid("http-write-fixtures.csv does not contain its exact replay cohorts")

    records = load_jsonl(run_dir / "writes-http.jsonl")
    actual: dict[str, set[int]] = defaultdict(set)
    for index, record in enumerate(records, start=1):
        context = f"writes-http.jsonl record {index}"
        scenario = record.get("scenario")
        if not isinstance(scenario, str):
            raise invalid(f"{context}: scenario must be a string")
        ordinal = require_int(record, "person_ordinal", context)
        if ordinal in actual[scenario]:
            raise invalid(f"{context}: duplicate person ordinal for {scenario}")
        actual[scenario].add(ordinal)
        if require_int(record, "status", context) != 200:
            raise invalid(f"{context}: status must be 200")
        if require_int(record, "concurrency", context) != spec.write_http_concurrency:
            raise invalid(
                f"{context}: concurrency must be {spec.write_http_concurrency}"
            )
        require_positive_int(record, "started_at_ns", context)
        require_positive_int(record, "elapsed_ns", context)
        require_positive_int(record, "response_bytes", context)

    expected = {
        scenario: set(range(first, last + 1))
        for scenario, first, last in spec.write_http_ordinals
    }
    if dict(actual) != expected:
        raise invalid("writes-http.jsonl does not contain its exact write cohorts")
    return len(records)


def parse_write_snapshot(path: Path) -> dict[str, Any]:
    rows = load_csv(path, WRITE_COUNT_FIELDS)
    if len(rows) != 1:
        raise invalid(f"{path.name} must contain exactly one snapshot row")
    row = rows[0]
    parsed: dict[str, Any] = {}
    for field in WRITE_COUNT_FIELDS[:5]:
        try:
            value = int(row[field])
        except ValueError as error:
            raise invalid(f"{path.name} has invalid integer field {field}") from error
        if value < 0:
            raise invalid(f"{path.name} has negative field {field}")
        parsed[field] = value
    if not re.fullmatch(r"[0-9A-Fa-f]+/[0-9A-Fa-f]+", row["current_wal_lsn"]):
        raise invalid(f"{path.name} has invalid current_wal_lsn")
    parsed["current_wal_lsn"] = row["current_wal_lsn"]
    try:
        parsed["captured_at"] = dt.datetime.fromisoformat(
            row["captured_at"].replace("Z", "+00:00")
        )
    except ValueError as error:
        raise invalid(f"{path.name} has invalid captured_at") from error
    return parsed


def validate_write_counts(run_dir: Path, spec: RunSpec) -> int:
    snapshots = {
        filename: parse_write_snapshot(run_dir / filename)
        for filename in WRITE_COUNT_FILES
    }
    ordered = [snapshots[filename] for filename in WRITE_COUNT_FILES]
    if len({snapshot["persons"] for snapshot in ordered}) != 1:
        raise invalid("write measurements changed the person count")
    if len({snapshot["projections"] for snapshot in ordered}) != 1:
        raise invalid("write measurements changed the projection count")

    database_totals = {
        workload.label: workload.samples_per_repeat * spec.repeats
        for workload in spec.database_workloads
        if workload.category == "writes-db"
    }
    required_database_workloads = {
        "winning-append",
        "late-append",
        "client-replay",
        "no-key-replay",
    }
    if set(database_totals) != required_database_workloads:
        raise invalid("write database workload plan is incomplete")
    http_totals = {
        scenario: last - first + 1
        for scenario, first, last in spec.write_http_ordinals
    }
    required_http_workloads = required_database_workloads
    if set(http_totals) != required_http_workloads:
        raise invalid("write HTTP workload plan is incomplete")

    before, after_winning, after_late, after_client, after_db, after_http = ordered
    expected_observations = (
        before["observations"],
        before["observations"] + database_totals["winning-append"],
        before["observations"]
        + database_totals["winning-append"]
        + database_totals["late-append"],
    )
    if (
        after_winning["observations"] != expected_observations[1]
        or after_late["observations"] != expected_observations[2]
        or after_client["observations"] != expected_observations[2]
        or after_db["observations"] != expected_observations[2]
        or after_http["observations"]
        != expected_observations[2]
        + http_totals["winning-append"]
        + http_totals["late-append"]
    ):
        raise invalid("write snapshot observation deltas differ from the workload plan")

    observation_deltas = [
        snapshot["observations"] - before["observations"]
        for snapshot in ordered
    ]
    client_update_deltas = [
        snapshot["client_updates"] - before["client_updates"]
        for snapshot in ordered
    ]
    if client_update_deltas != observation_deltas:
        raise invalid("write snapshot client-update deltas differ from inserts")

    winning_projection_counts = [
        snapshot["benchmark_winning_projection_rows"] for snapshot in ordered
    ]
    if not (
        winning_projection_counts[0] <= winning_projection_counts[1]
        and winning_projection_counts[1]
        == winning_projection_counts[2]
        == winning_projection_counts[3]
        == winning_projection_counts[4]
        and winning_projection_counts[4] <= winning_projection_counts[5]
    ):
        raise invalid("write snapshot winning-projection deltas are inconsistent")
    if winning_projection_counts[1] - winning_projection_counts[0] > database_totals[
        "winning-append"
    ]:
        raise invalid("database winning workload advanced too many projections")
    if winning_projection_counts[5] - winning_projection_counts[4] > http_totals[
        "winning-append"
    ]:
        raise invalid("HTTP winning workload advanced too many projections")

    captured = [snapshot["captured_at"] for snapshot in ordered]
    if captured != sorted(captured):
        raise invalid("write snapshot capture times are not monotonic")
    for filename in WRITE_COUNT_FILES:
        del snapshots[filename]
    return len(WRITE_COUNT_FILES)


def validate_plans(run_dir: Path, spec: RunSpec) -> int:
    directory = run_dir / "plans"
    if not directory.is_dir():
        raise invalid("required raw artifact directory is missing: plans")
    actual = {path.name for path in directory.iterdir() if path.is_file()}
    expected = set(spec.plan_files)
    if actual != expected:
        raise invalid(
            f"plan artifacts differ from the plan "
            f"(missing={sorted(expected - actual)}, extra={sorted(actual - expected)})"
        )
    for filename in spec.plan_files:
        path = directory / filename
        require_file(path)
        try:
            plan = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
            raise invalid(f"invalid JSON plan {filename}: {error}") from error
        if not isinstance(plan, list) or not plan:
            raise invalid(f"plan must be a nonempty JSON array: {filename}")
    return len(spec.plan_files)


def raw_artifact_hashes(run_dir: Path) -> dict[str, str]:
    excluded = {
        ".raw-completeness.json.tmp",
        "manifest.json",
        "raw-completeness.json",
        "summary.md",
    }
    hashes: dict[str, str] = {}
    for path in sorted(run_dir.rglob("*"), key=lambda item: item.as_posix()):
        relative = path.relative_to(run_dir).as_posix()
        if path.is_symlink():
            raise invalid(f"raw artifact must not be a symbolic link: {relative}")
        if not path.is_file() or relative in excluded:
            continue
        digest = hashlib.sha256()
        try:
            with path.open("rb") as source:
                while chunk := source.read(1024 * 1024):
                    digest.update(chunk)
        except OSError as error:
            raise invalid(f"cannot hash raw artifact {relative}: {error}") from error
        hashes[relative] = digest.hexdigest()
    return hashes


def validate_raw_artifacts(
    run_dir: Path,
    run_id: str,
    git_sha: str,
    source_fingerprint: str,
    spec: RunSpec | None = None,
) -> dict[str, Any]:
    active_spec = spec or PRODUCTION_SPEC
    if not run_dir.is_dir():
        raise invalid(f"run directory does not exist: {run_dir}")
    if run_dir.name != run_id:
        raise invalid("run identity does not match the result directory name")
    if not re.fullmatch(r"[0-9a-f]{40}", git_sha):
        raise invalid("run Git SHA must be a full lowercase commit ID")
    if not re.fullmatch(r"[0-9a-f]{64}", source_fingerprint):
        raise invalid("run source fingerprint must be a lowercase SHA-256")

    validate_environment(run_dir, run_id, git_sha, source_fingerprint)
    cardinalities = validate_cardinalities(run_dir, active_spec)
    oracle_rows = validate_oracle(run_dir, cardinalities)
    database_samples = validate_database_measurements(run_dir, active_spec)
    restart_samples = validate_restart_measurement(run_dir)
    nearby_http_samples = validate_http_latency(run_dir, active_spec, cardinalities)
    throughput_samples, throughput_groups = validate_http_throughput(
        run_dir,
        active_spec,
        cardinalities,
    )
    write_http_samples = validate_http_writes(run_dir, active_spec)
    write_snapshots = validate_write_counts(run_dir, active_spec)
    plan_count = validate_plans(run_dir, active_spec)

    return {
        "schema_version": 1,
        "run_id": run_id,
        "git_sha": git_sha,
        "source_fingerprint": source_fingerprint,
        "counts": {
            "nearby_scenarios": len(cardinalities),
            "nearby_oracle_rows": oracle_rows,
            "restart_first_database_samples": restart_samples,
            "nearby_http_latency_samples": nearby_http_samples,
            "nearby_http_throughput_samples": throughput_samples,
            "write_http_samples": write_http_samples,
            "write_count_snapshots": write_snapshots,
            "plans": plan_count,
        },
        "database_samples": dict(sorted(database_samples.items())),
        "throughput_group_samples": throughput_groups,
        "artifact_sha256": raw_artifact_hashes(run_dir),
    }


def validate_completed_run(
    run_dir: Path,
    spec: RunSpec | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    manifest = load_json(run_dir / "manifest.json")
    if (
        manifest.get("schema_version") != 2
        or manifest.get("phase") != "run"
        or manifest.get("completed") is not True
        or manifest.get("raw_completeness") != "raw-completeness.json"
    ):
        raise invalid("raw run manifest is not a completed schema-2 run")
    run_id = manifest.get("run_id")
    git_sha = manifest.get("git_sha")
    source_fingerprint = manifest.get("source_fingerprint")
    if not all(isinstance(value, str) for value in (run_id, git_sha, source_fingerprint)):
        raise invalid("raw run manifest identity fields must be strings")

    report = validate_raw_artifacts(
        run_dir,
        run_id,
        git_sha,
        source_fingerprint,
        spec,
    )
    recorded = load_json(run_dir / "raw-completeness.json")
    if recorded != report:
        raise invalid("raw-completeness.json does not match the current raw artifacts")
    return manifest, report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", required=True, type=Path)
    parser.add_argument("--pre-completion", action="store_true")
    parser.add_argument("--run-id")
    parser.add_argument("--git-sha")
    parser.add_argument("--source-fingerprint")
    args = parser.parse_args()

    try:
        if args.pre_completion:
            if not all((args.run_id, args.git_sha, args.source_fingerprint)):
                parser.error(
                    "--pre-completion requires --run-id, --git-sha, and "
                    "--source-fingerprint"
                )
            report = validate_raw_artifacts(
                args.run_dir,
                args.run_id,
                args.git_sha,
                args.source_fingerprint,
            )
        else:
            if any((args.run_id, args.git_sha, args.source_fingerprint)):
                parser.error("identity options are valid only with --pre-completion")
            _manifest, report = validate_completed_run(args.run_dir)
    except RawRunValidationError as error:
        raise SystemExit(f"raw benchmark evidence is incomplete: {error}") from error

    print(json.dumps(report, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
