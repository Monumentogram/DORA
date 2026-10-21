"""Two exact-source owner-local recording Alpha builds. Never installs or distributes."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from alpha_release_identity import CERTIFICATE_SHA256
from alpha_security_preflight import ROOT, require, check_version
from build_internal_alpha import signing_material, ALIAS
from isolated_alpha_apk import AAR_SHA, MODEL_SHA, sha

BASE = 'd56a64f079d7bf9acf1d548545dfbb9c38858bde'


def exact_source(expected):
    def git(*args):
        return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()
    require(git('rev-parse', 'HEAD') == expected, 'SOURCE_SHA_MISMATCH')
    require(not git('status', '--porcelain=v1', '--untracked-files=all'), 'SOURCE_NOT_CLEAN')
    require(git('branch', '--show-current') == 'codex/alpha-security-release-preflight', 'UNADMITTED_BRANCH')
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, expected], cwd=ROOT, check=True,
                   capture_output=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--private-vad-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--installed-version-code', type=int, required=True)
    parser.add_argument('--signing-dir', type=Path)
    args = parser.parse_args()
    exact_source(args.source_sha)
    from alpha_release_identity import identity
    check_version(identity()[0], args.installed_version_code)
    require(os.environ.get('GITHUB_ACTIONS') != 'true', 'OWNER_LOCAL_ONLY')
    require(not args.output.resolve().is_relative_to(ROOT), 'OUTPUT_MUST_BE_OUTSIDE_REPOSITORY')
    require(not args.output.exists(), 'OUTPUT_EXISTS')
    aar = args.private_vad_dir / 'sherpa-onnx-vad-1.13.8-dora.1-arm64.aar'
    require(sha(aar) == AAR_SHA and sha(args.private_vad_dir / 'silero.onnx') == MODEL_SHA, 'PRIVATE_INPUT_PIN_MISMATCH')
    signer_directory = None
    if args.signing_dir:
        signer_directory, pin = signing_material(args.signing_dir, ROOT)
        require(pin == CERTIFICATE_SHA256, 'SIGNER_PIN_MISMATCH')
    args.output.mkdir(parents=True)
    env = os.environ.copy()
    env.update(ANDROID_HOME=str(args.sdk), ANDROID_SDK_ROOT=str(args.sdk))
    suffix = '.bat' if os.name == 'nt' else ''
    wrapper = ROOT / ('android/gradlew' + suffix)
    common = [str(wrapper), '--no-daemon', '--no-configuration-cache', '--no-build-cache',
              '--dependency-verification', 'strict', '--console=plain']
    # Export the selected Maven/project graph independently of the pinned local file input.
    graph_command = common + ['-I', '../tools/alpha_release_graph.init.gradle', ':app:exportAlphaReleaseGraph']
    with (args.output / 'graph.local.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(graph_command, cwd=ROOT / 'android', env=env, stdout=log, stderr=subprocess.STDOUT)
    require(result.returncode == 0, 'RELEASE_GRAPH_FAILED')
    shutil.copyfile(ROOT / 'android/app/build/reports/alpha-release-graph.json', args.output / 'release-graph.json')
    for index in (1, 2):
        exact_source(args.source_sha)
        destination = args.output / f'build{index}'
        destination.mkdir()
        command = common + [f'-PdoraPrivateVadDir={args.private_vad_dir}', 'clean', ':app:assembleRelease']
        print(f'Start independent clean strict release build {index}', flush=True)
        with (destination / 'gradle.local.log').open('w', encoding='utf-8') as log:
            result = subprocess.run(command, cwd=ROOT / 'android', env=env, stdout=log, stderr=subprocess.STDOUT)
        require(result.returncode == 0, f'CLEAN_BUILD_{index}_FAILED')
        exact_source(args.source_sha)
        unsigned = destination / 'unsigned.apk'
        shutil.copyfile(ROOT / 'android/app/build/outputs/apk/release/app-release-unsigned.apk', unsigned)
        artifact = destination / 'candidate.apk'
        if signer_directory:
            command = [str(args.sdk / ('build-tools/36.0.0/apksigner' + suffix)), 'sign',
                       '--ks', str(signer_directory / 'alpha.p12'), '--ks-key-alias', ALIAS,
                       '--ks-pass', 'file:' + str(signer_directory / 'password.txt'),
                       '--v4-signing-enabled', 'false', '--out', str(artifact), str(unsigned)]
            result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True)
            require(result.returncode == 0, 'OWNER_LOCAL_SIGNING_FAILED')
        else:
            shutil.copyfile(unsigned, artifact)
        (destination / 'source.json').write_text(json.dumps({'source_sha': args.source_sha,
            'source_clean': True, 'independent_clean_build': index, 'sha256': sha(artifact)}) + '\n')
        print(f'Clean build {index} complete', flush=True)
    exact_source(args.source_sha)
    command = [sys.executable, str(ROOT / 'tools/alpha_security_preflight.py'),
               '--apk', str(args.output / 'build1/candidate.apk'), '--compare', str(args.output / 'build2/candidate.apk'),
               '--sdk', str(args.sdk), '--source-sha', args.source_sha,
               '--installed-version-code', str(args.installed_version_code), '--aar', str(aar),
               '--receipt', str(args.output / 'artifact-preflight.json')]
    if signer_directory:
        command.append('--signed')
    subprocess.run(command, cwd=ROOT, env=env, check=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError):
        print('FAIL: candidate incomplete; inspect owner-local evidence. No installation or distribution authorized.', file=sys.stderr)
        sys.exit(1)
