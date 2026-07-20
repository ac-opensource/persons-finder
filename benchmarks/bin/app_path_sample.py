#!/usr/bin/env python3
"""Create 100 five-observation trails through the real application HTTP path."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


def request_json(method: str, url: str, body: dict[str, object]) -> tuple[int, dict[str, object], int]:
    encoded = json.dumps(body, separators=(",", ":")).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=encoded,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    started = time.perf_counter_ns()
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        payload = error.read()
        raise RuntimeError(f"{method} {url} returned HTTP {error.code}") from error
    elapsed_ns = time.perf_counter_ns() - started
    decoded = json.loads(payload)
    if not isinstance(decoded, dict):
        raise RuntimeError(f"{method} {url} did not return a JSON object")
    return status, decoded, elapsed_ns


def api_timestamp(value: dt.datetime) -> str:
    return value.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")


def client_update_id(person_number: int, update_number: int) -> str:
    value = person_number * 10 + update_number
    return f"00000000-0000-4000-8000-{value:012d}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--persons", type=int, default=100)
    args = parser.parse_args()

    if args.persons != 100:
        raise SystemExit("The approved application-path sample size is exactly 100")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    base_time = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)

    with args.output.open("x", encoding="utf-8") as output:
        for person_number in range(1, args.persons + 1):
            latitude = -41.2865 + person_number * 0.000001
            longitude = 174.7762 + person_number * 0.000001
            create_body = {
                "name": f"Benchmark Sample {person_number:03d}",
                "jobTitle": "Software engineer",
                "hobbies": ["hiking"],
                "location": {"latitude": latitude, "longitude": longitude},
            }
            status, created, elapsed_ns = request_json(
                "POST",
                f"{args.base_url}/persons",
                create_body,
            )
            if status != 201:
                raise RuntimeError(f"POST person {person_number} returned {status}")
            person_id = str(created["id"])
            uuid.UUID(person_id)
            output.write(
                json.dumps(
                    {
                        "phase": "application-path-sample",
                        "operation": "create",
                        "person_number": person_number,
                        "person_id": person_id,
                        "status": status,
                        "elapsed_ns": elapsed_ns,
                    },
                    separators=(",", ":"),
                )
                + "\n"
            )

            if person_number % 4 == 0:
                offsets = (1, 3, 2, 4)
                cohort = "late-arrival"
            elif person_number % 4 == 1:
                offsets = (1, 2, 4, 4)
                cohort = "received-at-tiebreak"
            else:
                offsets = (1, 2, 3, 4)
                cohort = "chronological"

            for update_number, offset_minutes in enumerate(offsets, start=1):
                update_body = {
                    "latitude": latitude + update_number * 0.00001,
                    "longitude": longitude + update_number * 0.00001,
                    "capturedAt": api_timestamp(
                        base_time + dt.timedelta(minutes=offset_minutes)
                    ),
                    "clientUpdateId": client_update_id(person_number, update_number),
                }
                status, updated, elapsed_ns = request_json(
                    "PUT",
                    f"{args.base_url}/persons/{person_id}/location",
                    update_body,
                )
                if status != 200:
                    raise RuntimeError(
                        f"PUT person {person_number} update {update_number} returned {status}"
                    )
                output.write(
                    json.dumps(
                        {
                            "phase": "application-path-sample",
                            "operation": "update",
                            "cohort": cohort,
                            "person_number": person_number,
                            "update_number": update_number,
                            "person_id": person_id,
                            "observation_id": updated["observationId"],
                            "last_known_observation_id": updated[
                                "lastKnownObservationId"
                            ],
                            "status": status,
                            "elapsed_ns": elapsed_ns,
                        },
                        separators=(",", ":"),
                    )
                    + "\n"
                )
            output.flush()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"application-path sample failed: {error}", file=sys.stderr)
        raise
