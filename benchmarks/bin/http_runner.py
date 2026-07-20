#!/usr/bin/env python3
"""Record raw core-HTTP nearby timings with response correctness checks."""

from __future__ import annotations

import argparse
import concurrent.futures
import csv
import json
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


def fetch(base_url: str, scenario: Scenario) -> dict[str, object]:
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
    ids = [item["id"] for item in decoded]
    if len(ids) != scenario.expected_rows:
        raise RuntimeError(
            f"{scenario.scenario_id}: expected {scenario.expected_rows} rows, got {len(ids)}"
        )
    if len(ids) != len(set(ids)):
        raise RuntimeError(f"{scenario.scenario_id}: duplicate person in HTTP response")
    return {
        "started_at_ns": started_at_ns,
        "scenario_id": scenario.scenario_id,
        "status": status,
        "elapsed_ns": elapsed_ns,
        "rows_returned": len(ids),
        "response_bytes": len(payload),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--cardinalities", required=True, type=Path)
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
    args.output.parent.mkdir(parents=True, exist_ok=True)

    for _ in range(args.warmup):
        fetch(args.base_url, scenario)

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
                        fetch(args.base_url, scenario),
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
                            fetch(args.base_url, scenario),
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
