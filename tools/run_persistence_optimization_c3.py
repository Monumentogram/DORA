"""Explicit opt-in C3 emulator runner. Never discovers devices or changes credentials."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

from persistence_optimization_c3_results import parse, require

PACKAGE = 'com.monumentogram.dora.audio.test'
TEST = 'com.monumentogram.dora.audio.diagnostics.PersistenceOptimizationBenchmarkTest'


def validate_serial(serial):
    require(isinstance(serial,str) and re.fullmatch(r'emulator-[0-9]+',serial) is not None)


def verify_package(badging):
    packages = re.findall(r"^package: name='([^']+)'",badging,re.MULTILINE)
    require(packages == [PACKAGE])


def verify_emulator(command,expected_api):
    require(expected_api in (28,36))
    require(command('shell','getprop','ro.kernel.qemu').strip() == '1')
    require(command('shell','getprop','ro.hardware').strip() in ('ranchu','goldfish'))
    require(command('shell','getprop','ro.build.version.sdk').strip() == str(expected_api))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',required=True)
    parser.add_argument('--aapt',required=True)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--expected-api',type=int,choices=(28,36),required=True)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--apk-sha256',required=True)
    parser.add_argument('--source-sha256',required=True)
    parser.add_argument('--variant',choices=('baseline','optimized'),required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--max-blocks',type=int,choices=(10,400,1000,2000),default=2000)
    parser.add_argument('--timeout-seconds',type=int,default=14400)
    args = parser.parse_args()
    validate_serial(args.serial)
    require(60 <= args.timeout_seconds <= 21600)
    require(all(re.fullmatch('[0-9a-f]{64}',v) for v in (args.apk_sha256,args.source_sha256)))
    require(not args.output_dir.exists())
    apk_hash = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    require(apk_hash == args.apk_sha256)
    badging = subprocess.run([args.aapt,'dump','badging',str(args.apk.resolve())],check=True,
                            capture_output=True,text=True,timeout=60).stdout
    verify_package(badging)
    prefix = [args.adb,'-s',args.serial]
    def command(*parts):
        return subprocess.run([*prefix,*parts],check=True,capture_output=True,text=True,timeout=120).stdout.strip()
    verify_emulator(command,args.expected_api)
    args.output_dir.mkdir(parents=True,exist_ok=False)
    require('Success' in command('install','-r',str(args.apk.resolve())))
    command_line = [*prefix,'shell','am','instrument','-w','-r','-e','class',TEST,
                    '-e','persistenceOptimizationC3','true','-e','maxBlocks',str(args.max_blocks),
                    '-e','variant',args.variant,'-e','sourceSha256',args.source_sha256,
                    PACKAGE+'/androidx.test.runner.AndroidJUnitRunner']
    raw_path = args.output_dir/'instrumentation.private.log'
    begin = time.monotonic()
    outcome = 'EXITED'
    try:
        with raw_path.open('xb') as stream:
            execution = subprocess.run(command_line,stdout=stream,stderr=subprocess.STDOUT,
                                       timeout=args.timeout_seconds,check=False)
        if execution.returncode != 0: outcome = 'ADB_FAILED'
    except (subprocess.TimeoutExpired,KeyboardInterrupt):
        outcome = 'TIMED_OUT_OR_INTERRUPTED'
    except OSError:
        outcome = 'TRANSPORT_UNAVAILABLE'
    stop_status = 'NOT_REQUESTED'
    if outcome != 'EXITED':
        try:
            command('shell','am','force-stop',PACKAGE)
            stop_status = 'COMMAND_SUCCEEDED'
        except (subprocess.SubprocessError,OSError,KeyboardInterrupt):
            stop_status = 'UNCONFIRMED'
    raw = raw_path.read_bytes()
    receipt = parse(raw.decode('utf-8',errors='replace'),args.expected_api,args.variant,args.source_sha256)
    receipt.update(apk_sha256=apk_hash,input_sha256=hashlib.sha256(raw).hexdigest(),
                   elapsed_seconds=time.monotonic()-begin,host_process_outcome=outcome,
                   test_package_stop=stop_status)
    if outcome != 'EXITED': receipt['status'] = 'FAILED'
    with (args.output_dir/'receipt.json').open('x',encoding='utf-8') as stream:
        json.dump(receipt,stream,indent=2,allow_nan=False)
        stream.write('\n')
    print(receipt['status'])
    return 0 if receipt['status'] == 'COMPLETE' else 1


if __name__ == '__main__':
    raise SystemExit(main())
