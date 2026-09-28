"""Offline loopback privacy-boundary tests; never opens a microphone."""
import http.client
import importlib.util
import json
from pathlib import Path
import threading
import tempfile
import unittest


class ServerTests(unittest.TestCase):
    def setUp(self):
        path = Path(__file__).with_name('server.py')
        self.assertTrue(path.exists(), 'Local recorder server must exist')
        spec = importlib.util.spec_from_file_location('owned_server', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.module = module
        class PrivateStore:
            def state(self):
                return {'private': 'synthetic transcript'}
            def attest(self, confirmed):
                if confirmed is not True:
                    raise ValueError('EXPLICIT_CONFIRMATION_REQUIRED')
                return {'confirmed': True}
        self.server = module.make_server(PrivateStore(), port=0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.port = self.server.server_address[1]
        self.token = self.server.token

    def tearDown(self):
        if hasattr(self, 'server'):
            self.server.shutdown()
            self.server.server_close()
            self.thread.join()

    def request(self, path, token=True, host=None, origin=None, body=None):
        c = http.client.HTTPConnection('127.0.0.1', self.port)
        headers = {'Host': host or f'127.0.0.1:{self.port}'}
        if token:
            headers['X-Dora-Session'] = self.token
        if origin:
            headers['Origin'] = origin
        if body is not None:
            headers['Content-Type'] = 'application/json'
        c.request('POST' if body is not None else 'GET', path,
                  json.dumps(body) if body is not None else None, headers)
        r = c.getresponse()
        result = r.status, dict(r.getheaders()), r.read()
        c.close()
        return result

    def test_private_state_requires_session_and_is_not_cached(self):
        self.assertEqual(self.request('/api/state', token=False)[0], 403)
        status, headers, data = self.request('/api/state')
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(data), {'private': 'synthetic transcript'})
        self.assertEqual(headers['Cache-Control'], 'no-store')

    def test_dns_rebinding_and_cross_origin_rejected_even_with_token(self):
        self.assertEqual(self.request('/api/state', host='evil.example')[0], 403)
        self.assertEqual(self.request('/api/state', origin='https://evil.example')[0], 403)
        self.assertEqual(self.request('/api/attest', origin='null', body={'confirmed': True})[0], 403)

    def test_explicit_attestation_boolean_and_no_file_browsing(self):
        self.assertEqual(self.request('/api/attest', body={'confirmed': False})[0], 400)
        self.assertEqual(self.request('/api/attest', body={'confirmed': 'true'})[0], 400)
        self.assertEqual(self.request('/api/attest', body={'confirmed': True})[0], 200)
        self.assertEqual(self.request('/../corpus.py')[0], 404)
        self.assertEqual(self.request('/api/audio/../../inventory.json')[0], 404)

    def test_static_shell_contains_no_private_data_or_session_secret(self):
        status, headers, data = self.request('/', token=False)
        self.assertEqual(status, 200)
        self.assertNotIn(b'synthetic transcript', data)
        self.assertNotIn(self.token.encode(), data)
        self.assertIn("connect-src 'self'", headers['Content-Security-Policy'])
        self.assertEqual(headers['Referrer-Policy'], 'no-referrer')

    def test_single_writer_lock_rejects_second_server_and_releases_on_close(self):
        with tempfile.TemporaryDirectory(prefix='dora-lock-test-') as directory:
            class Store:
                root = Path(directory)
            first = self.module.make_server(Store(), port=0)
            try:
                with self.assertRaisesRegex(RuntimeError, 'RECORDER_ALREADY_RUNNING'):
                    self.module.make_server(Store(), port=0)
            finally:
                first.server_close()
            resumed = self.module.make_server(Store(), port=0)
            resumed.server_close()
            self.assertTrue((Path(directory) / '.recorder.lock').exists())


if __name__ == '__main__':
    unittest.main()
