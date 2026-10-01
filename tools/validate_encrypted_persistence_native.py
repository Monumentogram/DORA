"""Verify the exact composite and distributed license texts; runtime proof is separate."""
import hashlib
import io
import json
from pathlib import Path
import zipfile

from validate_encrypted_persistence import VERIFIED_ARTIFACTS, require
from verify_apk_native_alignment import PAGE_SIZE, load_segments

ROOT = Path(__file__).resolve().parents[1]
VENDOR = 'android/vendor/maven/com/monumentogram/dora/thirdparty/sqlcipher-android/4.17.0-dora.1'
ASSETS = 'android/app/src/main/assets/third_party/sqlcipher'
ABIS = {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}
SOURCE_PINS = {
    'coordinate': 'com.monumentogram.dora.thirdparty:sqlcipher-android:4.17.0-dora.1',
    'originalAarSha256': '44fc40c33d1de597c8339072a71fa0ff20e12d01ab352d6abe4ad5df668ead94',
    'androidSource': 'ae57a61052d8c41ce35cd48319b2f6f20f4de6bf',
    'androidTagOriginalCore': 'e2a6040f2ae5cfff2b3e08eb3320007d93cdf3fc',
    'explicitCoreSource': '810db22f575ee7cf94ea96a3e91622b5fcece3dc',
    'libtomcrypt': '476a9579ae94f32b9ea9e2747bfb04b302370259',
    'ndk': '28.2.13676358',
    'androidApiFloor': 28,
    'amalgamationSha256': 'e00ff0ca534baa14cdb30bb172bbd355e60ec2ad62a77660cc04c99ffa00f4d6',
    'headerSha256': 'b43f231e215c77500c10a11e61eaef8d75629f4c51dd021e862136d9bcf95c26',
}


def read_inputs(root=ROOT):
    directory = root / VENDOR
    return ((directory / 'sqlcipher-android-4.17.0-dora.1.aar').read_bytes(),
            (directory / 'sqlcipher-android-4.17.0-dora.1.pom').read_bytes(),
            json.loads((root / 'android/vendor/sqlcipher/provenance.json').read_text()),
            {path.name: path.read_bytes() for path in (root / ASSETS).glob('*.txt')})


def validate_bundle(aar, pom, provenance, assets):
    for payload, extension in ((aar, 'aar'), (pom, 'pom')):
        hashes = VERIFIED_ARTIFACTS[(*SOURCE_PINS['coordinate'].split(':'),
                                    'sqlcipher-android-4.17.0-dora.1.' + extension)]
        require(hashlib.sha256(payload).hexdigest() in hashes, 'Unadmitted native artifact bytes')
        require(provenance[extension + 'Sha256'] == hashlib.sha256(payload).hexdigest(),
                'Native provenance artifact mismatch')
    require(all(provenance.get(k) == value for k, value in SOURCE_PINS.items()),
            'Unadmitted composite source/toolchain identity')
    require(set(provenance['native']) == ABIS, 'Native ABI inventory changed')
    with zipfile.ZipFile(io.BytesIO(aar)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate native artifact ZIP entry')
        require({n for n in names if n.startswith('jni/')} == {f'jni/{abi}/libsqlcipher.so' for abi in ABIS},
                'Unexpected packaged native entry')
        require(hashlib.sha256(archive.read('classes.jar')).hexdigest() == provenance['classesJarSha256'],
                'Published Java wrapper identity changed')
        for abi in ABIS:
            payload = archive.read(f'jni/{abi}/libsqlcipher.so')
            row = provenance['native'][abi]
            segments = load_segments(payload, abi)
            require(hashlib.sha256(payload).hexdigest() == row['sha256'] and len(payload) == row['bytes'],
                    'Native ABI binary identity changed')
            require([s[2] for s in segments] == row['loadAlignment']
                    and all(align >= PAGE_SIZE and (address - offset) % PAGE_SIZE == 0
                            for offset, address, align in segments), 'Native 16KiB ELF policy changed')
            require(b'__android_log_' not in payload, 'Native Android logging import remains')
        licenses = {Path(name).name: archive.read(name) for name in names if name.startswith('META-INF/LICENSES/')}
        require(len(licenses) == 6 and set(assets) == set(licenses) | {'NOTICE.txt'},
                'Incomplete distributed native license inventory')
        require(all(assets[name] == payload for name, payload in licenses.items()),
                'Distributed native license/notice bytes changed')
        require(b'4.17.0-dora.1' in assets['NOTICE.txt'] and b'DORA modifications' in assets['NOTICE.txt'],
                'Composite modification notice missing')


def validate_apk(apk, root=ROOT):
    aar, pom, provenance, assets = read_inputs(root)
    validate_bundle(aar, pom, provenance, assets)
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require({n for n in names if n.endswith('/libsqlcipher.so')}
                == {f'lib/{abi}/libsqlcipher.so' for abi in ABIS}, 'APK SQLCipher ABI inventory changed')
        for abi in ABIS:
            require(hashlib.sha256(archive.read(f'lib/{abi}/libsqlcipher.so')).hexdigest()
                    == provenance['native'][abi]['sha256'], 'APK substituted native SQLCipher')
        for name, payload in assets.items():
            require(archive.read('assets/third_party/sqlcipher/' + name) == payload,
                    'APK omitted or changed distributed native notices')


def native_sbom(provenance):
    """Deterministic native constituents; does not substitute for the Gradle graph."""
    ref = 'pkg:maven/com.monumentogram.dora.thirdparty/sqlcipher-android@4.17.0-dora.1'
    components = []
    for name, version, license_expression, source in (
        ('SQLCipher Android wrapper and JNI', '4.17.0', 'BSD-3-Clause AND Apache-2.0', SOURCE_PINS['androidSource']),
        ('SQLCipher core', '4.17.0', 'BSD-3-Clause', SOURCE_PINS['explicitCoreSource']),
        ('SQLite', '3.53.3', None, SOURCE_PINS['amalgamationSha256']),
        ('LibTomCrypt', SOURCE_PINS['libtomcrypt'], 'WTFPL', SOURCE_PINS['libtomcrypt']),
        ('Android NDK linked LLVM static runtime inputs', SOURCE_PINS['ndk'], 'Apache-2.0 WITH LLVM-exception', None),
    ):
        item = {'type': 'library', 'bom-ref': 'native:' + name.replace(' ', '-'), 'name': name, 'version': version,
                'licenses': [{'expression': license_expression}] if license_expression else
                [{'license': {'name': 'SQLite public-domain dedication'}}]}
        if source:
            item['properties'] = [{'name': 'dora:pinned-source-identity', 'value': source}]
        else:
            item['properties'] = [
                {'name': 'dora:clang-version', 'value': '19.0.1 / r530567e'},
                {'name': 'dora:runtime-scope', 'value': 'libc++, libc++abi, libunwind, compiler-rt/libatomic link inputs; whole archives are not claimed copied into every ELF'},
                {'name': 'dora:retained-license-notices', 'value': 'assets/third_party/sqlcipher/LLVM-NOTICE.txt; assets/third_party/sqlcipher/Android-NDK-NOTICE.txt; includes applicable legacy notices'},
            ]
        components.append(item)
    for abi in sorted(ABIS):
        components.append({'type': 'file', 'bom-ref': 'native-binary:' + abi,
                           'name': 'jni/' + abi + '/libsqlcipher.so',
                           'hashes': [{'alg': 'SHA-256', 'content': provenance['native'][abi]['sha256']}],
                           'properties': [{'name': 'dora:android-api-floor', 'value': '28'},
                                          {'name': 'dora:elf-load-alignment', 'value': '16384'}]})
    sources = [item['bom-ref'] for item in components if item['type'] == 'library']
    return {
        'bomFormat': 'CycloneDX', 'specVersion': '1.6', 'version': 1,
        'metadata': {'component': {'type': 'library', 'bom-ref': ref, 'group': 'com.monumentogram.dora.thirdparty',
                                   'name': 'sqlcipher-android', 'version': '4.17.0-dora.1', 'purl': ref,
                                   'hashes': [{'alg': 'SHA-256', 'content': provenance['aarSha256']}]},
                     'properties': [{'name': 'dora:inventory-scope', 'value': 'NATIVE_CONSTITUENTS_NOT_GRADLE_RELEASE_GRAPH'},
                                    {'name': 'dora:status', 'value': 'CANDIDATE_PENDING_RUNTIME_ADMISSION'}]},
        'components': components,
        'dependencies': [{'ref': ref, 'dependsOn': [c['bom-ref'] for c in components if c['type'] == 'file']}] +
                        [{'ref': c['bom-ref'], 'dependsOn': sources if c['type'] == 'file' else []} for c in components],
    }


def validate_sbom(bom, provenance):
    require(bom == native_sbom(provenance), 'Native SBOM source/hash/license/relationship mismatch')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--write-sbom', action='store_true')
    args = parser.parse_args()
    inputs = read_inputs()
    validate_bundle(*inputs)
    sbom_path = ROOT / 'android/vendor/sqlcipher/native-components.cdx.json'
    if args.write_sbom:
        sbom_path.write_text(json.dumps(native_sbom(inputs[2]), sort_keys=True, indent=2) + '\n', encoding='utf-8')
    validate_sbom(json.loads(sbom_path.read_text()), inputs[2])
    if args.apk:
        validate_apk(args.apk)
    print('PASS exact composite source/ABI/ELF/license inventory; runtime acceptance is separate')
