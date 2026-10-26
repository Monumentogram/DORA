"""Synthetic checks for the optional native artifact admission boundary."""
import hashlib
import importlib.util
import pathlib
import tempfile
import unittest


class AdmissionTests(unittest.TestCase):
    def setUp(self):
        path = pathlib.Path(__file__).with_name("build_stage9_whisper_adapter.py")
        self.assertTrue(path.exists(), "The optional native admission helper is absent")
        spec = importlib.util.spec_from_file_location("stage9_build", path)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def test_modified_library_is_rejected_before_build(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "runtime.so"
            path.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "ARTIFACT_IDENTITY"):
                self.module.verify_file(path, 7, hashlib.sha256(b"allowed").hexdigest())

    def test_exact_library_is_admitted(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "runtime.so"
            path.write_bytes(b"allowed")
            self.module.verify_file(path, 7, hashlib.sha256(b"allowed").hexdigest())

    def test_elf_requires_16k_segments(self):
        import struct
        raw = bytearray(120)
        raw[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<H", raw, 18, 183)
        struct.pack_into("<Q", raw, 32, 64)
        struct.pack_into("<HH", raw, 54, 56, 1)
        struct.pack_into("<IIQQQQQQ", raw, 64, 1, 5, 0, 0, 0, 120, 120, 4096)
        with self.assertRaisesRegex(ValueError, "ELF_ALIGNMENT"):
            self.module.verify_elf(bytes(raw))
        struct.pack_into("<Q", raw, 112, 16384)
        self.assertEqual(self.module.verify_elf(bytes(raw)), [16384])


if __name__ == "__main__":
    unittest.main()
