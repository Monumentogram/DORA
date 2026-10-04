"""Build isolated local baseline/observer APKs; never builds or modifies production DORA."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('component', choices=('baseline', 'observer'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--debug-keystore', type=Path, required=True)
    args = parser.parse_args()
    sdk = Path(os.environ['ANDROID_HOME'])
    jdk = Path(os.environ['JAVA_HOME']) / 'bin'
    suffix = '.exe' if os.name == 'nt' else ''
    bt = sdk / 'build-tools/36.0.0'
    android = sdk / 'platforms/android-36/android.jar'
    src = Path(__file__).resolve().parent / args.component
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir()
    dex.mkdir()

    def run(values):
        return subprocess.check_output([str(value) for value in values], text=True)

    run([jdk / ('javac' + suffix), '-cp', android, '-d', classes, *src.glob('*.java')])
    run([jdk / ('java' + suffix), '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8',
         '--min-api', '29' if args.component == 'observer' else '28', '--lib', android,
         '--output', dex, *classes.rglob('*.class')])
    unsigned, aligned, apk = out / 'unsigned.apk', out / 'aligned.apk', out / (args.component + '.apk')
    run([bt / ('aapt2' + suffix), 'link', '--manifest', src / 'AndroidManifest.xml', '-I', android, '-o', unsigned])
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(dex / 'classes.dex', 'classes.dex')
    run([bt / ('zipalign' + suffix), '-f', '4', unsigned, aligned])
    run([jdk / ('java' + suffix), '-jar', bt / 'lib/apksigner.jar', 'sign',
         '--ks', args.debug_keystore, '--ks-pass', 'pass:android', '--out', apk, aligned])
    run([jdk / ('java' + suffix), '-jar', bt / 'lib/apksigner.jar', 'verify', apk])
    print(json.dumps({'component': args.component, 'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest()}))


if __name__ == '__main__':
    main()
