"""Closed Pilot v4 native inventory. Host-only; never accesses an Android device."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

from alpha_security_preflight import require
from isolated_alpha_apk import sha

ROOT = Path(__file__).resolve().parents[1]
MODEL_SHA = '49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f'
MODEL_BYTES = 264464607
STATIC_RECEIPT = ROOT / 'docs/evidence/stage9-asr/arm64-static-build.json'
# Unchanged ARM64 payload from the independently audited Pilot v3 at 4ee27ef.
PILOT_V3_NATIVE = {
    'libandroidx.graphics.path.so': '41e9a793c43a0f4fddb19e33f346bace464f30f888ba7b9eaf96294ea115bfb6',
    'libonnxruntime.so': '33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1',
    'libsherpa-onnx-jni.so': '2e7412da436e02b705cea92d6fe6e3d2f2bf97a483b12d9ae2cc65ad6b723cc7',
    'libsqlcipher.so': '52e2f692a1d59ab6222ab86b516885f930479de7c6ea2b07c4bb48d745ac3ae9',
}
SYSTEM_LIBS = {'libc.so', 'libdl.so', 'libm.so', 'liblog.so', 'libandroid.so', 'libz.so'}
ASR_LINKS = {
    'libc++_shared.so': [],
    'libdora-whisper-jni.so': ['libc++_shared.so'],
    'libggml-base.so': ['libc++_shared.so'],
    'libggml-cpu.so': ['libggml-base.so', 'libc++_shared.so'],
    'libggml.so': ['libggml-cpu.so', 'libggml-base.so', 'libc++_shared.so'],
    'libwhisper.so': ['libggml.so', 'libggml-cpu.so', 'libggml-base.so', 'libc++_shared.so'],
}


def receipt():
    return json.loads(STATIC_RECEIPT.read_text(encoding='utf-8'))


def native_pins():
    return {'lib/arm64-v8a/' + name: pin for name, pin in {
        **PILOT_V3_NATIVE, **{name: item['sha256'] for name, item in receipt()['artifacts'].items()}
    }.items()}


def check_payload(inventory):
    actual = {name: pin for name, pin in inventory.items() if name.startswith('lib/') and name.endswith('.so')}
    require(actual == native_pins(), 'ARM82_CLOSED_NATIVE_INVENTORY_MISMATCH')
    require(not any(pin == MODEL_SHA or ('ggml' in name.lower() and not name.endswith('.so'))
                    or name.lower().endswith(('.wav', '.pcm', '.mp3', '.p12', '.jks'))
                    for name, pin in inventory.items()), 'PRIVATE_MODEL_AUDIO_OR_KEY_IN_APK')


def check_sources():
    for name, pin in receipt()['sourcePins'].items():
        source = ROOT / 'android/app/src/main/cpp' / name
        require(source.stat().st_size == pin['bytes'] and sha(source) == pin['sha256'],
                'ARM82_ADAPTER_SOURCE_PIN_MISMATCH')


def check_private_runtime(directory):
    directory = Path(directory).resolve()
    require(not directory.is_relative_to(ROOT), 'PRIVATE_ASR_INSIDE_WORKTREE')
    require(json.loads((directory.parent / 'build-receipt.json').read_text()) == receipt(),
            'ARM82_RECEIPT_MISMATCH')
    require({p.name for p in directory.iterdir()} == {'arm64-v8a'}, 'ARM82_ABI_MISMATCH')
    actual = {p.name: sha(p) for p in (directory / 'arm64-v8a').iterdir() if p.is_file() and not p.is_symlink()}
    require(actual == {n: p['sha256'] for n, p in receipt()['artifacts'].items()}, 'ARM82_PRIVATE_PINS_MISMATCH')
    check_sources()


def check_model(model):
    model = Path(model).resolve()
    require(not model.is_relative_to(ROOT) and not any((p / '.git').exists() for p in model.parents),
            'MODEL_MUST_BE_OUTSIDE_GIT')
    require(model.is_file() and model.stat().st_size == MODEL_BYTES and sha(model) == MODEL_SHA,
            'MODEL_IDENTITY_MISMATCH')


def check_needed(name, needed):
    names = {Path(path).name for path in native_pins()}
    require(set(needed) <= names | SYSTEM_LIBS, 'UNRESOLVED_NATIVE_DEPENDENCY')
    if name in ASR_LINKS:
        require(set(needed) - SYSTEM_LIBS == set(ASR_LINKS[name]), 'ASR_DEPENDENCY_GRAPH_MISMATCH')
    if name == 'libdora-whisper-jni.so':
        require(set(needed) == set(receipt()['adapterNeeded']), 'EAGER_ARM82_DEPENDENCY')


def audit_dependencies(apk, sdk):
    tool = Path(sdk) / 'ndk/28.2.13676358/toolchains/llvm/prebuilt' / (
        'windows-x86_64/bin/llvm-readelf.exe' if os.name == 'nt' else 'linux-x86_64/bin/llvm-readelf')
    require(tool.is_file(), 'PINNED_NDK_READELF_REQUIRED')
    dependencies = {}
    with tempfile.TemporaryDirectory(prefix='dora-arm82-elf-') as temporary, zipfile.ZipFile(apk) as archive:
        for name in native_pins():
            data = archive.read(name)
            require(data[:6] == b'\x7fELF\x02\x01' and int.from_bytes(data[18:20], 'little') == 183,
                    'NATIVE_NOT_AARCH64')
            file = Path(temporary) / Path(name).name
            file.write_bytes(data)
            result = subprocess.run([str(tool), '--dynamic', '--dyn-syms', str(file)],
                                    capture_output=True, text=True, check=True, timeout=60)
            needed = sorted(re.findall(r'\(NEEDED\).*\[([^\]]+)\]', result.stdout))
            check_needed(file.name, needed)
            if file.name == 'libdora-whisper-jni.so':
                exports = set(re.findall(r'\bJava_com_monumentogram_dora_transcript_WhisperNative_\w+', result.stdout))
                require(exports == set(receipt()['adapterExports']), 'JNI_EXPORT_MISMATCH')
            dependencies[file.name] = needed
    return dependencies


def enrich_sbom(bom, source):
    require(re.fullmatch('[a-f0-9]{40}', source), 'INVALID_ADAPTER_SOURCE_SHA')
    check_sources()
    app = next(edge for edge in bom['dependencies'] if edge['ref'] == 'project::app')
    for name, pin in sorted(receipt()['artifacts'].items()):
        ref = 'private-arm82:' + name
        license_expression = 'Apache-2.0 WITH LLVM-exception' if name == 'libc++_shared.so' else 'MIT'
        component = {'type': 'library', 'bom-ref': ref, 'name': name,
            'version': receipt()['runtimeCommit'] if name != 'libc++_shared.so' else receipt()['ndk'],
            'hashes': [{'alg': 'SHA-256', 'content': pin['sha256']}],
            'licenses': [{'expression': license_expression}],
            'properties': [{'name': 'dora:abi', 'value': 'arm64-v8a'}]}
        if name == 'libdora-whisper-jni.so':
            component['version'] = source
            component.pop('licenses')  # Project adapter has no separately assigned upstream license.
            component['properties'].extend([
                {'name': 'dora:adapter-source-sha256', 'value': receipt()['sourcePins']['whisper_jni.cpp']['sha256']},
                {'name': 'dora:cmake-source-sha256', 'value': receipt()['sourcePins']['CMakeLists.txt']['sha256']},
                {'name': 'dora:license-disposition', 'value': 'OWNER_LOCAL_PROJECT_SOURCE_NO_PUBLIC_DISTRIBUTION'},
                {'name': 'dora:ndk', 'value': receipt()['ndk']},
            ])
        bom['components'].append(component)
        bom['dependencies'].append({'ref': ref, 'dependsOn': ['private-arm82:' + item for item in ASR_LINKS[name]]})
        app['dependsOn'].append(ref)
    bom['metadata']['properties'].extend([
        {'name': 'dora:private-asr-packaged', 'value': 'true'},
        {'name': 'dora:external-asr-model-sha256', 'value': MODEL_SHA},
        {'name': 'dora:asr-model-packaged', 'value': 'false'},
        {'name': 'dora:arm82-device-runtime', 'value': 'NOT_RUN'},
    ])
    return bom
