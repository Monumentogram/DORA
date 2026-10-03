"""Bounded Stage8.4 publication audit. No traversal of private audio or model directories."""
import argparse
import hashlib
import json
from pathlib import Path
import re

from validate_vad_runtime import ROOT, read_contract

BINARY_SUFFIXES = {'.aar', '.onnx', '.wav', '.pcm', '.mp3', '.apk', '.so', '.flac', '.m4a'}
PATTERNS = (
    re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    re.compile(rb'\bgh[pousr]_[A-Za-z0-9]{30,}\b'),
    re.compile(rb'\bAKIA[A-Z0-9]{16}\b'),
    re.compile(rb'\bGOCSPX-[A-Za-z0-9_-]{20,}\b'),
    re.compile(rb'(?i)(?:C:[/\\]Users[/\\]|/Users/)[A-Za-z0-9_.-]+[/\\]'),
)


def findings(name, data):
    result = []
    if Path(name).suffix.lower() in BINARY_SUFFIXES:
        result.append('PRIVATE_BINARY_OR_AUDIO')
    if data.startswith((b'PK\x03\x04', b'RIFF', b'\x7fELF')):
        result.append('BINARY_MAGIC')
    if any(pattern.search(data) for pattern in PATTERNS):
        result.append('CREDENTIAL_OR_LOCAL_IDENTITY_PATTERN')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--private-log', type=Path, action='append', default=[])
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    if args.receipt.exists():
        raise ValueError('Preserve prior audit receipts')
    contract = read_contract()
    selected = sorted(set(contract['files']) | {'tools/validate_vad_runtime.py',
                                               'docs/contracts/DORA_VAD_RUNTIME_8_4_V0_1.json'})
    rows = []
    for relative in selected:
        data = (ROOT / relative).read_bytes()
        if findings(relative, data):
            raise ValueError('Publication audit rejected ' + relative)
        rows.append({'path': relative, 'sha256': hashlib.sha256(data).hexdigest()})
    private = []
    for path in args.private_log:
        data = path.read_bytes()
        if findings(path.name, data):
            raise ValueError('Private run log contains an unpublishable pattern')
        private.append({'name': path.name, 'sha256': hashlib.sha256(data).hexdigest()})
    # Synthetic positive controls in memory only; never print credential-like payloads.
    controls = [findings('payload.wav', b'x'), findings('payload.txt', b'RIFF' + b'x' * 8),
                findings('payload.txt', b'gh' + b'p_' + b'A' * 36),
                findings('payload.txt', b'AK' + b'IA' + b'B' * 16)]
    if not all(controls):
        raise ValueError('Audit canary failed')
    args.receipt.write_text(json.dumps({'status': 'PASS_BOUNDED_PUBLICATION_AUDIT',
        'scope': 'sealed implementation paths and explicitly selected content-free run logs',
        'files': rows, 'private_logs': private, 'positive_controls': len(controls),
        'raw_audio_examined': False}, indent=2) + '\n', encoding='utf-8')
    print('PASS bounded publication audit; positive controls: ' + str(len(controls)))


if __name__ == '__main__':
    main()
