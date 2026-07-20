#!/usr/bin/env python3
"""Hash every source/configuration file that can affect a benchmark run."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path


ROOT_FILES = (
    "Dockerfile",
    "Dockerfile.postgis",
    "build.gradle.kts",
    "settings.gradle.kts",
    "compose.yaml",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar",
)


def included_files(root: Path) -> list[Path]:
    files = [root / relative for relative in ROOT_FILES]
    files.extend(path for path in (root / "src").rglob("*") if path.is_file())
    files.extend(
        path
        for path in (root / "benchmarks").rglob("*")
        if path.is_file()
        and "results" not in path.relative_to(root / "benchmarks").parts
    )
    return sorted(set(files), key=lambda path: path.relative_to(root).as_posix())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, type=Path)
    args = parser.parse_args()
    root = args.root.resolve()

    digest = hashlib.sha256()
    for path in included_files(root):
        relative = path.relative_to(root).as_posix().encode()
        content = path.read_bytes()
        digest.update(len(relative).to_bytes(8, "big"))
        digest.update(relative)
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    print(digest.hexdigest())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
