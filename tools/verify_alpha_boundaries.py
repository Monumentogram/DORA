#!/usr/bin/env python3
"""Fail-closed JVM dependency inspection for the minimal Alpha application contract."""
from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MODEL = "com.monumentogram.dora.model.alpha."
FLOW = "com.monumentogram.dora.flow."
JAVA_TYPES = {
    "java.lang.Object", "java.lang.String", "java.lang.StringBuilder", "java.lang.CharSequence",
    "java.lang.Enum", "java.lang.Throwable", "java.lang.RuntimeException",
    "java.lang.IllegalArgumentException", "java.lang.Number", "java.lang.Integer",
    "java.lang.Long", "java.lang.Boolean", "java.lang.annotation.Annotation",
    # JVM enum class literals and compiler-generated string concatenation/lambda linkage.
    "java.lang.Class", "java.lang.invoke.CallSite", "java.lang.invoke.MethodHandles$Lookup",
    "java.lang.invoke.MethodType", "java.lang.invoke.StringConcatFactory",
    "java.lang.invoke.LambdaMetafactory", "java.lang.invoke.MethodHandle",
    "java.util.Set", "java.util.Collection", "java.util.List", "java.util.Iterator",
}


def inspect_classes(directory: Path, namespace: str) -> int:
    if not directory.is_dir() or not list(directory.rglob("*.class")):
        raise ValueError("Alpha boundary requires compiled classes")
    executable = str(Path(os.environ["JAVA_HOME"]) / "bin" / "jdeps") if os.environ.get("JAVA_HOME") else shutil.which("jdeps")
    result = subprocess.run(
        [executable, "-verbose:class", "-filter:none", str(directory)],
        check=True, capture_output=True, text=True,
    )
    count = 0
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) < 3 or fields[1] != "->" or not fields[0].startswith(namespace):
            continue
        count += 1
        target = fields[2]
        allowed_local = target.startswith(MODEL) or (namespace == FLOW and target.startswith(FLOW))
        allowed_kotlin = target.startswith(("kotlin.jvm.", "kotlin.collections.", "kotlin.text.", "kotlin.enums.", "kotlin.annotation.")) or target in {"kotlin.Metadata", "kotlin.Unit", "kotlin.NoWhenBranchMatchedException"}
        if not (allowed_local or allowed_kotlin or target in JAVA_TYPES or target.startswith("org.jetbrains.annotations.")):
            raise ValueError(f"Alpha forbidden dependency: {fields[0]} -> {target}")
    if count == 0:
        raise ValueError("Alpha compiled package is absent or dependency output was unrecognized")
    return count


def validate_model_lock(text: str) -> None:
    permitted = {"org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains:annotations"}
    runtime_entries = 0
    for line in text.splitlines():
        if line.startswith(("#", "empty=")) or "=" not in line:
            continue
        artifact, configurations = line.split("=", 1)
        if set(configurations.split(",")) & {"debugCompileClasspath", "debugRuntimeClasspath", "releaseCompileClasspath", "releaseRuntimeClasspath"}:
            runtime_entries += 1
            if ":".join(artifact.split(":")[:2]) not in permitted:
                raise ValueError("Alpha model dependency lock contains an unadmitted runtime dependency")
    if runtime_entries == 0:
        raise ValueError("Alpha model runtime dependency lock is absent")


def main() -> None:
    validate_model_lock((ROOT / "android/core/model/gradle.lockfile").read_text(encoding="utf-8"))
    for module, namespace in (("core/model", MODEL), ("app", FLOW)):
        directory = ROOT / "android" / module / "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
        count = inspect_classes(directory, namespace)
        print(f"PASS Alpha compiled boundary {module}: {count} class dependencies")


if __name__ == "__main__":
    main()
