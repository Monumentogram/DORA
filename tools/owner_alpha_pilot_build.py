"""Two owner-local clean release-derived pilot builds, signed outside Gradle. No device actions."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

from build_internal_alpha import signing_material, ALIAS
from owner_arm82 import check_private_runtime, check_model, MODEL_SHA as ASR_MODEL_SHA
from isolated_alpha_apk import AAR_SHA, MODEL_SHA, sha
from owner_alpha_pilot_artifact import (
    ROOT, PRODUCTION_CERTIFICATE, OWNER_CERTIFICATE, EXCLUDED_CERTIFICATES, exact_source, require, check_reproducible,
)


def check_output(path):
    resolved = path.resolve()
    require(not resolved.is_relative_to(ROOT) and not any((parent / '.git').exists()
            for parent in (resolved, *resolved.parents)), 'OUTPUT_MUST_BE_OUTSIDE_GIT')
    require(not resolved.exists(), 'OUTPUT_EXISTS')


def private_inputs(directory):
    directory = directory.resolve()
    aar = directory / 'sherpa-onnx-vad-1.13.8-dora.1-arm64.aar'
    model = directory / 'silero.onnx'
    # Existing owner-managed, ignored input custody can live outside this worktree
    # under the parent checkout. It is never copied into source or public output.
    require(not any(path.resolve().is_relative_to(ROOT) for path in (aar, model)), 'PRIVATE_INPUTS_MUST_BE_OUTSIDE_WORKTREE')
    require(sha(aar) == AAR_SHA and sha(model) == MODEL_SHA, 'PRIVATE_INPUT_PIN_MISMATCH')
    return aar


def gradle_command():
    return [str(ROOT / ('android/gradlew.bat' if os.name == 'nt' else 'android/gradlew')),
            '--no-daemon', '--no-configuration-cache', '--no-build-cache',
            '--dependency-verification', 'strict', '--console=plain']


def build_command(private_vad, private_asr):
    return gradle_command() + [f'-PdoraPrivateVadDir={private_vad}', f'-PdoraPrivateAsrDir={private_asr}', 'clean', ':app:assembleOwnerPilot']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--private-vad-dir', type=Path, required=True)
    parser.add_argument('--private-asr-dir', type=Path, required=True)
    parser.add_argument('--asr-model', type=Path, required=True)
    parser.add_argument('--signing-dir', type=Path, required=True)
    parser.add_argument('--diagnostic-certificate-sha256', required=True)
    parser.add_argument('--debug-certificate-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    exact_source(args.source_sha, 'owner-local')
    check_output(args.output)
    aar = private_inputs(args.private_vad_dir)
    check_private_runtime(args.private_asr_dir)
    check_model(args.asr_model)
    directory, certificate = signing_material(args.signing_dir, ROOT)
    exclusions = (args.diagnostic_certificate_sha256, args.debug_certificate_sha256)
    require(all(re.fullmatch('[a-f0-9]{64}', pin) for pin in exclusions), 'KNOWN_SIGNER_EXCLUSIONS_REQUIRED')
    require({'diagnostic': exclusions[0], 'debug': exclusions[1]} == EXCLUDED_CERTIFICATES, 'KNOWN_SIGNER_EXCLUSIONS_REQUIRED')
    require(certificate == OWNER_CERTIFICATE, 'OWNER_SIGNER_PIN_MISMATCH')
    require(certificate not in {PRODUCTION_CERTIFICATE, *exclusions}, 'SEPARATE_PILOT_SIGNER_REQUIRED')
    output = args.output.resolve()
    sdk = args.sdk.resolve()
    args.private_vad_dir = args.private_vad_dir.resolve()
    output.mkdir(parents=True)
    env = os.environ.copy()
    env.update(ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    graph_command = gradle_command() + ['-I', '../tools/owner_alpha_pilot_graph.init.gradle', ':app:exportOwnerAlphaPilotGraph']
    with (output / 'graph.local.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(graph_command, cwd=ROOT / 'android', env=env, stdout=log, stderr=subprocess.STDOUT)
    require(result.returncode == 0, 'PILOT_GRAPH_EXPORT_FAILED')
    graph = output / 'owner-pilot-graph.json'
    shutil.copyfile(ROOT / 'android/app/build/reports/owner-alpha-pilot-graph.json', graph)
    from alpha_release_sbom import canonical_bytes
    from owner_alpha_pilot_sbom import generate
    bom = generate(json.loads(graph.read_text()), args.source_sha, private_vad=True, private_asr=True)
    sbom = output / 'owner-pilot.cdx.json'
    sbom.write_bytes(canonical_bytes(bom))
    for index in (1, 2):
        exact_source(args.source_sha, 'owner-local')
        private_inputs(args.private_vad_dir)
        check_private_runtime(args.private_asr_dir)
        destination = output / f'build{index}'
        destination.mkdir()
        print(f'Start ownerPilot clean strict build {index}', flush=True)
        with (destination / 'gradle.local.log').open('w', encoding='utf-8') as log:
            result = subprocess.run(build_command(args.private_vad_dir, args.private_asr_dir.resolve()), cwd=ROOT / 'android', env=env,
                                    stdout=log, stderr=subprocess.STDOUT)
        require(result.returncode == 0, f'PILOT_CLEAN_BUILD_{index}_FAILED')
        exact_source(args.source_sha, 'owner-local')
        unsigned = destination / 'unsigned.apk'
        shutil.copyfile(ROOT / 'android/app/build/outputs/apk/ownerPilot/app-ownerPilot-unsigned.apk', unsigned)
        artifact = destination / 'candidate.apk'
        command = [str(sdk / ('build-tools/36.0.0/apksigner.bat' if os.name == 'nt' else 'build-tools/36.0.0/apksigner')),
                   'sign', '--ks', str(directory / 'alpha.p12'), '--ks-key-alias', ALIAS,
                   '--ks-pass', 'file:' + str(directory / 'password.txt'), '--v4-signing-enabled', 'false',
                   '--out', str(artifact), str(unsigned)]
        result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True)
        require(result.returncode == 0, 'OWNER_LOCAL_SIGNING_FAILED')
        (destination / 'source.json').write_bytes(canonical_bytes({
            'source_sha': args.source_sha, 'source_clean': True, 'variant': 'ownerPilot',
            'independent_clean_build': index, 'sha256': sha(artifact), 'unsigned_sha256': sha(unsigned),
            'no_build_cache': True, 'dependency_verification': 'strict',
            'asr_model_sha256': ASR_MODEL_SHA, 'asr_model_packaged': False,
            'arm82_device_runtime': 'NOT_RUN', 'aar_sha256': AAR_SHA, 'model_sha256': MODEL_SHA, 'graph_sha256': sha(graph), 'sbom_sha256': sha(sbom),
        }))
        print(f'OwnerPilot clean build {index} complete', flush=True)
    check_reproducible(output / 'build1/unsigned.apk', output / 'build2/unsigned.apk')
    check_reproducible(output / 'build1/candidate.apk', output / 'build2/candidate.apk')
    exact_source(args.source_sha, 'owner-local')
    command = [sys.executable, str(ROOT / 'tools/owner_alpha_pilot_artifact.py'), '--mode', 'owner-local',
        '--apk', str(output / 'build1/candidate.apk'), '--compare', str(output / 'build2/candidate.apk'),
        '--build-receipt', str(output / 'build1/source.json'), '--compare-build-receipt', str(output / 'build2/source.json'),
        '--sdk', str(sdk), '--source-sha', args.source_sha, '--certificate-sha256', certificate,
        '--diagnostic-certificate-sha256', args.diagnostic_certificate_sha256,
        '--debug-certificate-sha256', args.debug_certificate_sha256, '--aar', str(aar),
        '--graph', str(graph), '--sbom', str(sbom), '--receipt', str(output / 'artifact-preflight.json')]
    subprocess.run(command, cwd=ROOT, env=env, check=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError):
        print('FAIL: pilot build evidence incomplete; inspect owner-local output. Installation and distribution remain blocked.', file=sys.stderr)
        sys.exit(1)
