"""Deterministic CycloneDX 1.6 from the actual locked release resolution graph."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
from urllib.parse import quote

from alpha_release_identity import ROOT, identity

APPROVED = ROOT / "docs/contracts/DORA_ALPHA_RELEASE_RUNTIME_GRAPH_V0_1.json"


def canonical_bytes(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode()


def exact_version(value: str) -> bool:
    return isinstance(value, str) and bool(re.fullmatch(r"[0-9][0-9A-Za-z._-]*", value)) and "SNAPSHOT" not in value.upper()


def normalize(graph: dict) -> dict:
    result = json.loads(json.dumps(graph))
    result["components"].sort(key=lambda c: c["id"])
    for component in result["components"]:
        if "artifacts" in component:
            component["artifacts"].sort(key=lambda a: (a["name"], a["sha256"]))
    result["dependencies"].sort(key=lambda d: d["ref"])
    for edge in result["dependencies"]:
        edge["dependsOn"].sort()
    result["requested_versions"] = sorted(set(result["requested_versions"]))
    return result


def check_graph(graph: dict, locked: set[str], approved: dict) -> dict:
    if graph["configuration"] != "releaseRuntimeClasspath" or graph["root"] != "project::app" or graph["unresolved"]:
        raise ValueError("Unresolved or wrong release configuration")
    if any(not exact_version(v) for v in graph["requested_versions"]):
        raise ValueError("Dynamic dependency request")
    ids = [c["id"] for c in graph["components"]]
    if len(ids) != len(set(ids)) or graph["root"] not in ids:
        raise ValueError("Duplicate or missing graph component")
    coordinates = set()
    for component in graph["components"]:
        if component["kind"] == "maven":
            coordinate = ":".join(component[k] for k in ("group", "name", "version"))
            if component["id"] != "maven:" + coordinate or not exact_version(component["version"]):
                raise ValueError("Invalid or dynamic resolved component")
            coordinates.add(coordinate)
            for artifact in component["artifacts"]:
                if not re.fullmatch(r"[0-9a-f]{64}", artifact["sha256"]) or "/" in artifact["name"] or "\\" in artifact["name"]:
                    raise ValueError("Invalid artifact identity")
        elif component["kind"] == "project":
            if component["id"] != "project:" + component["path"] or component["path"] not in {":app", ":core:common", ":core:model"}:
                raise ValueError("Unexpected local release module")
        else:
            raise ValueError("Unknown component kind")
    if not coordinates or coordinates != locked:
        raise ValueError("Release graph differs from dependency locks")
    edges = graph["dependencies"]
    if len(edges) != len(ids) or {e["ref"] for e in edges} != set(ids):
        raise ValueError("Missing or duplicate dependency relationship")
    for edge in edges:
        if len(edge["dependsOn"]) != len(set(edge["dependsOn"])) or not set(edge["dependsOn"]).issubset(ids):
            raise ValueError("Duplicate or dangling dependency")
    reachable, pending = set(), [graph["root"]]
    by_ref = {e["ref"]: e["dependsOn"] for e in edges}
    while pending:
        ref = pending.pop()
        if ref not in reachable:
            reachable.add(ref)
            pending.extend(by_ref[ref])
    if reachable != set(ids):
        raise ValueError("Disconnected release component")
    normalized = normalize(graph)
    if normalized != normalize(approved):
        raise ValueError("Release graph/artifacts differ from approved inventory")
    return normalized


def lock_coordinates(path: Path = ROOT / "android/app/gradle.lockfile") -> set[str]:
    result = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        coordinate, configurations = line.split("=", 1)
        if "releaseRuntimeClasspath" in configurations.split(",") and coordinate != "empty":
            if len(coordinate.split(":")) != 3 or not exact_version(coordinate.rsplit(":", 1)[1]):
                raise ValueError("Invalid release lock coordinate")
            result.add(coordinate)
    if not result:
        raise ValueError("No locked release runtime dependencies")
    return result


def generate(graph: dict, source: str, locked: set[str], approved: dict) -> dict:
    if not re.fullmatch(r"[0-9a-f]{40}", source):
        raise ValueError("Invalid source SHA")
    graph = check_graph(graph, locked, approved)
    _, version = identity()
    components = []
    for node in graph["components"]:
        if node["id"] == graph["root"]:
            continue
        component = {"type": "library", "bom-ref": node["id"]}
        if node["kind"] == "project":
            component.update(name=node["path"], version=version, properties=[{"name": "dora:gradle-project", "value": node["path"]}])
        else:
            component.update(group=node["group"], name=node["name"], version=node["version"], purl=f"pkg:maven/{quote(node['group'], safe='.')}/{quote(node['name'], safe='')}@{quote(node['version'], safe='')}")
            component["properties"] = [{"name": "dora:artifact-sha256:" + a["name"], "value": a["sha256"]} for a in node["artifacts"]]
        components.append(component)
    return {
        "bomFormat": "CycloneDX", "specVersion": "1.6", "version": 1,
        "metadata": {
            "component": {"type": "application", "bom-ref": graph["root"], "name": "DORA Alpha", "version": version},
            "tools": {"components": [{"type": "application", "name": "dora-alpha-release-sbom", "version": "0.1"}]},
            "properties": [
                {"name": "dora:source-sha", "value": source},
                {"name": "dora:configuration", "value": "releaseRuntimeClasspath"},
                {"name": "dora:graph-sha256", "value": hashlib.sha256(canonical_bytes(graph)).hexdigest()},
            ],
        },
        "components": components,
        "dependencies": graph["dependencies"],
    }


def validate(bom: dict, graph: dict, source: str, locked: set[str], approved: dict) -> None:
    if bom != generate(graph, source, locked, approved):
        raise ValueError("SBOM source/version/components/relationships mismatch")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--graph", type=Path, required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--check", type=Path)
    args = parser.parse_args()
    graph = json.loads(args.graph.read_text())
    approved = json.loads(APPROVED.read_text())
    locked = lock_coordinates()
    bom = generate(graph, args.source, locked, approved)
    if args.check:
        validate(json.loads(args.check.read_text()), graph, args.source, locked, approved)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(canonical_bytes(bom))
    print(f"PASS release SBOM: {len(bom['components'])} components plus application root")


if __name__ == "__main__":
    main()
