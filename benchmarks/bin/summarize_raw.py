#!/usr/bin/env python3
"""Create a concise summary only from an already completed raw run."""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from validate_raw import RawRunValidationError, validate_completed_run


@dataclass(frozen=True)
class Sample:
    latency_ms: float
    started_at: int
    completed_at: int
    repeat: int


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    if not ordered:
        raise ValueError("no samples")
    return ordered[max(0, math.ceil(fraction * len(ordered)) - 1)]


def markdown_table(headers: list[str], rows: Iterable[list[object]]) -> str:
    result = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    result.extend(
        "| " + " | ".join(str(value) for value in row) + " |"
        for row in rows
    )
    return "\n".join(result)


def pgbench_samples(run_dir: Path) -> dict[str, list[Sample]]:
    groups: dict[str, list[Sample]] = defaultdict(list)
    for category in ("nearby-db", "baseline-db", "history-db", "writes-db"):
        directory = run_dir / category
        if not directory.exists():
            continue
        for path in directory.iterdir():
            match = re.match(r"(.+)-r([123])-\.\d+(?:\.\d+)?$", path.name)
            if not match:
                continue
            with path.open(encoding="utf-8") as source:
                for line in source:
                    fields = line.split()
                    if len(fields) >= 6:
                        latency_ms = float(fields[2]) / 1000.0
                        completed_at_us = (
                            int(fields[4]) * 1_000_000
                            + int(fields[5])
                        )
                        groups[f"{category}/{match.group(1)}"].append(
                            Sample(
                                latency_ms=latency_ms,
                                started_at=completed_at_us - round(latency_ms * 1000.0),
                                completed_at=completed_at_us,
                                repeat=int(match.group(2)),
                            )
                        )
    return groups


def jsonl_samples(
    path: Path,
    key_fields: tuple[str, ...],
) -> dict[str, list[Sample]]:
    groups: dict[str, list[Sample]] = defaultdict(list)
    if not path.exists():
        return groups
    with path.open(encoding="utf-8") as source:
        for line in source:
            item = json.loads(line)
            key = "/".join(str(item[field]) for field in key_fields)
            started_at_ns = int(item["started_at_ns"])
            elapsed_ns = int(item["elapsed_ns"])
            groups[key].append(
                Sample(
                    latency_ms=elapsed_ns / 1_000_000.0,
                    started_at=started_at_ns,
                    completed_at=started_at_ns + elapsed_ns,
                    repeat=int(item.get("repeat", 1)),
                )
            )
    return groups


def throughput(samples: list[Sample], timestamp_scale: float) -> str:
    blocks: dict[int, list[Sample]] = defaultdict(list)
    for sample in samples:
        blocks[sample.repeat].append(sample)

    rates: list[float] = []
    for block_samples in blocks.values():
        seconds = (
            max(sample.completed_at for sample in block_samples)
            - min(sample.started_at for sample in block_samples)
        ) / timestamp_scale
        if seconds > 0:
            rates.append(len(block_samples) / seconds)
    return "n/a" if not rates else f"{percentile(rates, 0.50):.2f}"


def metric_rows(
    groups: dict[str, list[Sample]],
    timestamp_scale: float,
) -> list[list[object]]:
    rows: list[list[object]] = []
    for name, samples in sorted(groups.items()):
        latencies = [sample.latency_ms for sample in samples]
        rows.append(
            [
                name,
                len(samples),
                f"{percentile(latencies, 0.50):.3f}",
                f"{percentile(latencies, 0.95):.3f}",
                f"{percentile(latencies, 0.99):.3f}",
                throughput(samples, timestamp_scale),
            ]
        )
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", required=True, type=Path)
    args = parser.parse_args()

    try:
        manifest, _completeness = validate_completed_run(args.run_dir)
    except RawRunValidationError as error:
        raise SystemExit(
            f"raw benchmark evidence is incomplete; refusing to summarize: {error}"
        ) from error

    cardinalities_path = args.run_dir / "cardinalities.csv"
    output_path = args.run_dir / "summary.md"
    if output_path.exists():
        raise SystemExit(f"summary already exists: {output_path}")

    cardinalities: list[list[object]] = []
    with cardinalities_path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            cardinalities.append(
                [
                    row["scenario_id"],
                    row["distribution"],
                    row["radius_km"],
                    row["rows_returned"],
                ]
            )

    http_groups: dict[str, list[Sample]] = defaultdict(list)
    for path in (
        args.run_dir / "nearby-http.jsonl",
        args.run_dir / "nearby-http-throughput.jsonl",
    ):
        for key, samples in jsonl_samples(
            path,
            ("scenario_id", "mode", "concurrency"),
        ).items():
            http_groups[key].extend(samples)

    sections = [
        "# Benchmark summary",
        "",
        f"- Run: `{manifest['run_id']}`",
        f"- Git SHA: `{manifest['git_sha']}`",
        "- Source: raw machine-readable files in this directory",
        "- Restart-first evidence is not a proven cold filesystem cache",
        "- Authenticated trail HTTP remains deferred",
        "",
        "## Cardinalities",
        "",
        markdown_table(
            ["Scenario", "Distribution", "Radius km", "Rows"],
            cardinalities,
        ),
        "",
        "## Database measurements",
        "",
        markdown_table(
            [
                "Workload",
                "Samples",
                "p50 ms",
                "p95 ms",
                "p99 ms",
                "Median block throughput/s",
            ],
            metric_rows(pgbench_samples(args.run_dir), 1_000_000.0),
        ),
        "",
        "## Core nearby HTTP measurements",
        "",
        markdown_table(
            [
                "Workload",
                "Samples",
                "p50 ms",
                "p95 ms",
                "p99 ms",
                "Median block throughput/s",
            ],
            metric_rows(http_groups, 1_000_000_000.0),
        ),
        "",
        "## Core write HTTP measurements",
        "",
        markdown_table(
            [
                "Workload",
                "Samples",
                "p50 ms",
                "p95 ms",
                "p99 ms",
                "Median block throughput/s",
            ],
            metric_rows(
                jsonl_samples(
                    args.run_dir / "writes-http.jsonl",
                    ("scenario",),
                ),
                1_000_000_000.0,
            ),
        ),
        "",
        "Correctness, write deltas, environment data, and plans remain in the",
        "raw files. This summary does not replace those artifacts.",
        "",
    ]
    output_path.write_text("\n".join(sections), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
