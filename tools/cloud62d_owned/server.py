"""Private loopback-only corpus acquisition. No AWS client or upload capability."""
import argparse
import base64
import binascii
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import sys
import webbrowser

ASSETS = {'/': ('ui.html', 'text/html; charset=utf-8'),
          '/ui.js': ('ui.js', 'text/javascript; charset=utf-8'),
          '/audio.js': ('audio.js', 'text/javascript; charset=utf-8'),
          '/capture-worklet.js': ('capture-worklet.js', 'text/javascript; charset=utf-8')}
MAX_BODY = 48 * 1024 * 1024


class _ProcessLock:
    """Lifetime OS lock; a crash releases it without deleting any corpus file."""
    def __init__(self, root):
        self.file = None
        if root is None:
            return
        root.mkdir(parents=True, exist_ok=True)
        self.file = (root / '.recorder.lock').open('a+b')
        try:
            self.file.seek(0, 2)
            if self.file.tell() == 0:
                self.file.write(b'\0')
                self.file.flush()
            self.file.seek(0)
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(self.file.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            self.file.close()
            self.file = None
            raise RuntimeError('RECORDER_ALREADY_RUNNING') from exc

    def close(self):
        if self.file is not None:
            self.file.close()
            self.file = None


class _LockedServer(HTTPServer):
    def __init__(self, address, handler, lock):
        self._corpus_lock = lock
        super().__init__(address, handler)

    def server_close(self):
        try:
            super().server_close()
        finally:
            self._corpus_lock.close()


def make_server(store, port=0):
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(15)

        def log_message(self, *args):
            pass  # Never log private IDs, text, paths, or session tokens.

        def reply(self, status, value, mime='application/json; charset=utf-8'):
            raw = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header('Content-Type', mime)
            self.send_header('Content-Length', str(len(raw)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Referrer-Policy', 'no-referrer')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('X-Frame-Options', 'DENY')
            self.send_header('Content-Security-Policy', "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; media-src blob:; worker-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
            self.end_headers()
            self.wfile.write(raw)

        def allowed(self, private=False):
            expected = f'127.0.0.1:{self.server.server_address[1]}'
            if self.headers.get('Host') != expected:
                return False
            if self.headers.get('Origin') not in (None, 'http://' + expected):
                return False
            return not private or secrets.compare_digest(self.headers.get('X-Dora-Session', ''), self.server.token)

        def do_GET(self):
            if not self.allowed(private=self.path.startswith('/api/')):
                return self.reply(403, {'error': 'LOCAL_SESSION_REQUIRED'})
            if self.path in ASSETS:
                name, mime = ASSETS[self.path]
                return self.reply(200, Path(__file__).with_name(name).read_bytes(), mime)
            try:
                if self.path == '/api/state':
                    return self.reply(200, store.state())
                match = re.fullmatch(r'/api/audio/([A-Za-z0-9_-]{1,80})', self.path)
                if match:
                    return self.reply(200, store.get_audio(match[1]).read_bytes(), 'audio/wav')
                return self.reply(404, {'error': 'NOT_FOUND'})
            except (ValueError, KeyError, FileNotFoundError):
                return self.reply(400, {'error': 'CLIP_UNAVAILABLE'})

        def do_POST(self):
            if not self.allowed(private=True):
                return self.reply(403, {'error': 'LOCAL_SESSION_REQUIRED'})
            if self.headers.get('Content-Type') != 'application/json' or self.headers.get('Transfer-Encoding'):
                return self.reply(415, {'error': 'JSON_REQUIRED'})
            try:
                size = int(self.headers.get('Content-Length', '-1'))
                if not 0 <= size <= MAX_BODY:
                    return self.reply(413, {'error': 'REQUEST_TOO_LARGE'})
                body = json.loads(self.rfile.read(size))
                if not isinstance(body, dict):
                    raise ValueError('OBJECT_REQUIRED')
                if self.path == '/api/attest':
                    store.attest(confirmed=body.get('confirmed') is True)
                elif self.path == '/api/capture':
                    source = base64.b64decode(body['source'], validate=True)
                    evaluation = base64.b64decode(body['evaluation'], validate=True)
                    store.save_capture(body['id'], source, evaluation)
                elif self.path == '/api/reference':
                    store.verify_reference(body['id'], body['text'], confirmed=body.get('confirmed') is True)
                elif self.path == '/api/timing':
                    store.save_timings(body['id'], body['words'], confirmed=body.get('confirmed') is True)
                elif self.path == '/api/timing-draft':
                    store.save_timing_draft(body['id'], body['words'])
                elif self.path == '/api/exclude':
                    store.exclude(body['id'], body['reason'])
                elif self.path == '/api/select':
                    store.select()
                elif self.path == '/api/finalize':
                    store.finalize()
                else:
                    return self.reply(404, {'error': 'NOT_FOUND'})
                return self.reply(200, store.state())
            except (ValueError, KeyError, TypeError, binascii.Error) as exc:
                # Backend errors are stable codes; no stack traces or private paths.
                message = str(exc)
                safe = message if re.fullmatch(r'[A-Z][A-Z0-9_ :.,/-]{0,220}', message) else 'VALIDATION_FAILED'
                return self.reply(400, {'error': safe})
            except OSError:
                return self.reply(500, {'error': 'PRIVATE_STORAGE_UNAVAILABLE'})

    lock = _ProcessLock(getattr(store, 'root', None))
    try:
        server = _LockedServer(('127.0.0.1', port), Handler, lock)
    except BaseException:
        lock.close()
        raise
    server.token = secrets.token_urlsafe(32)
    return server


def main():
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    from corpus import CorpusStore
    parser = argparse.ArgumentParser()
    repo = Path(__file__).resolve().parents[2]
    parser.add_argument('--root', type=Path, default=repo.parent / '.dora-62d-private' / 'owned-corpus-v1')
    parser.add_argument('--no-browser', action='store_true')
    args = parser.parse_args()
    store = CorpusStore(args.root, repo)
    try:
        server = make_server(store)
    except RuntimeError as exc:
        if str(exc) != 'RECORDER_ALREADY_RUNNING':
            raise
        print('RECORDER_ALREADY_RUNNING: use the already open recorder, or close its console before relaunching.', file=sys.stderr)
        return 1
    try:
        store.initialize()
    except BaseException:
        server.server_close()
        raise
    url = f'http://127.0.0.1:{server.server_address[1]}/#{server.token}'
    print('DORA private recorder is running locally. Close this window to stop. No cloud upload.', flush=True)
    if not args.no_browser:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    raise SystemExit(main())
