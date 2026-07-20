#!/usr/bin/env python3
"""Capture host and tool metadata without secrets."""

from __future__ import annotations

import argparse
import json
import os
import platform
import subprocess
import sys
import time
from pathlib import Path


def command(*args: str) -> dict[str, object]:
    completed = subprocess.run(
        args,
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    return {
        "command": list(args),
        "exit_code": completed.returncode,
        "stdout": completed.stdout.strip(),
        "stderr": completed.stderr.strip(),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--git-sha", required=True)
    parser.add_argument("--source-fingerprint", required=True)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()

    document = {
        "schema_version": 1,
        "run_id": args.run_id,
        "captured_at_unix_ns": time.time_ns(),
        "git_sha": args.git_sha,
        "source_fingerprint_sha256": args.source_fingerprint,
        "platform": platform.platform(),
        "machine": platform.machine(),
        "processor": platform.processor(),
        "python": sys.version,
        "cpu_count": os.cpu_count(),
        "commands": {
            "uname": command("uname", "-a"),
            "docker": command("docker", "version"),
            "compose": command("docker", "compose", "version"),
            "git_status": command("git", "status", "--short", "--branch"),
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as output:
        json.dump(document, output, indent=2, sort_keys=True)
        output.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
