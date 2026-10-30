"""Bind the actual compiled label (including configuration alternatives) to APK identity."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess

from alpha_security_preflight import require

IDENTITIES = {
    'release': ('com.monumentogram.dora', '5', '0.1.0-alpha.3'),
    'debug': ('com.monumentogram.dora.debug', '5', '0.1.0-alpha.3-debug'),
    'alphaTest': ('com.monumentogram.dora.alpha.test', '5', '0.1.0-alpha.3'),
    'ownerPreview': ('com.monumentogram.dora.alpha.preview', '9', '0.1.0-owner-preview.9'),
    'ownerPilot': ('com.monumentogram.dora.alpha.pilot', '4', '0.1.0-owner-alpha.4'),
}


def check_label(badging, resources, variant):
    identities = re.findall(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    require(variant in IDENTITIES and identities == [IDENTITIES[variant]], 'VERSION_LABEL_APK_IDENTITY')
    package, code, name = identities[0]
    prefix = 'DORA Owner Preview' if variant == 'ownerPreview' else ('DORA Owner Alpha' if variant == 'ownerPilot' else 'DORA Alpha')
    expected = f'{prefix} {name} ({code})'
    # Inspect each concrete resource entry, never a spec declaration or an unrelated string.
    # No translated/qualified alternative is admitted: identity is locale-independent.
    entries = []
    config = None
    lines = resources.splitlines()
    for index, line in enumerate(lines):
        match = re.fullmatch(r'\s*config (.+):', line)
        if match:
            config = match[1]
        if re.match(r'\s*resource \S+ \S+:string/alpha_version_label:', line):
            require(re.fullmatch(r'\s*resource 0x[0-9a-f]+ ' + re.escape(package) +
                                r':string/alpha_version_label: t=0x03 .+', line), 'VERSION_LABEL_RESOURCE_TYPE')
            value = re.fullmatch(r'\s*\(string8?\) "([^"\r\n]*)"', lines[index + 1] if index + 1 < len(lines) else '')
            require(value is not None, 'VERSION_LABEL_RESOURCE_VALUE')
            entries.append((config, value[1]))
    require(entries == [('(default)', expected)], 'VERSION_LABEL_RESOURCE_MISMATCH')
    return expected


def inspect_apk(apk, sdk, variant):
    aapt = Path(sdk) / ('build-tools/36.0.0/aapt' + ('.exe' if os.name == 'nt' else ''))
    def dump(*args):
        result = subprocess.run([str(aapt), 'dump', *args, str(apk)], capture_output=True, text=True, timeout=120)
        require(result.returncode == 0, 'VERSION_LABEL_AAPT_FAILED')
        return result.stdout
    return check_label(dump('badging'), dump('--values', 'resources'), variant)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_HOME'))
    parser.add_argument('--variant', choices=IDENTITIES, required=True)
    args = parser.parse_args()
    require(args.sdk is not None, 'SDK_REQUIRED')
    print(json.dumps({'variant': args.variant, 'compiled_label': inspect_apk(args.apk, args.sdk, args.variant), 'status': 'PASS'}))


if __name__ == '__main__':
    main()
