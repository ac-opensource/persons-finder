#!/usr/bin/env python3
"""Record raw core-HTTP nearby timings with response correctness checks."""

from __future__ import annotations

import argparse
import concurrent.futures
import csv
import json
import math
import threading
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Scenario:
    scenario_id: str
    latitude: float
    longitude: float
    radius_km: float
    expected_rows: int


@dataclass(frozen=True)
class ExpectedNearby:
    person_id: str
    latitude: float
    longitude: float
    distance_km: float


def load_scenarios(path: Path) -> dict[str, Scenario]:
    scenarios: dict[str, Scenario] = {}
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            scenario = Scenario(
                scenario_id=row["scenario_id"],
                latitude=float(row["latitude"]),
                longitude=float(row["longitude"]),
                radius_km=float(row["radius_km"]),
                expected_rows=int(row["rows_returned"]),
            )
            scenarios[scenario.scenario_id] = scenario
    return scenarios


def load_oracle(path: Path) -> dict[str, tuple[ExpectedNearby, ...]]:
    expected: dict[str, list[ExpectedNearby]] = {}
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            expected.setdefault(row["scenario_id"], []).append(
                ExpectedNearby(
                    person_id=row["id"],
                    latitude=float(row["latitude"]),
                    longitude=float(row["longitude"]),
                    distance_km=float(row["distance_km"]),
                )
            )
    return {
        scenario_id: tuple(items)
        for scenario_id, items in expected.items()
    }


def require_number(value: object, field: str, scenario_id: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise RuntimeError(f"{scenario_id}: {field} must be a JSON number")
    numeric = float(value)
    if not math.isfinite(numeric):
        raise RuntimeError(f"{scenario_id}: {field} must be finite")
    return numeric


def validate_item_shape(item: object, scenario_id: str) -> dict[str, object]:
    if not isinstance(item, dict):
        raise RuntimeError(f"{scenario_id}: nearby item must be a JSON object")
    required_fields = {
        "id",
        "name",
        "jobTitle",
        "hobbies",
        "bio",
        "createdAt",
        "lastKnownLocationAt",
        "location",
        "distanceKm",
    }
    if set(item) != required_fields:
        raise RuntimeError(
            f"{scenario_id}: nearby item fields differ from the API contract"
        )
    for field in ("id", "name", "jobTitle", "bio", "createdAt", "lastKnownLocationAt"):
        if not isinstance(item[field], str):
            raise RuntimeError(f"{scenario_id}: {field} must be a JSON string")
    hobbies = item["hobbies"]
    if not isinstance(hobbies, list) or not all(
        isinstance(hobby, str) for hobby in hobbies
    ):
        raise RuntimeError(f"{scenario_id}: hobbies must be a JSON string array")
    location = item["location"]
    if not isinstance(location, dict) or set(location) != {"latitude", "longitude"}:
        raise RuntimeError(
            f"{scenario_id}: location must contain exactly latitude and longitude"
        )
    return item


def validate_response(
    decoded: list[object],
    scenario: Scenario,
    expected: tuple[ExpectedNearby, ...],
) -> None:
    if len(expected) != scenario.expected_rows:
        raise RuntimeError(
            f"{scenario.scenario_id}: oracle has {len(expected)} rows, "
            f"cardinality file has {scenario.expected_rows}"
        )
    if len(decoded) != len(expected):
        raise RuntimeError(
            f"{scenario.scenario_id}: expected {len(expected)} rows, got {len(decoded)}"
        )

    seen_ids: set[str] = set()
    for result_order, (raw_item, oracle_item) in enumerate(
        zip(decoded, expected),
        start=1,
    ):
        item = validate_item_shape(raw_item, scenario.scenario_id)
        person_id = item["id"]
        if person_id in seen_ids:
            raise RuntimeError(
                f"{scenario.scenario_id}: duplicate person at result {result_order}"
            )
        seen_ids.add(person_id)
        if person_id != oracle_item.person_id:
            raise RuntimeError(
                f"{scenario.scenario_id}: wrong person or order at result {result_order}"
            )

        location = item["location"]
        assert isinstance(location, dict)
        latitude = require_number(
            location["latitude"],
            f"location.latitude at result {result_order}",
            scenario.scenario_id,
        )
        longitude = require_number(
            location["longitude"],
            f"location.longitude at result {result_order}",
            scenario.scenario_id,
        )
        distance_km = require_number(
            item["distanceKm"],
            f"distanceKm at result {result_order}",
            scenario.scenario_id,
        )
        if not math.isclose(latitude, oracle_item.latitude, abs_tol=1e-12):
            raise RuntimeError(
                f"{scenario.scenario_id}: wrong latitude at result {result_order}"
            )
        if not math.isclose(longitude, oracle_item.longitude, abs_tol=1e-12):
            raise RuntimeError(
                f"{scenario.scenario_id}: wrong longitude at result {result_order}"
            )
        if not math.isclose(distance_km, oracle_item.distance_km, abs_tol=1e-9):
            raise RuntimeError(
                f"{scenario.scenario_id}: wrong distance at result {result_order}"
            )


def fetch(
    base_url: str,
    scenario: Scenario,
    expected: tuple[ExpectedNearby, ...],
) -> dict[str, object]:
    query = urllib.parse.urlencode(
        {
            "lat": scenario.latitude,
            "lon": scenario.longitude,
            "radius": scenario.radius_km,
        }
    )
    started_at_ns = time.time_ns()
    started = time.perf_counter_ns()
    with urllib.request.urlopen(
        f"{base_url}/persons/nearby?{query}",
        timeout=120,
    ) as response:
        payload = response.read()
        status = response.status
    decoded = json.loads(payload)
    elapsed_ns = time.perf_counter_ns() - started
    if status != 200 or not isinstance(decoded, list):
        raise RuntimeError(
            f"{scenario.scenario_id}: expected HTTP 200 JSON array, got {status}"
        )
    validate_response(decoded, scenario, expected)
    return {
        "started_at_ns": started_at_ns,
        "scenario_id": scenario.scenario_id,
        "status": status,
        "elapsed_ns": elapsed_ns,
        "rows_returned": len(decoded),
        "response_bytes": len(payload),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--cardinalities", required=True, type=Path)
    parser.add_argument("--oracle", required=True, type=Path)
    parser.add_argument("--scenario", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--mode", choices=("latency", "throughput"), required=True)
    parser.add_argument("--warmup", type=int, default=25)
    parser.add_argument("--iterations", type=int, default=200)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--concurrency", type=int, default=1)
    parser.add_argument("--duration-seconds", type=int, default=60)
    args = parser.parse_args()

    scenario = load_scenarios(args.cardinalities)[args.scenario]
    expected = load_oracle(args.oracle).get(args.scenario, ())
    args.output.parent.mkdir(parents=True, exist_ok=True)

    for _ in range(args.warmup):
        fetch(args.base_url, scenario, expected)

    write_lock = threading.Lock()
    with args.output.open("a", encoding="utf-8") as output:
        def record(sample: dict[str, object], **extra: object) -> None:
            sample.update(extra)
            line = json.dumps(sample, separators=(",", ":"))
            with write_lock:
                output.write(line + "\n")

        if args.mode == "latency":
            if args.concurrency != 1:
                raise SystemExit("latency mode requires --concurrency 1")
            for repeat in range(1, args.repeats + 1):
                for iteration in range(1, args.iterations + 1):
                    record(
                        fetch(args.base_url, scenario, expected),
                        mode="latency",
                        repeat=repeat,
                        iteration=iteration,
                        concurrency=1,
                    )
                output.flush()
        else:
            for repeat in range(1, args.repeats + 1):
                deadline = time.monotonic() + args.duration_seconds

                def worker(worker_id: int) -> None:
                    iteration = 0
                    while time.monotonic() < deadline:
                        iteration += 1
                        record(
                            fetch(args.base_url, scenario, expected),
                            mode="throughput",
                            repeat=repeat,
                            iteration=iteration,
                            worker=worker_id,
                            concurrency=args.concurrency,
                            duration_seconds=args.duration_seconds,
                        )

                with concurrent.futures.ThreadPoolExecutor(
                    max_workers=args.concurrency
                ) as executor:
                    futures = [
                        executor.submit(worker, worker_id)
                        for worker_id in range(args.concurrency)
                    ]
                    for future in futures:
                        future.result()
                output.flush()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
