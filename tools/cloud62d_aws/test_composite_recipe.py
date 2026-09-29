"""Exact-byte regression for the owned technical composite recipe."""

import hashlib
import unittest

from tools.cloud62d_aws.composite_recipe import (
    canonical_recipe_bytes, recipe_sha256, require_recipe_artifact_bytes,
    require_recipe_sha256,
)
from tools.cloud62d_owned.corpus import encoded


PARTS = [{'case_id': 'ru-read-01', 'frames': 16000,
          'uploaded_wav_sha256': 'a' * 64}]
EXACT = (b'[{' + b'"case_id":"ru-read-01","frames":16000,' +
         b'"uploaded_wav_sha256":"' + b'a' * 64 + b'"}]\n')
EXPECTED_SHA = hashlib.sha256(EXACT).hexdigest()


class CompositeRecipeBytesTests(unittest.TestCase):
    def test_canonical_exact_bytes_pass(self):
        self.assertEqual(canonical_recipe_bytes(PARTS), EXACT)
        self.assertEqual(require_recipe_artifact_bytes(EXACT, EXPECTED_SHA), PARTS)

    def test_one_byte_changed_fails(self):
        changed = EXACT.replace(b'"frames":16000', b'"frames":16001')
        with self.assertRaisesRegex(ValueError, 'SHA differs'):
            require_recipe_artifact_bytes(changed, EXPECTED_SHA)

    def test_missing_terminal_lf_fails(self):
        with self.assertRaisesRegex(ValueError, 'artifact bytes differ'):
            require_recipe_artifact_bytes(EXACT[:-1], EXPECTED_SHA)

    def test_extra_terminal_lf_fails(self):
        with self.assertRaisesRegex(ValueError, 'artifact bytes differ'):
            require_recipe_artifact_bytes(EXACT + b'\n', EXPECTED_SHA)

    def test_wrong_recipe_fails(self):
        wrong = [{**PARTS[0], 'case_id': 'en-read-01'}]
        with self.assertRaisesRegex(ValueError, 'SHA differs'):
            require_recipe_sha256(wrong, EXPECTED_SHA)

    def test_wrong_manifest_hash_fails(self):
        with self.assertRaisesRegex(ValueError, 'SHA differs'):
            require_recipe_artifact_bytes(EXACT, '0' * 64)

    def test_changed_source_clip_hash_invalidates_recipe(self):
        changed = [{**PARTS[0], 'uploaded_wav_sha256': 'b' * 64}]
        with self.assertRaisesRegex(ValueError, 'SHA differs'):
            require_recipe_sha256(changed, EXPECTED_SHA)

    def test_deterministic_rebuild_is_byte_identical(self):
        rebuilt = [dict(reversed(tuple(PARTS[0].items())))]
        self.assertEqual(canonical_recipe_bytes(rebuilt), EXACT)
        self.assertEqual(recipe_sha256(rebuilt), EXPECTED_SHA)

    def test_owned_corpus_producer_encoding_is_exact_same_bytes(self):
        self.assertEqual(encoded(PARTS), canonical_recipe_bytes(PARTS))
        self.assertEqual(recipe_sha256(PARTS), EXPECTED_SHA)


if __name__ == '__main__':
    unittest.main()
