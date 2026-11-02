"""Prepare exact public-synthetic context controls; never runs inference or edits a device.

Only the two retained v9 PCM digests are admitted. Reports contain scalar data,
not transcript/audio/marker text. Generated PCM and reports stay outside Git.
Float hashes are host-derived expectations, NOT observations at the JNI boundary.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct


PUBLIC_SHA = {
    "en": "cddbfd9a2d1bca6ef6453a1efd270349ab00a878930c8e0ac59b698462ccaba0",
    "ru": "20141a2ca7716f5110d784ac911d52754a467f5c8059f725f691465910d45552",
}
OMITTED = {"en": (8,), "ru": (1, 7, 13, 16)}
WINDOW = 4_800_000
RATE = 16_000


def verify_source(source, language):
    if language not in PUBLIC_SHA or source.stat().st_size != 57_600_000:
        raise ValueError("KNOWN_PUBLIC_SOURCE_REQUIRED")
    with source.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if digest != PUBLIC_SHA[language]:
        raise ValueError("KNOWN_PUBLIC_SOURCE_REQUIRED")


def ranges_for(window, first, count):
    start, end = window * WINDOW, (window + 1) * WINDOW
    if not (0 <= window < 6 and count > 0 and start <= first < first + count <= end):
        raise ValueError("MARKER_RANGE")
    context = max(start, min(first - 10 * RATE, end - 30 * RATE))
    if first + count > context + 30 * RATE:
        raise ValueError("MARKER_RANGE")
    return {"window300": (start, WINDOW), "context30": (context, 30 * RATE),
            "phrase": (first, count)}


def inspect_range(source, first, count):
    if first < 0 or not 0 < count <= WINDOW:
        raise ValueError("PCM_RANGE_INVALID")
    with source.open("rb") as stream:
        stream.seek(first * 2)
        data = stream.read(count * 2)
    if len(data) != count * 2:
        raise ValueError("PCM_RANGE_TRUNCATED")
    floating = hashlib.sha256()
    squared = nonzero = peak = 0
    for (sample,) in struct.iter_unpack("<h", data):
        floating.update(struct.pack("<f", sample / 32768))
        squared += sample * sample
        nonzero += sample != 0
        peak = max(peak, abs(sample))
    return data, {
        "firstFrame": first, "frameCount": count,
        "pcmSha256": hashlib.sha256(data).hexdigest(),
        "float32LeSha256": floating.hexdigest(),
        "nonzeroFrames": nonzero, "peakAbsolutePcm16": peak,
        "rmsPcm16": math.sqrt(squared / count),
    }


def prepare(fixture, language, output, write_pcm=False):
    repository = Path(__file__).resolve().parents[1]
    output = output.resolve()
    if output == repository or repository in output.parents:
        raise ValueError("OUTPUT_MUST_STAY_OUTSIDE_REPOSITORY")
    if output.exists():
        raise ValueError("NEW_OUTPUT_DIRECTORY_REQUIRED")
    source = fixture / "public-speech-1800.pcm"
    verify_source(source, language)
    manifest = json.loads((fixture / "manifest.json").read_text(encoding="utf-8-sig"))
    required = {"version": 2, "language": language, "windowPolicy": "local-fixed-300-v1",
                "pcmSha256": PUBLIC_SHA[language], "frames": 28_800_000,
                "sampleRateHz": RATE, "encoding": "PCM_S16LE", "channels": 1}
    if any(manifest.get(key) != value for key, value in required.items()):
        raise ValueError("MANIFEST_IDENTITY")
    markers = manifest["markers"]
    if len(markers) != 18:
        raise ValueError("MARKER_COUNT")
    planned = []
    for index, marker in enumerate(markers):
        window, role = divmod(index, 3)
        first, count = marker["firstFrame"], marker["frameCount"]
        if (marker["windowIndex"] != window or
                marker["role"] != ("WINDOW_START", "MIDPOINT", "WINDOW_END")[role] or
                (role == 0 and first != window * WINDOW) or
                (role == 1 and first != window * WINDOW + 145 * RATE) or
                (role == 2 and first + count != (window + 1) * WINDOW)):
            raise ValueError("MARKER_LAYOUT")
        ranges = ranges_for(window, first, count)
        if index in OMITTED[language]:
            planned.append((index, ranges))
    output.mkdir(parents=True)
    controls = []
    for index, ranges in planned:
        for kind, (first, count) in ranges.items():
            data, metrics = inspect_range(source, first, count)
            name = f"{language}-marker{index}-{kind}.pcm"
            if write_pcm:
                (output / name).write_bytes(data)
            controls.append({"markerIndex": index, "kind": kind, "pcmWritten": write_pcm,
                             "file": name, **metrics})
    report = {"schema": 1, "language": language, "sourcePcmSha256": PUBLIC_SHA[language],
              "inferenceCalls": 0, "jniInputHashes": "NOT_CAPTURED",
              "floatHashMeaning": "HOST_EXPECTED_PCM16_DIVIDED_BY_32768_FLOAT32_LE",
              "nativeSegments": "NOT_CAPTURED", "quality": "PARTIAL", "controls": controls}
    (output / "context-controls.json").write_text(json.dumps(report, indent=2) + "\n",
                                                encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture-directory", required=True, type=Path)
    parser.add_argument("--language", required=True, choices=tuple(PUBLIC_SHA))
    parser.add_argument("--output-directory", required=True, type=Path)
    parser.add_argument("--write-pcm", action="store_true")
    args = parser.parse_args()
    report = prepare(args.fixture_directory, args.language, args.output_directory, args.write_pcm)
    print(json.dumps({"result": "EXACT_PUBLIC_CONTROLS_PREPARED", "language": args.language,
                      "controls": len(report["controls"]), "inferenceCalls": 0}))


if __name__ == "__main__":
    main()
