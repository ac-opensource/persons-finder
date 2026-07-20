#!/usr/bin/env python3
"""Measure core HTTP write-path variants and verify their state semantics."""

from __future__ import annotations

import argparse
import concurrent.futures
import csv
import datetime as dt
import hashlib
import json
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


def deterministic_uuid(namespace: str, ordinal: int) -> str:
    digest = hashlib.md5(f"{namespace}:{ordinal}".encode(), usedforsecurity=False).hexdigest()
    return (
        f"{digest[0:8]}-{digest[8:12]}-4{digest[13:16]}-"
        f"8{digest[17:20]}-{digest[20:32]}"
    )


def put(base_url: str, person_id: str, body: dict[str, Any]) -> dict[str, Any]:
    encoded = json.dumps(body, separators=(",", ":")).encode()
    request = urllib.request.Request(
        f"{base_url}/persons/{person_id}/location",
        data=encoded,
        method="PUT",
        headers={"Content-Type": "application/json"},
    )
    started_at_ns = time.time_ns()
    started = time.perf_counter_ns()
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        error.read()
        raise RuntimeError(f"PUT {person_id} returned HTTP {error.code}") from error
    elapsed_ns = time.perf_counter_ns() - started
    decoded = json.loads(payload)
    if status != 200 or not isinstance(decoded, dict):
        raise RuntimeError(f"PUT {person_id} did not return an accepted response")
    return {
        "started_at_ns": started_at_ns,
        "status": status,
        "elapsed_ns": elapsed_ns,
        "response_bytes": len(payload),
        "response": decoded,
    }


def load_fixtures(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as source:
        return list(csv.DictReader(source))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--fixtures", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--concurrency", type=int, default=8)
    args = parser.parse_args()

    fixtures = load_fixtures(args.fixtures)
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    operations: list[dict[str, Any]] = []

    for fixture in fixtures:
        operations.append(dict(fixture))

    for ordinal in range(902001, 903001):
        operations.append(
            {
                "scenario": "winning-append",
                "person_ordinal": str(ordinal),
                "person_id": deterministic_uuid("person", ordinal),
                "client_update_id": deterministic_uuid("http-winning-client", ordinal),
                "captured_at": now.strftime("%Y-%m-%dT%H:%M:%S.000Z"),
                "latitude": str(-33.86 + (ordinal % 1000) * 0.000001),
                "longitude": str(151.20 + (ordinal % 1000) * 0.000001),
                "expected_observation_id": "",
            }
        )

    for ordinal in range(903001, 904001):
        operations.append(
            {
                "scenario": "late-append",
                "person_ordinal": str(ordinal),
                "person_id": deterministic_uuid("person", ordinal),
                "client_update_id": deterministic_uuid("http-late-client", ordinal),
                "captured_at": "2024-01-01T00:00:00.000Z",
                "latitude": str(1.35 + (ordinal % 1000) * 0.000001),
                "longitude": str(103.80 + (ordinal % 1000) * 0.000001),
                "expected_observation_id": "",
            }
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lock = threading.Lock()

    def execute(operation: dict[str, Any]) -> None:
        scenario = operation["scenario"]
        body: dict[str, Any] = {
            "latitude": float(operation["latitude"]),
            "longitude": float(operation["longitude"]),
        }
        if scenario != "no-key-replay":
            body["capturedAt"] = operation["captured_at"]
            body["clientUpdateId"] = operation["client_update_id"]
        measurement = put(args.base_url, operation["person_id"], body)
        response = measurement.pop("response")
        expected_observation_id = operation.get("expected_observation_id", "")

        if scenario in ("client-replay", "no-key-replay"):
            if response["observationId"] != expected_observation_id:
                raise RuntimeError(f"{scenario}: replay returned a different observation")
        elif scenario == "winning-append":
            if response["observationId"] != response["lastKnownObservationId"]:
                raise RuntimeError("winning append did not advance the projection")
        elif scenario == "late-append":
            if response["observationId"] == response["lastKnownObservationId"]:
                raise RuntimeError("late append incorrectly advanced the projection")

        record = {
            **measurement,
            "scenario": scenario,
            "person_ordinal": int(operation["person_ordinal"]),
            "person_id": operation["person_id"],
            "observation_id": response["observationId"],
            "last_known_observation_id": response["lastKnownObservationId"],
            "concurrency": args.concurrency,
        }
        line = json.dumps(record, separators=(",", ":"))
        with lock:
            output.write(line + "\n")

    with args.output.open("x", encoding="utf-8") as output:
        with concurrent.futures.ThreadPoolExecutor(
            max_workers=args.concurrency
        ) as executor:
            futures = [executor.submit(execute, operation) for operation in operations]
            for future in futures:
                future.result()
        output.flush()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
