#!/usr/bin/env python3
"""Shared Android runtime environment checks for POC-SEARCH-001 tooling."""

from __future__ import annotations

from typing import Any, Mapping


def _normalized(environment: Mapping[str, Any], key: str) -> str:
    value = environment.get(key, "")
    return value.lower() if isinstance(value, str) else ""


def classify_android_runtime(environment: Mapping[str, Any]) -> str:
    fingerprint = _normalized(environment, "buildFingerprint")
    manufacturer = _normalized(environment, "manufacturer")
    model = _normalized(environment, "model")
    brand = _normalized(environment, "brand")
    device = _normalized(environment, "device")
    product = _normalized(environment, "product")
    hardware = _normalized(environment, "hardware")
    cpu_summary = _normalized(environment, "cpuSummary")
    emulator = (
        fingerprint.startswith("generic/")
        or "/generic" in fingerprint
        or "emulator" in fingerprint
        or fingerprint.startswith("google/sdk_gphone")
        or "genymotion" in manufacturer
        or "google_sdk" in model
        or "android sdk built for" in model
        or "emulator" in model
        or model.startswith("sdk_gphone")
        or device.startswith("emu")
        or product.startswith("sdk_gphone")
        or "emulator" in product
        or "vbox86" in product
        or hardware in {"goldfish", "ranchu", "vbox86"}
        or "virtual processor" in cpu_summary
        or (brand.startswith("generic") and device.startswith("generic"))
    )
    return "emulator" if emulator else "physical"


def require_consistent_android_runtime(environment: Mapping[str, Any]) -> str:
    reported = environment.get("kind")
    if reported not in {"emulator", "physical", "remote_physical"}:
        raise ValueError(f"Unsupported or missing androidEnvironment.kind: {reported!r}")
    derived = classify_android_runtime(environment)
    if reported == "remote_physical":
        if derived == "emulator":
            raise ValueError("remote_physical environment contains emulator indicators")
        return reported
    if reported != derived:
        raise ValueError(
            f"androidEnvironment.kind is {reported!r}, but recorded identifiers classify it as {derived!r}"
        )
    return reported
