#!/usr/bin/env python3
"""Validate the isolated POC-CAPTURE-001 harness without a physical microphone."""

from __future__ import annotations

import hashlib
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def fail(message: str) -> None:
    raise ValueError(message)


def read_text(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.is_file():
        fail(f"Missing required file: {relative_path}")
    return path.read_text(encoding="utf-8")


def read_json(relative_path: str) -> dict[str, Any]:
    value = json.loads(read_text(relative_path))
    if not isinstance(value, dict):
        fail(f"Expected a JSON object: {relative_path}")
    return value


def validate_module_and_manifests() -> None:
    settings = read_text("android/settings.gradle.kts")
    build = read_text("android/poc/capture/build.gradle.kts")
    main_manifest_text = read_text("android/app/src/main/AndroidManifest.xml")
    poc_manifest_path = ROOT / "android/poc/capture/src/main/AndroidManifest.xml"
    poc_manifest_text = read_text("android/poc/capture/src/main/AndroidManifest.xml")
    poc_manifest = ET.parse(poc_manifest_path).getroot()

    if 'include(":poc:capture")' not in settings:
        fail(":poc:capture is not included in Android settings")
    expected_id = 'applicationId = "com.monumentogram.dora.poc.capture"'
    if expected_id not in build:
        fail("PoC application ID drifted")
    if "android.permission.RECORD_AUDIO" in main_manifest_text:
        fail("The main Dora bootstrap must remain microphone-free")

    permissions = {
        node.attrib[ANDROID_NS + "name"] for node in poc_manifest.findall("uses-permission")
    }
    expected_permissions = {
        "android.permission.RECORD_AUDIO",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MICROPHONE",
        "android.permission.POST_NOTIFICATIONS",
    }
    if permissions != expected_permissions:
        fail(f"Unexpected PoC permissions: {sorted(permissions)}")
    if "android.permission.INTERNET" in poc_manifest_text:
        fail("PoC must not request INTERNET")
    if poc_manifest.find("application/receiver") is not None:
        fail("PoC must not register a boot or other receiver")

    services = poc_manifest.findall("application/service")
    if len(services) != 1:
        fail("PoC must declare exactly one isolated capture service")
    service = services[0]
    if service.attrib.get(ANDROID_NS + "foregroundServiceType") != "microphone":
        fail("Capture service must use foregroundServiceType=microphone")
    if service.attrib.get(ANDROID_NS + "exported") != "false":
        fail("Capture service must not be exported")


def validate_runtime_policy() -> None:
    source_root = ROOT / "android/poc/capture/src/main"
    sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted(source_root.rglob("*"))
        if path.is_file() and path.suffix in {".kt", ".xml"}
    )
    service = read_text(
        "android/poc/capture/src/main/kotlin/com/monumentogram/dora/poc/capture/"
        "service/CaptureService.kt"
    )
    activity = read_text(
        "android/poc/capture/src/main/kotlin/com/monumentogram/dora/poc/capture/"
        "MainActivity.kt"
    )
    controller = read_text(
        "android/poc/capture/src/main/kotlin/com/monumentogram/dora/poc/capture/"
        "runtime/CaptureController.kt"
    )
    ui = read_text(
        "android/poc/capture/src/main/kotlin/com/monumentogram/dora/poc/capture/ui/"
        "CaptureApp.kt"
    )
    inspector = read_text(
        "android/poc/capture/src/main/kotlin/com/monumentogram/dora/poc/capture/device/"
        "DeviceInspector.kt"
    )
    start_failure = read_text(
        "android/poc/capture/src/main/kotlin/com/monumentogram/dora/poc/capture/runtime/"
        "CaptureStartFailure.kt"
    )
    export = read_text(
        "android/poc/capture/src/main/kotlin/com/monumentogram/dora/poc/capture/"
        "report/SafeExportArchive.kt"
    )

    forbidden_apis = (
        "MediaProjection",
        "AudioPlaybackCaptureConfiguration",
        "AccessibilityService",
        "BOOT_COMPLETED",
        "READ_CONTACTS",
        "READ_CALL_LOG",
        "READ_SMS",
        "MANAGE_EXTERNAL_STORAGE",
        "ACCESS_FINE_LOCATION",
    )
    present = [token for token in forbidden_apis if token in sources]
    if present:
        fail(f"Forbidden APIs or permissions present: {present}")
    required_service_tokens = (
        "START_NOT_STICKY",
        "FOREGROUND_SERVICE_TYPE_MICROPHONE",
        "ACTION_STOP",
        "setOngoing(true)",
        "PendingIntent.getService",
    )
    missing = [token for token in required_service_tokens if token not in service]
    if missing:
        fail(f"Foreground service policy is incomplete: {missing}")
    if "RequestMultiplePermissions" not in activity or "requestStart()" not in activity:
        fail("Runtime permissions must follow the explicit Start action")
    if "startForegroundService" not in controller:
        fail("Capture service must be started from the explicit controller path")
    if "Технический тест. Не является готовой Dora." not in ui:
        fail("PoC disclaimer is missing")
    if (
        "Я понимаю, что это технический тест, и рядом нет людей, которых записывают без предупреждения."
        not in ui
    ):
        fail("Required preflight reminder is missing")
    if "contentColor = MaterialTheme.colorScheme.onBackground" not in ui:
        fail("Root surface must propagate semantic dark-theme content color")
    if "optionalMetric" not in inspector:
        fail("Unsupported optional device telemetry must not abort capture")
    if "CAPTURE_START_SYSTEM_SNAPSHOT" not in start_failure:
        fail("Capture-start failures must expose a stable sanitized stage code")
    required_export_entries = {
        "device-profile.json",
        "run-result.json",
        "deletion-receipt.json",
        "fixture-manifest.json",
        "sanitized-event-log.json",
        "README.txt",
    }
    missing_entries = [entry for entry in required_export_entries if entry not in export]
    if missing_entries:
        fail(f"Safe export allowlist is incomplete: {missing_entries}")
    if "receipt.deletionSucceeded && receipt.absenceVerified" not in export:
        fail("ZIP export is not gated on verified raw-audio deletion")


def validate_tests_present() -> None:
    required = (
        "android/poc/capture/src/test/kotlin/com/monumentogram/dora/poc/capture/"
        "state/CaptureStateMachineTest.kt",
        "android/poc/capture/src/test/kotlin/com/monumentogram/dora/poc/capture/"
        "audio/WavIOTest.kt",
        "android/poc/capture/src/test/kotlin/com/monumentogram/dora/poc/capture/"
        "audio/SyntheticFixtureTest.kt",
        "android/poc/capture/src/test/kotlin/com/monumentogram/dora/poc/capture/"
        "report/SafeExportPolicyTest.kt",
        "android/poc/capture/src/test/kotlin/com/monumentogram/dora/poc/capture/"
        "device/OptionalMetricTest.kt",
        "android/poc/capture/src/test/kotlin/com/monumentogram/dora/poc/capture/"
        "runtime/CaptureStartFailureTest.kt",
        "android/poc/capture/src/test/kotlin/com/monumentogram/dora/poc/capture/"
        "ui/CaptureThemeContrastTest.kt",
        "android/poc/capture/src/androidTest/kotlin/com/monumentogram/dora/poc/capture/"
        "CaptureFlowTest.kt",
    )
    for relative_path in required:
        read_text(relative_path)


def validate_fixture_digest() -> None:
    sample_rate = 16_000
    samples = [0] * (sample_rate * 12)
    amplitude = 8_000.0
    fade_samples = int(sample_rate * 0.01)

    def envelope(index: int, count: int) -> float:
        if index < fade_samples:
            return index / fade_samples
        if index >= count - fade_samples:
            return max(count - index - 1, 0) / fade_samples
        return 1.0

    def tone(start_seconds: float, duration: float, frequency: float) -> None:
        start = int(start_seconds * sample_rate)
        count = int(duration * sample_rate)
        for index in range(count):
            value = math.sin(2.0 * math.pi * frequency * index / sample_rate)
            samples[start + index] = int(value * envelope(index, count) * amplitude)

    tone(1.0, 1.0, 440.0)
    start = 3 * sample_rate
    count = 2 * sample_rate
    slope = (2_400.0 - 300.0) / 2.0
    for index in range(count):
        time = index / sample_rate
        phase = 2.0 * math.pi * (300.0 * time + 0.5 * slope * time * time)
        samples[start + index] = int(
            math.sin(phase) * envelope(index, count) * amplitude
        )
    base = 6 * sample_rate
    marker_length = sample_rate // 100
    spacing = sample_rate // 5
    for marker in range(4):
        for index in range(marker_length):
            phase = 2.0 * math.pi * 1_600.0 * index / sample_rate
            samples[base + marker * spacing + index] = int(
                math.sin(phase) * amplitude * envelope(index, marker_length)
            )
    tone(8.0, 1.0, 880.0)

    pcm = bytearray()
    for sample in samples:
        pcm.extend((sample & 0xFF, (sample >> 8) & 0xFF))
    digest = hashlib.sha256(pcm).hexdigest()
    expected = "f77826d52cd0fde219c4e4f98f9db11a9cd66708b36614debabf5720315f2013"
    if digest != expected:
        fail(f"Synthetic fixture digest drifted: {digest}")
    fixture_test = read_text(
        "android/poc/capture/src/test/kotlin/com/monumentogram/dora/poc/capture/"
        "audio/SyntheticFixtureTest.kt"
    )
    if expected not in fixture_test:
        fail("Fixture unit test does not pin the independently reproduced digest")


class SchemaValidator:
    def __init__(self, root_schema: dict[str, Any]) -> None:
        self.root = root_schema

    def validate(self, instance: Any, schema: dict[str, Any], path: str = "$") -> None:
        if "$ref" in schema:
            self.validate(instance, self.resolve(schema["$ref"]), path)
        if "allOf" in schema:
            for child in schema["allOf"]:
                self.validate(instance, child, path)
        if "oneOf" in schema:
            matches = sum(self.is_valid(instance, child) for child in schema["oneOf"])
            if matches != 1:
                fail(f"{path}: expected exactly one oneOf match, found {matches}")
        if "if" in schema and self.is_valid(instance, schema["if"]):
            self.validate(instance, schema.get("then", {}), path)
        if "const" in schema and instance != schema["const"]:
            fail(f"{path}: expected const {schema['const']!r}, found {instance!r}")
        if "enum" in schema and instance not in schema["enum"]:
            fail(f"{path}: {instance!r} is not in enum")
        if "type" in schema and not self.matches_type(instance, schema["type"]):
            fail(f"{path}: wrong JSON type")

        if isinstance(instance, dict):
            required = schema.get("required", [])
            missing = [key for key in required if key not in instance]
            if missing:
                fail(f"{path}: missing required properties {missing}")
            properties = schema.get("properties", {})
            if schema.get("additionalProperties") is False:
                extra = sorted(set(instance) - set(properties))
                if extra:
                    fail(f"{path}: additional properties are forbidden: {extra}")
            for key, child in properties.items():
                if key in instance:
                    self.validate(instance[key], child, f"{path}.{key}")

        if isinstance(instance, list):
            if len(instance) < schema.get("minItems", 0):
                fail(f"{path}: too few array items")
            if schema.get("uniqueItems"):
                normalized = [json.dumps(item, sort_keys=True) for item in instance]
                if len(normalized) != len(set(normalized)):
                    fail(f"{path}: array items are not unique")
            if "items" in schema:
                for index, item in enumerate(instance):
                    self.validate(item, schema["items"], f"{path}[{index}]")

        if isinstance(instance, str):
            if len(instance) < schema.get("minLength", 0):
                fail(f"{path}: string is too short")
            if len(instance) > schema.get("maxLength", sys.maxsize):
                fail(f"{path}: string is too long")
            if "pattern" in schema and re.search(schema["pattern"], instance) is None:
                fail(f"{path}: string does not match {schema['pattern']}")
            if schema.get("format") == "date-time":
                try:
                    datetime.fromisoformat(instance.replace("Z", "+00:00"))
                except ValueError as error:
                    fail(f"{path}: invalid date-time: {error}")

        if self.is_number(instance):
            if instance < schema.get("minimum", -math.inf):
                fail(f"{path}: number is below minimum")
            if instance > schema.get("maximum", math.inf):
                fail(f"{path}: number is above maximum")

    def is_valid(self, instance: Any, schema: dict[str, Any]) -> bool:
        try:
            self.validate(instance, schema)
            return True
        except ValueError:
            return False

    def resolve(self, reference: str) -> dict[str, Any]:
        if not reference.startswith("#/"):
            fail(f"Unsupported external schema reference: {reference}")
        value: Any = self.root
        for component in reference[2:].split("/"):
            value = value[component.replace("~1", "/").replace("~0", "~")]
        return value

    @staticmethod
    def is_number(value: Any) -> bool:
        return isinstance(value, (int, float)) and not isinstance(value, bool)

    @classmethod
    def matches_type(cls, value: Any, expected: Any) -> bool:
        types = expected if isinstance(expected, list) else [expected]
        for type_name in types:
            if type_name == "null" and value is None:
                return True
            if type_name == "object" and isinstance(value, dict):
                return True
            if type_name == "array" and isinstance(value, list):
                return True
            if type_name == "string" and isinstance(value, str):
                return True
            if type_name == "boolean" and isinstance(value, bool):
                return True
            if type_name == "integer" and isinstance(value, int) and not isinstance(value, bool):
                return True
            if type_name == "number" and cls.is_number(value):
                return True
        return False


def validate_benchmark_schema() -> None:
    schema = json.loads(read_text("docs/stage0/benchmark-result.schema.json"))
    fixture = json.loads(
        read_text("android/poc/capture/src/test/resources/valid-run-result.json")
    )
    SchemaValidator(schema).validate(fixture, schema)


def validate_public_capture_evidence() -> None:
    evidence_root = ROOT / "docs/evidence/poc-capture-001"
    required_files = {
        "README.md",
        "device-profile.json",
        "run-a-result.json",
        "deletion-summary.json",
    }
    if not evidence_root.is_dir():
        fail("Missing public POC-CAPTURE-001 evidence directory")
    present_files = {
        path.relative_to(evidence_root).as_posix()
        for path in evidence_root.rglob("*")
        if path.is_file()
    }
    missing = sorted(required_files - present_files)
    if missing:
        fail(f"Missing public capture evidence files: {missing}")

    forbidden_suffixes = {
        ".zip",
        ".wav",
        ".pcm",
        ".m4a",
        ".aac",
        ".mp3",
        ".flac",
        ".ogg",
        ".raw",
        ".trace",
    }
    forbidden_keys = {
        "serialnumber",
        "androidid",
        "imei",
        "macaddress",
        "ipaddress",
        "accountname",
        "phonenumber",
        "localpath",
        "usbidentifier",
        "advertisingid",
        "transcript",
        "waveformbytes",
        "rawaudiobytes",
    }
    forbidden_text_patterns = (
        re.compile(r"[A-Za-z]:\\"),
        re.compile(r"/(?:data|storage|sdcard|home)/", re.IGNORECASE),
        re.compile(r"-----BEGIN [A-Z ]+PRIVATE KEY-----"),
        re.compile(r"(?:ghp_|github_pat_|AIza)[A-Za-z0-9_-]+"),
    )

    def inspect_json_keys(value: Any, path: str = "$") -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                if key.lower() in forbidden_keys:
                    fail(f"{path}: forbidden public evidence field {key}")
                inspect_json_keys(child, f"{path}.{key}")
        elif isinstance(value, list):
            for index, child in enumerate(value):
                inspect_json_keys(child, f"{path}[{index}]")

    for relative_path in sorted(present_files):
        path = evidence_root / relative_path
        if path.suffix.lower() in forbidden_suffixes:
            fail(f"Forbidden binary/raw evidence file: {relative_path}")
        if path.stat().st_size > 512 * 1024:
            fail(f"Public evidence file is unexpectedly large: {relative_path}")
        text = path.read_text(encoding="utf-8")
        for pattern in forbidden_text_patterns:
            if pattern.search(text):
                fail(f"Forbidden public evidence text pattern: {relative_path}")
        if path.suffix.lower() == ".json":
            inspect_json_keys(json.loads(text))

    schema = read_json("docs/stage0/benchmark-result.schema.json")
    run = read_json("docs/evidence/poc-capture-001/run-a-result.json")
    profile = read_json("docs/evidence/poc-capture-001/device-profile.json")
    deletion = read_json("docs/evidence/poc-capture-001/deletion-summary.json")
    SchemaValidator(schema).validate(run, schema)

    expected_commit = "5d9a8aceebaa7175a7a5cbaa139e8295df87d632"
    expected_run_id = "run-a-20260805T133208Z-349c5e0c"
    if run["pocId"] != "POC-CAPTURE-001" or run["commit"] != expected_commit:
        fail("Run A evidence identity or commit drifted")
    if run["result"]["status"] != "INCONCLUSIVE":
        fail("One-phone Run A evidence must remain INCONCLUSIVE")
    if run["inputData"]["containsRealMeetingData"]:
        fail("Run A public evidence must not contain real meeting data")
    if run["device"]["uniqueHardwareIdentifierRecorded"]:
        fail("Run A evidence records a unique hardware identifier")
    if profile["commit"] != expected_commit:
        fail("Run A device profile commit drifted")
    if profile["device"]["uniqueHardwareIdentifierRecorded"]:
        fail("Public device profile records a unique hardware identifier")

    metrics = {metric["name"]: metric["value"] for metric in run["metrics"]}
    required_metrics = {
        "capture.actual_samples",
        "capture.expected_samples",
        "capture.sample_delta",
        "capture.recorded_bytes",
        "capture.audiorecord_errors",
        "capture.wav_valid",
        "capture.audio_deleted",
        "manual.notification_visible",
    }
    missing_metrics = sorted(required_metrics - metrics.keys())
    if missing_metrics:
        fail(f"Run A evidence is missing required metrics: {missing_metrics}")
    if metrics["capture.actual_samples"] * 2 != metrics["capture.recorded_bytes"]:
        fail("Run A PCM16 sample and byte counts do not reconcile")
    if metrics["capture.audiorecord_errors"] != 0:
        fail("Run A public evidence unexpectedly contains AudioRecord errors")
    if metrics["capture.wav_valid"] is not True:
        fail("Run A WAV validity evidence drifted")
    if metrics["capture.audio_deleted"] is not True:
        fail("Run A deletion metric drifted")
    if any(gate["outcome"] == "triggered" for gate in run["failureGates"]):
        fail("Run A evidence contains a triggered approved failure gate")

    if deletion["runId"] != expected_run_id:
        fail("Deletion summary Run ID drifted")
    if not deletion["deletionSucceeded"] or not deletion["absenceVerified"]:
        fail("Deletion summary does not prove raw-audio absence")
    if deletion["containsAudio"] or deletion["rawAudioRetained"]:
        fail("Public deletion summary claims retained audio")
    if deletion["bytesBeforeDeletion"] != run["fileSizes"][0]["bytes"]:
        fail("Deletion summary byte count does not match Run A result")
    if deletion["reviewDisposition"] != "public_aggregate_only":
        fail("Deletion summary public-review disposition drifted")

    readme = read_text("docs/evidence/poc-capture-001/README.md")
    required_readme_facts = (
        "overall result INCONCLUSIVE",
        "Run B may proceed",
        "notification visibility `unknown`",
        "`0, 16, 180189, 180367, 180258`",
        "source ZIP is not committed",
    )
    missing_facts = [fact for fact in required_readme_facts if fact not in readme]
    if missing_facts:
        fail(f"Run A evidence review is missing required facts: {missing_facts}")


def main() -> int:
    checks = (
        validate_module_and_manifests,
        validate_runtime_policy,
        validate_tests_present,
        validate_fixture_digest,
        validate_benchmark_schema,
        validate_public_capture_evidence,
    )
    for check in checks:
        check()
        print(f"PASS {check.__name__}")
    print("POC-CAPTURE-001 static validation passed")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        sys.exit(1)
