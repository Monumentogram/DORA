"""Synthetic checks for exact-range, fail-closed Whisper diagnostic preparation."""
import hashlib
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest


class ContextDiagnosticTests(unittest.TestCase):
    def setUp(self):
        path = Path(__file__).with_name("prepare_whisper_context_diagnostic.py")
        self.assertTrue(path.exists(), "Exact-input diagnostic helper is absent")
        spec = importlib.util.spec_from_file_location("context_diagnostic", path)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def test_midpoint_and_terminal_controls_remain_inside_original_window(self):
        self.assertEqual(self.module.ranges_for(2, 11_920_000, 59_200), {
            "window300": (9_600_000, 4_800_000),
            "context30": (11_760_000, 480_000),
            "phrase": (11_920_000, 59_200),
        })
        self.assertEqual(self.module.ranges_for(2, 14_349_680, 50_320)["context30"],
                         (13_920_000, 480_000))
        with self.assertRaisesRegex(ValueError, "MARKER_RANGE"):
            self.module.ranges_for(2, 14_399_999, 2)

    def test_exact_signed_pcm_slice_and_float_fingerprint(self):
        raw = struct.pack("<hhhhhh", 123, -32768, -16384, 0, 32767, 456)
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "input.pcm"
            source.write_bytes(raw)
            data, metrics = self.module.inspect_range(source, 1, 4)
        self.assertEqual(data, bytes.fromhex("008000c00000ff7f"))
        self.assertEqual(metrics["pcmSha256"], hashlib.sha256(data).hexdigest())
        self.assertEqual(metrics["float32LeSha256"], hashlib.sha256(
            struct.pack("<ffff", -1, -0.5, 0, 32767 / 32768)).hexdigest())
        self.assertEqual(metrics["nonzeroFrames"], 3)
        self.assertEqual(metrics["peakAbsolutePcm16"], 32768)

    def test_range_truncation_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "input.pcm"
            source.write_bytes(b"\x00\x00")
            with self.assertRaisesRegex(ValueError, "PCM_RANGE_TRUNCATED"):
                self.module.inspect_range(source, 0, 2)

    def test_changed_source_is_rejected_before_any_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = Path(temporary)
            source = fixture / "public-speech-1800.pcm"
            with source.open("wb") as stream:
                stream.truncate(57_600_000)
            output = fixture / "output"
            with self.assertRaisesRegex(ValueError, "KNOWN_PUBLIC_SOURCE_REQUIRED"):
                self.module.prepare(fixture, "ru", output, write_pcm=True)
            self.assertFalse(output.exists())

    def test_repository_output_is_rejected_without_reading_source(self):
        with self.assertRaisesRegex(ValueError, "OUTPUT_MUST_STAY_OUTSIDE_REPOSITORY"):
            self.module.prepare(Path("absent-input"), "ru", Path(__file__).parent / "controls")


if __name__ == "__main__":
    unittest.main()
