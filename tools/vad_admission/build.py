"""Isolated Windows admission build; never modifies the DORA Android graph.

Use Python 3.11+, JDK17, SDK platform36/build-tools36.0.0, NDK28.2.13676358,
CMake3.22.1. Pass a pristine pinned checkout and an output directory outside Git.
Third-party FetchContent archives are checked by exact upstream SHA256 pins.
No model download, credential access, publishing, or device execution occurs.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import wave
import zipfile

SOURCE_SHA = '11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf'
MODEL_SHA = '1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3'
PROFILE = 'dora-sherpa-vad-arm64-v1'
ARTIFACT_SHA = '64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db'
ARTIFACT_BYTES = 23396212
AAR_ENTRIES = {'classes.jar','AndroidManifest.xml','proguard.txt','META-INF/LICENSES/NOTICE.txt',
               'jni/arm64-v8a/libsherpa-onnx-jni.so','jni/arm64-v8a/libonnxruntime.so'}
HERE = Path(__file__).resolve().parent
JAVA_FILES = ('Vad', 'VadModelConfig', 'SileroVadModelConfig', 'TenVadModelConfig',
              'SpeechSegment', 'VersionInfo', 'LibraryLoader', 'LibraryUtils')


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def identity(path):
    data = path.read_bytes()
    return {'bytes': len(data), 'sha256': sha(data)}


def zip_bytes(entries):
    """Stable timestamps/order/modes; ZIP_STORED avoids compressor-version drift."""
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_STORED) as archive:
        for name, data in sorted(entries.items()):
            require(not name.startswith('/') and '..' not in name.split('/'), 'Unsafe ZIP member')
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            item.create_system = 3
            item.external_attr = 0o100644 << 16
            archive.writestr(item, data)
    return output.getvalue()


def inspect_elf(data):
    require(len(data) >= 64, 'Truncated ELF header')
    require(data[:6] == b'\x7fELF\x02\x01', 'Expected ELF64 little-endian')
    require(struct.unpack_from('<H', data, 18)[0] == 183, 'Expected AArch64')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    require(size >= 56 and offset + size * count <= len(data), 'Truncated ELF program table')
    loads = []
    for index in range(count):
        kind, flags, file_offset, address, _, file_size, memory_size, alignment = struct.unpack_from('<IIQQQQQQ', data, offset + index * size)
        require(file_offset + file_size <= len(data), 'Malformed ELF segment')
        if kind == 1:
            require(alignment >= 16384 and file_offset % 16384 == address % 16384, 'Incompatible 16KiB LOAD')
            loads.append(dict(offset=file_offset, address=address, filesz=file_size, memsz=memory_size, alignment=alignment, flags=flags))
    require(loads, 'Missing ELF LOAD')
    return dict(architecture='AArch64', static16KiB='STATIC_16K_ALIGNMENT_COMPATIBLE', loads=loads)


def check_model(path):
    require(identity(path) == {'bytes': 2327524, 'sha256': MODEL_SHA}, 'Pinned Silero model mismatch; STOP')


def check_aar_entries(names):
    require(len(names)==len(AAR_ENTRIES) and set(names)==AAR_ENTRIES, 'Frozen AAR entry inventory mismatch')


def check_aar(path):
    require(identity(path)=={'bytes':ARTIFACT_BYTES,'sha256':ARTIFACT_SHA}, 'Frozen AAR identity mismatch; STOP')
    with zipfile.ZipFile(path) as archive:
        check_aar_entries(archive.namelist())


def check_fresh_output(output):
    require(not output.exists() or (output.is_dir() and not any(output.iterdir())), 'Fresh empty build output required; cached dependencies rejected')


def notice_entries(notices):
    require(notices.is_file() or (notices.is_dir() and list(notices.iterdir())), 'Explicit notice input required')
    entries = {}
    for notice in ([notices] if notices.is_file() else sorted(notices.iterdir())):
        require(not notice.is_symlink() and notice.is_file(), 'Invalid notice input')
        entries['META-INF/LICENSES/' + notice.name] = notice.read_bytes()
    return entries


def run(args, *, env=None, capture=False):
    result = subprocess.run([str(a) for a in args], check=True, env=env,
                            stdout=subprocess.PIPE if capture else None, text=capture)
    return result.stdout if capture else None


def git(source, *args):
    return run(['git', '-c', 'safe.directory=' + source.as_posix(), '-C', source, *args], capture=True).strip()


def build(args):
    source, output, sdk, jdk = [Path(getattr(args, key)).resolve() for key in ('source', 'output', 'sdk', 'jdk')]
    require(git(source, 'rev-parse', 'HEAD') == SOURCE_SHA, 'Wrong sherpa source revision')
    require(not git(source, 'status', '--porcelain', '--untracked-files=all'), 'Upstream must be pristine')
    require(not git(source, 'submodule', 'status'), 'Unexpected submodule state')
    require(not any((ancestor / '.git').exists() for ancestor in (output, *output.parents)), 'Output must be outside a Git worktree')
    check_fresh_output(output)
    output.mkdir(parents=True, exist_ok=True)
    ndk = sdk / 'ndk/28.2.13676358'
    cmake = sdk / 'cmake/3.22.1/bin/cmake.exe'
    llvm = ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
    env = dict(os.environ)
    for key in ('CFLAGS', 'CXXFLAGS', 'LDFLAGS', 'CC', 'CXX', 'CL', '_CL_', 'INCLUDE', 'LIB',
                'SHERPA_ONNXRUNTIME_LIB_DIR', 'SHERPA_ONNXRUNTIME_INCLUDE_DIR'):
        env.pop(key, None)
    env['SOURCE_DATE_EPOCH'] = '0'
    env['TZ'] = 'UTC'
    build_dir = output / 'build'
    command = [cmake, '-S', HERE, '-B', build_dir, '-G', 'Ninja',
               '-DCMAKE_MAKE_PROGRAM=' + (cmake.parent / 'ninja.exe').as_posix(),
               '-DCMAKE_TOOLCHAIN_FILE=' + (ndk / 'build/cmake/android.toolchain.cmake').as_posix(),
               '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-28', '-DANDROID_STL=c++_static',
               '-DCMAKE_BUILD_TYPE=Release', '-DSHERPA_SOURCE=' + source.as_posix()]
    run(command, env=env)
    run([cmake, '--build', build_dir, '--target', 'dora-vad-jni', '-j', '6'], env=env)
    require(not git(source, 'status', '--porcelain', '--untracked-files=all'), 'Build modified upstream source')
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    require(not list(classes.rglob('*.class')), 'Use fresh packaging output; stale classes rejected')
    java_root = source / 'sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx'
    run([jdk / 'bin/javac.exe', '--release', '8', '-g:none', '-encoding', 'UTF-8', '-d', classes,
         *[java_root / (name + '.java') for name in JAVA_FILES]])
    jar = zip_bytes({p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')})
    entries = {'classes.jar': jar,
               'AndroidManifest.xml': b'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.monumentogram.dora.thirdparty.vad"><uses-sdk android:minSdkVersion="28" /></manifest>',
               'proguard.txt': b'-keep class com.k2fsa.sherpa.onnx.** { *; }\n'}
    native = {}
    paths = {'libsherpa-onnx-jni.so': build_dir / 'candidate/libsherpa-onnx-jni.so',
             'libonnxruntime.so': build_dir / '_deps/onnxruntime-src/jni/arm64-v8a/libonnxruntime.so'}
    for name, path in paths.items():
        packed = output / name
        shutil.copyfile(path, packed)
        if name == 'libsherpa-onnx-jni.so':
            run([llvm / 'llvm-strip.exe', '--strip-unneeded', packed])
        data = packed.read_bytes()
        elf = inspect_elf(data)
        forbidden = (b'espeak', b'piper_phonemize', b'piper-phonemize', b'OfflineTts', b'Java_com_k2fsa_sherpa_onnx_OfflineRecognizer', b'Java_com_k2fsa_sherpa_onnx_KeywordSpotter')
        require(not any(marker.lower() in data.lower() for marker in forbidden), 'Forbidden runtime feature marker')
        dynamic = run([llvm / 'llvm-readelf.exe', '-d', packed], capture=True)
        needed = re.findall(r'Shared library: \[(.*?)\]', dynamic)
        require(set(needed) <= {'libandroid.so', 'liblog.so', 'libonnxruntime.so', 'libm.so', 'libdl.so', 'libc.so'}, 'Unexpected native dependency')
        native[name] = {**identity(packed), **elf, 'needed': needed}
        if name == 'libsherpa-onnx-jni.so':
            symbols = run([llvm / 'llvm-nm.exe', '--dynamic', '--defined-only', packed], capture=True)
            exports = [line.split()[-1] for line in symbols.splitlines() if line.strip()]
            require(len(exports) == 20 and all(re.fullmatch(r'Java_com_k2fsa_sherpa_onnx_(Vad|VersionInfo)_.*', name) for name in exports), 'Unexpected JNI exports')
            native[name]['exports'] = sorted(exports)
        entries['jni/arm64-v8a/' + name] = data
    entries.update(notice_entries(Path(args.notices)))
    artifact = output / 'sherpa-onnx-vad-1.13.8-dora.1-arm64.aar'
    require(not artifact.exists(), 'Frozen AAR must never be overwritten')
    artifact.write_bytes(zip_bytes(entries))
    check_aar(artifact)
    cache = (build_dir / 'CMakeCache.txt').read_text()
    flags = {key: value for key, value in re.findall(r'^([A-Z][A-Z0-9_]*):(?:BOOL|STRING)=(.*)$', cache, re.M)
             if key.startswith(('SHERPA_', 'KALDI', 'SBPE_', 'HAVE_', 'OPENFST_', 'BUILD_', 'ANDROID_'))}
    receipt = dict(profile=PROFILE, sourceSha=SOURCE_SHA, artifact={'filename':artifact.name, **identity(artifact)},
                   abi=['arm64-v8a'], native=native, flags=flags,
                   entries={name:{'bytes':len(data), 'sha256':sha(data)} for name,data in sorted(entries.items())},
                   javaSources={name + '.java':identity(java_root / (name + '.java')) for name in JAVA_FILES},
                   recipe={p.name:identity(p) for p in (HERE/'CMakeLists.txt', HERE/'build.py')})
    (output/'candidate-inventory.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    return artifact


def harness(args):
    output, sdk, jdk = [Path(getattr(args,key)).resolve() for key in ('output','sdk','jdk')]
    artifact, model, speech = map(Path, (args.aar,args.model,args.speech))
    check_aar(artifact)
    check_model(model)
    with wave.open(str(speech),'rb') as wav:
        require((wav.getframerate(),wav.getnchannels(),wav.getsampwidth()) == (16000,1,2), 'Fixture must be PCM S16LE mono16k')
        pcm = wav.readframes(wav.getnframes())
    require(pcm and len(pcm) <= 16000*2*60, 'Speech fixture must be bounded to60 seconds')
    output.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(artifact) as archive:
        jar=output/'classes.jar'; jar.write_bytes(archive.read('classes.jar'))
        libs={name.replace('jni/','lib/',1):archive.read(name) for name in archive.namelist() if name.endswith('.so')}
        notices={name:archive.read(name) for name in archive.namelist() if name.startswith('META-INF/LICENSES/')}
    model_license=Path(args.model_license).read_bytes()
    require(sha(model_license) == '2e63e9a38b6e8fc0c7bc37ce174caca1862870856c6daf5697cfb785e925520b', 'Model license mismatch')
    classes=output/'harness-classes'; classes.mkdir(exist_ok=True)
    android=sdk/'platforms/android-36/android.jar'
    run([jdk/'bin/javac.exe','-source','8','-target','8','-g:none','-encoding','UTF-8','-cp',str(android)+os.pathsep+str(jar),'-d',classes,HERE/'SmokeActivity.java'])
    harness_jar=output/'harness.jar'; harness_jar.write_bytes(zip_bytes({p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}))
    bt=sdk/'build-tools/36.0.0'
    run([jdk/'bin/java.exe','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android,'--output',output,jar,harness_jar])
    unsigned=output/'unsigned.apk'
    run([bt/'aapt2.exe','link','-I',android,'--manifest',HERE/'AndroidManifest.xml','-o',unsigned])
    with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_STORED) as apk:
        for name,data in {**libs,**notices,'META-INF/LICENSES/SILERO-MIT.txt':model_license,'classes.dex':(output/'classes.dex').read_bytes(),'assets/silero.onnx':model.read_bytes(),
                          'assets/speech.pcm':pcm,'assets/candidate.sha256':identity(artifact)['sha256'].encode()}.items():
            apk.writestr(name,data)
    run([bt/'zipalign.exe','-f','-P','16','4',unsigned,output/'aligned.apk'])
    key=output/'admission-only.p12'
    require(not key.exists(),'Fresh disposable signing key path required')
    run([jdk/'bin/keytool.exe','-genkeypair','-keystore',key,'-storetype','PKCS12','-storepass','admission-only',
         '-alias','admission','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Disposable VAD Admission'])
    run([jdk/'bin/java.exe','-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:admission-only',
         '--out',output/'admission.apk',output/'aligned.apk'])
    print(json.dumps({'apk':identity(output/'admission.apk'),'aar':identity(artifact),'model':identity(model),
                      'syntheticPcm':{'bytes':len(pcm),'sha256':sha(pcm)}}))


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=('build','harness'))
    for key in ('source','output','sdk','jdk','notices','aar','model','model-license','speech'):
        parser.add_argument('--'+key,required=key in ('output','sdk','jdk'))
    args=parser.parse_args()
    build(args) if args.mode=='build' else harness(args)
