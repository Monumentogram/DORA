#!/usr/bin/env python3
"""Generate or verify the durable Actions evidence ledger for POC-SEARCH-001."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from poc_search_environment import classify_android_runtime


POC_ID = "POC-SEARCH-001"
RECORDS = (
    {
        "actionsRunId": "31484440781",
        "artifactId": "9099098018",
        "artifactName": "poc-search-001-evidence-b74aa4f0cc42f65c15502d1018aa68ee3b7b293f",
        "classification": "VALID_FAIL",
        "commit": "b74aa4f0cc42f65c15502d1018aa68ee3b7b293f",
        "expectedResultStatus": "FAIL",
        "targetedArtifactId": "9098780070",
        "targetedArtifactName": "poc-search-001-targeted-b74aa4f0cc42f65c15502d1018aa68ee3b7b293f",
    },
    {
        "actionsRunId": "31486943815",
        "artifactId": "9099733854",
        "artifactName": "poc-search-001-targeted-9571e5f2e7cceeab95b52f1e1167770518a3e475",
        "classification": "TARGETED_PASS",
        "commit": "9571e5f2e7cceeab95b52f1e1167770518a3e475",
        "expectedTargetedPass": True,
    },
    {
        "actionsRunId": "31487775567",
        "artifactId": "9100353133",
        "artifactName": "poc-search-001-evidence-9571e5f2e7cceeab95b52f1e1167770518a3e475",
        "classification": "SUPERSEDED_REVIEW_DEFECTIVE",
        "commit": "9571e5f2e7cceeab95b52f1e1167770518a3e475",
        "expectedResultStatus": "INCONCLUSIVE",
        "supersededReasons": [
            "mandatory storage/update gate was not evaluated while the recommendation claimed GO",
            "raw environment kind contradicted recorded emulator identifiers",
            "dependency evaluation approval was asserted without a complete reviewed inventory",
        ],
        "targetedArtifactId": "9100040873",
        "targetedArtifactName": "poc-search-001-targeted-9571e5f2e7cceeab95b52f1e1167770518a3e475",
    },
)


class LedgerError(RuntimeError):
    """Raised when durable evidence is missing or internally inconsistent."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return "sha256:" + digest.hexdigest()


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def environment_from_file(path: Path, value: dict[str, Any]) -> dict[str, Any] | None:
    if path.name == "benchmark-observations.json" or path.name == "targeted-scale-observations.json":
        environment = value.get("androidEnvironment")
        return environment if isinstance(environment, dict) else None
    if path.name == "environment.json":
        environment = value.get("android")
        return environment if isinstance(environment, dict) else None
    return None


def build_record(repo_root: Path, specification: dict[str, Any]) -> dict[str, Any]:
    run_id = specification["actionsRunId"]
    run_root = repo_root / "docs" / "evidence" / "poc-search-001" / "runs" / run_id
    if not run_root.is_dir():
        raise LedgerError(f"Durable run directory is missing: {run_root}")
    files = sorted(path for path in run_root.iterdir() if path.is_file())
    if not files:
        raise LedgerError(f"Durable run directory is empty: {run_root}")
    recorded_kinds: set[str] = set()
    derived_kinds: set[str] = set()
    file_entries: list[dict[str, Any]] = []
    result_status: str | None = None
    targeted_pass: bool | None = None
    for path in files:
        value = read_json(path)
        if value.get("pocId") != POC_ID:
            raise LedgerError(f"Wrong PoC id in {path}")
        if value.get("commit") != specification["commit"]:
            raise LedgerError(f"Commit mismatch in {path}")
        if path.name == "benchmark-result.json":
            result_status = value.get("result", {}).get("status")
        if path.name == "targeted-scale-observations.json":
            targeted_pass = value.get("passed")
        environment = environment_from_file(path, value)
        if environment is not None:
            recorded_kind = environment.get("kind")
            if isinstance(recorded_kind, str):
                recorded_kinds.add(recorded_kind)
            derived_kinds.add(classify_android_runtime(environment))
        relative = path.relative_to(repo_root).as_posix()
        file_entries.append(
            {
                "bytes": path.stat().st_size,
                "locator": relative,
                "sha256": sha256_file(path),
            }
        )
    expected_status = specification.get("expectedResultStatus")
    if expected_status is not None and result_status != expected_status:
        raise LedgerError(f"Result status mismatch for Actions run {run_id}")
    expected_targeted = specification.get("expectedTargetedPass")
    if expected_targeted is not None and targeted_pass is not expected_targeted:
        raise LedgerError(f"Targeted outcome mismatch for Actions run {run_id}")
    record = {
        key: value
        for key, value in specification.items()
        if key not in {"expectedResultStatus", "expectedTargetedPass"}
    }
    record.update(
        {
            "actionsRunUrl": f"https://github.com/Monumentogram/DORA/actions/runs/{run_id}",
            "files": file_entries,
            "retention": "repository-history",
        }
    )
    if result_status is not None:
        record["resultStatus"] = result_status
    if targeted_pass is not None:
        record["targetedPassed"] = targeted_pass
    if recorded_kinds or derived_kinds:
        record["environmentAudit"] = {
            "derivedKinds": sorted(derived_kinds),
            "recordedKinds": sorted(recorded_kinds),
            "correctionRequired": recorded_kinds != derived_kinds,
            "correction": (
                "The immutable raw files recorded physical, but sdk_gphone/emu64xa, the Google SDK "
                "fingerprint, and Android virtual processor identify an emulator. No metric changed."
                if recorded_kinds != derived_kinds
                else None
            ),
        }
    return record


def build_ledger(repo_root: Path) -> dict[str, Any]:
    records = [build_record(repo_root, specification) for specification in RECORDS]
    classifications = {record["classification"] for record in records}
    if "VALID_FAIL" not in classifications or "TARGETED_PASS" not in classifications:
        raise LedgerError("The durable ledger must retain both valid FAIL and targeted evidence")
    return {
        "schemaVersion": 1,
        "pocId": POC_ID,
        "generator": "tools/poc_search_evidence_ledger.py",
        "records": records,
        "policy": {
            "completedResultsAreImmutable": True,
            "ephemeralActionsArtifactsAreNotTheDurableRecord": True,
            "supersededEvidenceRemainsRetained": True,
        },
    }


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("docs/evidence/poc-search-001/evidence-ledger.json"),
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = args.root.resolve()
    output_path = args.output if args.output.is_absolute() else repo_root / args.output
    try:
        rendered = canonical_json(build_ledger(repo_root))
    except (LedgerError, OSError, KeyError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    if args.check:
        if not output_path.is_file() or output_path.read_text(encoding="utf-8") != rendered:
            print("ERROR: durable evidence ledger is missing or stale", file=sys.stderr)
            return 1
        print(f"Validated {output_path.relative_to(repo_root)}")
        return 0
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(rendered, encoding="utf-8", newline="\n")
    print(f"Wrote {output_path.relative_to(repo_root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
