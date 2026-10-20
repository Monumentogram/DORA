"""Validate the owner-scoped limited profile without rewriting historical acceptance."""
import argparse
import json
from pathlib import Path
import subprocess
import zipfile

BASE = "6f5e87317b163e4567ffa76da3cd3191bcfac826"
ROOT = Path(__file__).resolve().parents[1]
PROFILE = "docs/contracts/stage8-alpha-limited-v1.json"
INVENTORY = "docs/contracts/stage8-alpha-device-tests-v1.json"
DEFERRED_DEVICE = (
    "com.monumentogram.dora.audio.persistence.SegmentationPersistenceTest"
    "#encryptedRotationPreservesCanonicalHashAndExactOverlapAcrossReopen"
)
NEW_DEVICE = {
    "com.monumentogram.dora.audio.persistence.AlphaRecordingRuntimeTest#"
    "pauseReopenAt299SecondsClipsResumeAndPreservesOriginalAcrossNewRecording",
    "com.monumentogram.dora.audio.persistence.AlphaRecordingRuntimeTest#"
    "interruptedAtExactLimitReopensPrefixWithoutResumeOrFalseFinalization",
    "com.monumentogram.dora.audio.persistence.AlphaRecordingProcessDeathTest#"
    "restartPreserves299SecondsAndCannotCapturePast300",
}


def require(value, message):
    if not value:
        raise ValueError(message)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def validate_inventory(current, inherited):
    expected = (set(inherited["tests"]) - {DEFERRED_DEVICE}) | NEW_DEVICE
    require(len(current["tests"]) == len(set(current["tests"])) == 165,
            "Duplicate/missing limited test inventory")
    require(set(current["tests"]) == expected, "Unexpected runtime test omission/addition")
    require(current["no_credential_tests"] == inherited["no_credential_tests"],
            "Credential controls changed")
    require(current["deferred_tests"] == [DEFERRED_DEVICE], "Unexpected long-test deferral")


def validate_profile(profile):
    require(profile["baseline"] == BASE and profile["schema_version"] == 1, "Wrong baseline/version")
    require(profile["max_alpha_frames"] == 4_800_000, "Alpha cap changed")
    require(profile["outstanding_frames"] == 256_000, "Backpressure budget changed")
    require(profile["pcm"] == "PCM_S16LE/16000/mono", "PCM changed")
    require(profile["physical_smoke"] == "NOT_RUN / SAFE_DEVICE_UNAVAILABLE",
            "Source cannot self-certify physical acceptance")
    require(profile["security_release_gate"] == "OPEN", "Security gate cannot be closed here")
    require(profile["signed_alpha_release"] == "BLOCKED", "Signed Alpha is not admitted")
    require(profile["historical_stage_8_6"] == "NOT_READY", "Historical failure relabeled")
    require(profile["long02_physical_protection"] == "NOT_INSTALLED", "Physical custody overclaim")
    require(profile["long02_root_cause"] == "NOT_PROVEN", "Causality overclaim")


def check_apk(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(not any("dora-protected-policy" in name for name in names),
                "Private successor policy/pin must not be packaged")
        require(not any(name.endswith((".jks", ".keystore", ".pem", ".p12")) for name in names),
                "Signing material in APK")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, action="append", default=[])
    args = parser.parse_args()
    profile = json.loads((ROOT / PROFILE).read_text(encoding="utf-8"))
    validate_profile(profile)
    inherited = json.loads(git("show", BASE + ":docs/contracts/persistence-8.6c3-device-tests.json"))
    validate_inventory(json.loads((ROOT / INVENTORY).read_text(encoding="utf-8")), inherited)
    subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], cwd=ROOT, check=True)
    changed = set(git("diff", "--name-only", BASE).decode().splitlines())
    changed |= set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    allowed = set(profile["implementation_paths"])
    require(changed <= allowed, "Out-of-scope files: " + ", ".join(sorted(changed - allowed)))
    require(len(allowed) == len(profile["implementation_paths"]), "Duplicate scope entry")
    for path in profile["append_only_decisions"]:
        old = git("show", BASE + ":" + path).decode().replace("\r\n", "\n")
        require((ROOT / path).read_text(encoding="utf-8").endswith(old), "Historical source was rewritten: " + path)
    deferred = profile["deferred_jvm_tests"]
    init = (ROOT / "tools/stage8_alpha_profile.init.gradle").read_text(encoding="utf-8")
    import re
    require(re.findall(r"excludeTestsMatching '([^']+)'", init) == deferred,
            "Limited JVM exclusions differ from exact contract")
    for test in deferred:
        source = "android/core/audio/src/test/kotlin/" + test.rsplit(".", 1)[0].replace(".", "/") + ".kt"
        require((ROOT / source).read_text(encoding="utf-8") == git("show", BASE + ":" + source).decode().replace("\r\n", "\n"),
                "Deferred historical test changed")
    for path in args.apk:
        check_apk(path)
    print("PASS limited Alpha scope, inherited test inventory, historical preservation and release gates")


if __name__ == "__main__":
    main()
