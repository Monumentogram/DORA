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
DEFERRED_DEVICE = {
    "com.monumentogram.dora.audio.persistence.SegmentationPersistenceTest"
    "#encryptedRotationPreservesCanonicalHashAndExactOverlapAcrossReopen",
    "com.monumentogram.dora.audio.persistence.SegmentationProcessDeathTest"
    "#verifySegmentationProcessDeath",
}
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
    expected = (set(inherited["tests"]) - DEFERRED_DEVICE) | NEW_DEVICE
    require(len(current["tests"]) == len(set(current["tests"])) == 164,
            "Duplicate/missing limited test inventory")
    require(set(current["tests"]) == expected, "Unexpected runtime test omission/addition")
    require(current["no_credential_tests"] == inherited["no_credential_tests"],
            "Credential controls changed")
    require(current["deferred_tests"] == sorted(DEFERRED_DEVICE), "Unexpected long-test deferral")


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


def isolated_scope(contract):
    require(contract['baseline'] == '1442f0db8a6936920a692913035d100f68871f81',
            'Unexpected isolated successor baseline')
    require(contract['application_id'] == 'com.monumentogram.dora.alpha.test', 'Isolated runtime ID changed')
    require(contract['physical_operations_authorized'] is False and
            contract['security_restoration'] == 'OPEN' and contract['stage_8_6'] == 'NOT_READY',
            'Isolated software cannot close physical/security gates')
    require(contract['mandatory_additional_jobs'] ==
            ['isolated-alpha-software', 'isolated-alpha-api28', 'isolated-alpha-api36'],
            'Mandatory additional jobs changed')
    paths = contract['implementation_paths']
    require(bool(paths) and len(paths) == len(set(paths)) and all(
        isinstance(path, str) and path and not path.startswith('/') and
        not any(value in path for value in ('..', '*', '?', '\\', ':')) for path in paths),
        'Ambiguous successor scope')
    return set(paths)


def owner_pilot_scope(contract):
    expected = {
        'baseline': '52e4ce8bdf074ec021ae52c3c38ad9d26c24e128',
        'application_id': 'com.monumentogram.dora.alpha.pilot',
        'security_restoration': 'OPEN', 'stage_8_6': 'NOT_READY',
        'long02_root_cause': 'NOT_PROVEN', 'production_upgrade_safety': 'UNPROVEN',
        'distribution': 'BLOCKED',
    }
    require(all(contract.get(key) == value for key, value in expected.items()) and
            contract.get('physical_operations_authorized') is False,
            'Invalid owner pilot software boundary')
    paths = contract['implementation_paths']
    require(bool(paths) and len(paths) == len(set(paths)) and all(
        isinstance(path, str) and path and not path.startswith('/') and
        not any(value in path for value in ('..', '*', '?', '\\', ':')) for path in paths),
        'Ambiguous owner pilot scope')
    return set(paths)


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
    require(len(allowed) == len(profile["implementation_paths"]), "Duplicate scope entry")
    isolated = ROOT / 'docs/contracts/isolated-alpha-v1.json'
    if isolated.exists():
        successor = json.loads(isolated.read_text(encoding='utf-8'))
        allowed |= isolated_scope(successor)
        subprocess.run(['git', 'merge-base', '--is-ancestor', successor['baseline'], 'HEAD'],
                       cwd=ROOT, check=True)
    security = ROOT / 'docs/contracts/alpha-security-preflight-v1.json'
    if security.exists():
        contract = json.loads(security.read_text(encoding='utf-8'))
        require(contract['baseline'] == 'd56a64f079d7bf9acf1d548545dfbb9c38858bde' and
                contract['security_restoration'] == 'OPEN' and
                contract['production_installation'] == 'NOT_RUN' and
                contract['distribution'] == 'BLOCKED', 'Invalid security successor boundary')
        paths = contract['implementation_paths']
        require(len(paths) == len(set(paths)) and all(
            isinstance(path, str) and path and not path.startswith('/') and
            not any(value in path for value in ('..', '*', '?', '\\', ':')) for path in paths),
            'Ambiguous security successor scope')
        subprocess.run(['git', 'merge-base', '--is-ancestor', contract['baseline'], 'HEAD'],
                       cwd=ROOT, check=True)
        allowed |= set(paths)
    pilot = ROOT / 'docs/contracts/owner-alpha-pilot-v1.json'
    if pilot.exists():
        contract = json.loads(pilot.read_text(encoding='utf-8'))
        allowed |= owner_pilot_scope(contract)
        subprocess.run(['git', 'merge-base', '--is-ancestor', contract['baseline'], 'HEAD'],
                       cwd=ROOT, check=True)
    require(changed <= allowed, "Out-of-scope files: " + ", ".join(sorted(changed - allowed)))
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
    for test in DEFERRED_DEVICE:
        source = "android/core/audio/src/androidTest/kotlin/" + test.split("#")[0].replace(".", "/") + ".kt"
        require((ROOT / source).read_text(encoding="utf-8") == git("show", BASE + ":" + source).decode().replace("\r\n", "\n"),
                "Deferred historical instrumentation changed")
    for path in args.apk:
        check_apk(path)
    print("PASS limited Alpha scope, inherited test inventory, historical preservation and release gates")


if __name__ == "__main__":
    main()
