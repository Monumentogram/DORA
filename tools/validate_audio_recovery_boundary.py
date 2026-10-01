"""Stage 8.1 admission only; frozen Recovery and 7.4 remain source-specific evidence."""
from pathlib import Path
import argparse
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "f3e58b12d0e9353cdb17e986336f23f48f33511c"
BASE_TREE = "6b2950a3acc247f8d7f9816ff0e416ee713a24e0"
BASE_PARENT = "ee8b3e70217307a3ba110a80b707dcd0a8bc7e3a"
BRANCH = "stage/7-alpha-foundation"
ADR = "docs/adr/ADR-AUDIO-001-product-recovery-boundary.md"
REPORT = "docs/evidence/audio-8.1-local-v0.1.json"
PREFIX = "android/core/audio/"
KOTLIN = PREFIX + "src/main/kotlin/com/monumentogram/dora/audio/"
TEST = PREFIX + "src/test/kotlin/com/monumentogram/dora/audio/"
STATUS = ("docs/DORA_MVP1_STAGE_STATUS.md", "docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md")
PATHS = frozenset({
    "android/settings.gradle.kts", PREFIX + "build.gradle.kts", PREFIX + "gradle.lockfile",
    PREFIX + "src/main/AndroidManifest.xml", KOTLIN + "ProductAudioPort.kt",
    KOTLIN + "RecoveryAudioBridge.kt", TEST + "ProductAudioContractTest.kt",
    TEST + "RecoveryAudioBridgeTest.kt", TEST + "AudioMemoryFixture.kt",
    TEST + "MemoryQuarantineFixture.kt", ADR, REPORT,
    "docs/superpowers/plans/2026-10-01-audio-recovery-boundary.md", *STATUS,
    ".github/workflows/android-ci.yml", "tools/validate_audio_recovery_boundary.py",
    "tools/test_audio_recovery_boundary.py", "tools/validate_alpha_release.py",
    "tools/validate_poc_recovery_governance.py", "tools/validate_security_identity_contract.py",
})
EVIDENCE_PATHS = frozenset({REPORT})
STEP = """      - name: Verify Stage 8.1 audio Recovery boundary
        run: |
          python3 tools/validate_audio_recovery_boundary.py
          python3 -m unittest discover -s tools -p test_audio_recovery_boundary.py
          cd android
          ./gradlew --no-daemon --stacktrace :core:audio:testDebugUnitTest :core:audio:compileDebugAndroidTestKotlin :core:audio:lintDebug :core:audio:assembleDebug :core:audio:assembleRelease
          cd ..
          python3 tools/validate_audio_recovery_boundary.py --compiled

"""


def require(condition, message):
    if not condition:
        raise ValueError(message)


def git(root, *args):
    return subprocess.check_output(["git", "-c", f"safe.directory={root.as_posix()}",
                                   "-C", str(root), *args])


def validate_history(history, transitions, head):
    require(1 <= len(history) <= 2 and len(transitions) == len(history), "8.1 finite successor length")
    parent = BASE
    for index, (line, paths) in enumerate(zip(history, transitions)):
        fields = line.split()
        require(len(fields) == 2 and re.fullmatch(r"[0-9a-f]{40}", fields[0])
                and fields[1] == parent and fields[0] != parent, "8.1 ordered single-parent baseline")
        require(set(paths) == PATHS if index == 0 else bool(paths) and set(paths) <= EVIDENCE_PATHS,
                "8.1 exact transition inventory")
        parent = fields[0]
    require(parent == head, "8.1 HEAD mismatch")


def validate_status_text(text, historical):
    if isinstance(text, bytes):
        text, historical = text.decode(), historical.decode()
    require(text.endswith(historical) and text != historical, "8.1 historical status bytes changed")
    latest = text[:-len(historical)]
    for token in ("8.1 = PENDING_FINAL_PUBLICATION", "Stage 8.2 = NOT_STARTED",
                  "Recovery integration prerequisite = SATISFIED", "7.4 = PASS",
                  "Encrypted product persistence runtime = NOT_ACCEPTED"):
        require(token in latest, "8.1 status missing " + token)


def validate_sources(files):
    build = files[PREFIX + "build.gradle.kts"]
    require(build.count('rootProject.file("poc/recovery/src/main/kotlin")') == 1,
            "Accepted Recovery source-sharing path changed")
    require('implementation("com.google.crypto.tink:tink-android:1.23.0")' in build
            and 'implementation(project(":core:model"))' in build, "Unadmitted audio dependency")
    ports = files[KOTLIN + "ProductAudioPort.kt"]
    require('AudioFormat("PCM_S16LE", 16_000, 1)' in ports, "Frozen PCM format changed")
    require("RequiresEncryptedPersistence" in ports and "interface ProductAudioReaderPort" in ports
            and "interface ProductAudioWriterPort" in ports, "Public runtime/ports changed")
    require("poc.recovery" not in ports and "java.io." not in ports and "android." not in ports,
            "Product API exposes engine/platform/storage details")
    bridge = files[KOTLIN + "RecoveryAudioBridge.kt"]
    for token in ("internal class RecoveryAudioBridge", "bootstrap.bootstrap(", "publisher.publish(",
                  "reader.reconcile(", "capability.authorizes(prefix)", "catalog.reserve(",
                  "AudioIntent.Append", "AudioIntent.Finalize", "requireComplete = true",
                  "quarantine: RecoveryQuarantineController", "prefix.manifestGenerationUsed != 1UL"):
        require(token in bridge, "Required integration gate missing: " + token)
    for forbidden in ("AndroidRecoveryJournalDatabase(", "FileOutputStream", "writeBytes(",
                      "RecoveryStreaming", "MemoryAudioCatalog", "AesGcmJce", "android."):
        require(forbidden not in bridge, "Unadmitted product composition: " + forbidden)
    require(STEP in files[".github/workflows/android-ci.yml"], "Mandatory audio CI gate missing/changed")


def validate_checkout(root=ROOT, *, allow_working=False):
    require(git(root, "rev-parse", f"{BASE}^{{tree}}").decode().strip() == BASE_TREE,
            "8.1 baseline tree unavailable/changed")
    require(git(root, "show", "-s", "--format=%P", BASE).decode().strip() == BASE_PARENT,
            "8.1 baseline parent changed")
    require(git(root, "branch", "--show-current").decode().strip() == BRANCH, "8.1 branch mismatch")
    history = git(root, "rev-list", "--reverse", "--parents", f"{BASE}..HEAD").decode().splitlines()
    transitions = [git(root, "diff", "--name-only", "--no-renames", *line.split()[1:],
                       line.split()[0]).decode().splitlines() for line in history]
    head = git(root, "rev-parse", "HEAD").decode().strip()
    if history:
        validate_history(history, transitions, head)
    else:
        require(allow_working and head == BASE, "8.1 implementation commit missing")
    dirty = git(root, "status", "--porcelain", "--untracked-files=all").decode()
    require(allow_working or not dirty, "8.1 checkout must be clean")
    changed = set(git(root, "diff", "--name-only", BASE).decode().splitlines())
    changed.update(git(root, "ls-files", "--others", "--exclude-standard").decode().splitlines())
    require(changed == PATHS, "8.1 exact changed-file inventory mismatch: " + str(sorted(changed ^ PATHS)))
    for name in STATUS:
        validate_status_text((root / name).read_bytes(), git(root, "show", BASE + ":" + name))
    files = {p: (root / p).read_text(encoding="utf-8") for p in PATHS if not p.endswith(".lockfile")}
    validate_sources(files)
    expected_settings = git(root, "show", BASE + ":android/settings.gradle.kts").decode()
    require(files["android/settings.gradle.kts"].replace('\ninclude(":core:audio")\n', '', 1)
            == expected_settings, "Unbounded module graph change")
    return files


def validate_compiled(root=ROOT):
    import zipfile
    aar = root / PREFIX / "build/outputs/aar/audio-debug.aar"
    require(aar.is_file(), "Audio compiled artifact missing")
    import io
    with zipfile.ZipFile(aar) as outer:
        require(not any(n.endswith(".so") for n in outer.namelist()), "Unadmitted audio native code")
        with zipfile.ZipFile(io.BytesIO(outer.read("classes.jar"))) as classes:
            names = classes.namelist()
            require("com/monumentogram/dora/audio/RecoveryAudioBridge.class" in names, "Bridge not compiled")
            require(not any("Fixture" in n or "MemoryAudioCatalog" in n for n in names), "Test fallback shipped")
            for name in ("ProductAudioWriterPort", "ProductAudioReaderPort", "ProductAudioRuntime"):
                data = classes.read("com/monumentogram/dora/audio/" + name + ".class")
                require(b"poc/recovery" not in data and b"java/io/File" not in data,
                        "Compiled public boundary leaks storage internals")
    # The executable bridge has no platform calls; accepted journal/API33 implementation remains
    # dormant. API28–32 applicability requires the separately admitted encrypted 8.2 journal.
    require('const val MIN = 28' in (root / "android/build-logic/src/main/kotlin/DoraAndroidSdk.kt").read_text()
            and 'minSdk = DoraAndroidSdk.MIN' in (root / "android/build-logic/src/main/kotlin/DoraAndroidLibraryPlugin.kt").read_text(),
            "Audio minimum SDK changed")
    def coordinates(path):
        return {line.split("=", 1)[0] for line in path.read_text().splitlines()
                if line and not line.startswith("#") and not line.startswith("empty=")}
    approved = coordinates(root / "android/poc/recovery/gradle.lockfile") | coordinates(root / "android/core/model/gradle.lockfile")
    require(coordinates(root / PREFIX / "gradle.lockfile") <= approved,
            "Unadmitted audio dependency coordinate/license inventory")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiled", action="store_true")
    parser.add_argument("--working", action="store_true")
    args = parser.parse_args()
    validate_checkout(allow_working=args.working)
    if args.compiled:
        validate_compiled()
    print("PASS Stage 8.1 boundary/source checks; encrypted persistence runtime NOT_ACCEPTED")


if __name__ == "__main__":
    main()
