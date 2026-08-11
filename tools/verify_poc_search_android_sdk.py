#!/usr/bin/env python3
"""Verify the installed Android system image against POC-SEARCH-001 provenance."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROVENANCE = ROOT / "docs" / "evidence" / "poc-search-001" / "android-system-image-provenance.json"


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--android-home",
        type=Path,
        default=Path(os.environ["ANDROID_HOME"]) if os.environ.get("ANDROID_HOME") else None,
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    require(args.android_home is not None, "ANDROID_HOME is required")
    provenance = json.loads(PROVENANCE.read_text(encoding="utf-8"))
    package_parts = provenance["package"].split(";")
    require(
        package_parts == ["system-images", "android-36", "google_apis", "x86_64"],
        "Unsupported system-image package contract",
    )
    source_properties = (
        args.android_home
        / package_parts[0]
        / package_parts[1]
        / package_parts[2]
        / package_parts[3]
        / "source.properties"
    )
    require(source_properties.is_file(), f"Installed system-image metadata is missing: {source_properties}")
    values = parse_properties(source_properties)
    require(values.get("Pkg.Revision") == str(provenance["revision"]), "Installed system-image revision drift")
    require(values.get("AndroidVersion.ApiLevel") == "36", "Installed system-image API drift")
    require(values.get("SystemImage.Abi") == "x86_64", "Installed system-image ABI drift")
    require(values.get("SystemImage.TagId") == "google_apis", "Installed system-image tag drift")
    print(
        "Validated "
        f"{provenance['package']} revision {provenance['revision']} "
        f"archive sha256:{provenance['archive']['sha256']}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
