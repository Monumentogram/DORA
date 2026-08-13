#!/usr/bin/env python3
"""Validate the immutable handoff inputs and Stage 00 governance artifacts."""

from __future__ import annotations

import csv
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise ValueError(message)


def read_text(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.is_file():
        fail(f"Missing required file: {relative_path}")
    return path.read_text(encoding="utf-8")


def validate_tokens() -> None:
    relative_path = "docs/design/DORA_MVP1_DESIGN_TOKENS.json"
    tokens = json.loads(read_text(relative_path))
    expected_sections = {
        "meta",
        "color",
        "typography",
        "spacing",
        "radius",
        "size",
        "motion",
        "waveform",
        "layout",
    }
    missing = expected_sections.difference(tokens)
    if missing:
        fail(f"Design tokens are missing sections: {sorted(missing)}")


def validate_screen_inventory() -> None:
    relative_path = "docs/design/DORA_MVP1_SCREEN_INVENTORY.csv"
    path = ROOT / relative_path
    if not path.is_file():
        fail(f"Missing required file: {relative_path}")

    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    required_columns = {
        "screen_id",
        "area",
        "name_ru",
        "mvp_priority",
        "compact_pattern",
        "expanded_pattern",
        "primary_action",
        "key_states",
    }
    columns = set(rows[0].keys()) if rows else set()
    if columns != required_columns:
        fail(f"Unexpected screen inventory columns: {sorted(columns)}")

    screen_ids = [row["screen_id"] for row in rows]
    if len(screen_ids) != 42:
        fail(f"Expected 42 screen inventory rows, found {len(screen_ids)}")
    if len(screen_ids) != len(set(screen_ids)):
        fail("Screen inventory contains duplicate screen_id values")
    if any(not value.strip() for row in rows for value in row.values()):
        fail("Screen inventory contains empty required values")


def validate_decisions() -> None:
    text = read_text("docs/DORA_MVP1_PRODUCT_DECISIONS.md")
    ids = re.findall(r"^## (DEC-\d{3})\.", text, flags=re.MULTILINE)
    expected_ids = [f"DEC-{index:03d}" for index in range(1, 45)]
    if ids != expected_ids:
        fail(f"Expected ordered product decisions DEC-001 through DEC-044, found {ids}")

    required_labels = (
        "Статус:",
        "Приоритет:",
        "Источник:",
        "Срок принятия:",
        "Варианты:",
        "Рекомендуемый вариант:",
        "Обоснование:",
        "Влияние на архитектуру:",
        "Влияние на UX:",
        "Обратимость:",
        "Связанные задачи:",
    )
    sections = re.split(r"(?=^## DEC-\d{3}\.)", text, flags=re.MULTILINE)[1:]
    for section, decision_id in zip(sections, ids, strict=True):
        missing = [label for label in required_labels if label not in section]
        if missing:
            fail(f"{decision_id} is missing fields: {missing}")


def validate_readiness() -> None:
    text = read_text("docs/DORA_MVP1_IMPLEMENTATION_READINESS.md")
    ids = re.findall(r"^\| (RDY-\d{3}) \|", text, flags=re.MULTILINE)
    if len(ids) != 30 or len(ids) != len(set(ids)):
        fail(f"Expected 30 unique readiness findings, found {len(ids)}")
    if "READY WITH CONDITIONS" not in text:
        fail("Implementation readiness status is missing")
    traceability_header = (
        "| Требование | Модуль/порт | Экран | Данные | Ключевой тест | Этап |"
    )
    if traceability_header not in text:
        fail("Traceability matrix heading is missing")


def validate_test_strategy() -> None:
    text = read_text("docs/DORA_MVP1_TEST_STRATEGY.md")
    required_headings = (
        "## 1. Назначение и границы",
        "## 2. Уровни тестирования",
        "## 3. CI и execution tiers",
        "## 4. Fixtures, данные и воспроизводимость",
        "## 5. Physical device matrix",
        "## 6. Stage 00 commands",
        "## 7. Release gate ownership",
    )
    missing_headings = [heading for heading in required_headings if heading not in text]
    if missing_headings:
        fail(f"Test Strategy is missing headings: {missing_headings}")

    expected_levels = {
        "TS-UNIT": "Unit",
        "TS-INTEGRATION": "Integration",
        "TS-INSTRUMENTATION": "Android instrumentation",
        "TS-COMPOSE": "Compose UI",
        "TS-DB-MIGRATION": "Database migration",
        "TS-LIFECYCLE": "Lifecycle и process death",
        "TS-FGS": "Foreground service",
        "TS-OFFLINE": "Offline",
        "TS-STORAGE": "Storage",
        "TS-BATTERY": "Battery/thermal",
        "TS-ASR": "ASR",
        "TS-DIARIZATION": "Diarization",
        "TS-PRIVACY": "Privacy",
        "TS-ACCESSIBILITY": "Accessibility",
        "TS-CI": "CI",
        "TS-DEVICE": "Physical device matrix",
        "TS-RELEASE": "Release gates",
    }
    strategy_rows = re.findall(
        r"^\| (TS-[A-Z-]+) \| ([^|]+) \|", text, flags=re.MULTILINE
    )
    actual_levels = {level_id: name.strip() for level_id, name in strategy_rows}
    if actual_levels != expected_levels:
        fail(
            "Unexpected Test Strategy levels: "
            f"expected {expected_levels}, found {actual_levels}"
        )

    table_header = (
        "| ID | Уровень | Цель | Этап внедрения | Среда запуска | "
        "Критерий прохождения |"
    )
    if table_header not in text:
        fail("Test Strategy level table heading is missing")


def validate_governance() -> None:
    required_files = (
        "AGENTS.md",
        "CONTRIBUTING.md",
        "docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md",
        "docs/DORA_MVP1_STAGE_STATUS.md",
        "docs/adr/ADR-0001-android-bootstrap.md",
        "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_3.md",
        "docs/stage0/poc-recovery-gate-set-stage0-v0.3.json",
        "docs/stage0/poc-recovery-protocol-stage0-v0.3.json",
        "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_4.md",
        "docs/stage0/poc-recovery-gate-set-stage0-v0.4.json",
        "docs/stage0/poc-recovery-protocol-stage0-v0.4.json",
        "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_5.md",
        "docs/stage0/poc-recovery-gate-set-stage0-v0.5.json",
        "docs/stage0/poc-recovery-protocol-stage0-v0.5.json",
        "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md",
        "docs/stage0/poc-recovery-gate-set-stage0-v0.6.json",
        "docs/stage0/poc-recovery-protocol-stage0-v0.6.json",
        "docs/evidence/poc-recovery-001/evidence-index.json",
        "docs/evidence/poc-recovery-001/review-findings-v0.1.json",
        "docs/evidence/poc-recovery-001/review-findings-v0.2.json",
        "docs/evidence/poc-recovery-001/review-findings-v0.3.json",
        "docs/evidence/poc-recovery-001/review-findings-v0.4.json",
        "docs/evidence/poc-recovery-001/review-findings-v0.5.json",
        "docs/evidence/poc-recovery-001/governance-remediation-v0.4.md",
        "docs/evidence/poc-recovery-001/governance-remediation-v0.5.md",
        "docs/evidence/poc-recovery-001/governance-remediation-v0.6.md",
        "docs/evidence/poc-recovery-001/base-lockfile-tooling-inventory-2026-08-12.json",
        "docs/evidence/poc-recovery-001/jetbrains-annotations-license-notice-verification-2026-08-12.md",
        "docs/evidence/poc-recovery-001/dependency-ip-authenticity-v0.3.json",
    )
    for relative_path in required_files:
        read_text(relative_path)


def validate_android_bootstrap() -> None:
    catalog = read_text("android/gradle/libs.versions.toml")
    build_logic = read_text("android/build-logic/build.gradle.kts")
    wrapper = read_text("android/gradle/wrapper/gradle-wrapper.properties")
    app_build = read_text("android/app/build.gradle.kts")
    app_manifest = read_text("android/app/src/main/AndroidManifest.xml")

    catalog_agp = re.search(r'^agp = "([^"]+)"$', catalog, flags=re.MULTILINE)
    build_logic_agp = re.search(
        r'com\.android\.tools\.build:gradle:([^"]+)', build_logic
    )
    if not catalog_agp or not build_logic_agp:
        fail("Unable to locate both Android Gradle plugin pins")
    if catalog_agp.group(1) != build_logic_agp.group(1):
        fail("Android Gradle plugin pins drifted between catalog and build-logic")

    if "gradle-9.5.0-bin.zip" not in wrapper:
        fail("Unexpected Gradle wrapper distribution")
    if not re.search(r"^distributionSha256Sum=[0-9a-f]{64}$", wrapper, re.MULTILINE):
        fail("Gradle wrapper SHA-256 pin is missing")
    if 'applicationId = "com.monumentogram.dora.bootstrap"' not in app_build:
        fail("Stage 00 non-release application ID changed without an ADR update")
    if "android.permission.RECORD_AUDIO" in app_manifest:
        fail("Stage 00 bootstrap must not request microphone permission")
    read_text(
        "android/app/src/androidTest/kotlin/com/monumentogram/dora/bootstrap/"
        "DoraBootstrapAppTest.kt"
    )
    read_text("android/native-libs-allowlist.txt")


def main() -> int:
    checks = (
        validate_tokens,
        validate_screen_inventory,
        validate_decisions,
        validate_readiness,
        validate_test_strategy,
        validate_governance,
        validate_android_bootstrap,
    )
    for check in checks:
        check()
        print(f"PASS {check.__name__}")
    print("Stage 00 artifact validation passed")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        sys.exit(1)
