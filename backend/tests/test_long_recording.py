"""Candidate backend only: no deployment and no actual AWS calls."""
import unittest
from test_service import ServiceTests, wav
from dora_cloud.errors import ServiceError


class LongRecordingTests(ServiceTests):
    def test_thirty_minute_binding_and_media_bound(self):
        self.binding['frames'] = 28_800_000
        self.create()
        job = self.store.job(self.action)
        self.service._validate_audio(job, wav(28_800_000))
        job['binding']['frames'] = 28_800_001
        with self.assertRaises(ServiceError):
            self.service._validate_audio(job, wav(28_800_001))
        self.assertEqual([], self.aws.starts)

    def test_oversize_binding_rejected_before_storage(self):
        self.binding['frames'] = 28_800_001
        self.assertEqual(422, self.request('POST', '/v1/recordings', self.binding).status_code)
        self.assertEqual(0, self.store.db.execute('SELECT count(*) FROM recordings').fetchone()[0])


def load_tests(loader, tests, pattern):
    return unittest.TestSuite(loader.loadTestsFromName(name, LongRecordingTests)
                             for name in LongRecordingTests.__dict__ if name.startswith('test_'))
