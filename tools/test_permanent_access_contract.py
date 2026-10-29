"""Guard clock-free local eligibility and Keystore identity across APK updates."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "android/app/src/main/kotlin/com/monumentogram/dora/transcript"


class PermanentAccessContractTests(unittest.TestCase):
    def test_local_model_eligibility_has_no_calendar_dependency(self):
        model = (SOURCE / "LocalAsrModel.kt").read_text()
        native = (SOURCE / "WhisperNative.kt").read_text()
        for source in (model, native):
            self.assertIsNone(re.search(r"LocalDate|Instant\.now|Clock|currentTimeMillis|expires|EXPIRY|2026-11", source))
        self.assertIn("MessageDigest.isEqual", model)
        self.assertIn("Files.isSymbolicLink", model)

    def test_existing_android_identity_and_sessions_need_no_reactivation(self):
        identity = (SOURCE / "CloudIdentity.kt").read_text()
        client = (SOURCE / "CloudHttpClient.kt").read_text()
        self.assertIn('"cloud-installation-v1"', identity)
        self.assertIn('"dora-cloud-$installationId"', identity)
        self.assertIn('KeyStore.getInstance("AndroidKeyStore")', identity)
        self.assertNotIn("setKeyValidityEnd", identity)
        self.assertIn('keyStore.containsAlias(alias)', identity)
        self.assertIn('tokenExpiry - 10', client)
        self.assertIn('exchange("POST", "/v1/session", JSONObject(), null, cancelled)', client)
        self.assertIn('session.getLong("expires_at")', client)
        self.assertIn('connection.setRequestProperty("X-Dora-Signature", sign(proof))', client)


if __name__ == "__main__":
    unittest.main()
