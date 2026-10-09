"""Bounded exact four-class diagnostic inventory on a disposable emulator only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess

import persistence_instrumentation_diagnostics as diagnostics
import run_encrypted_persistence_device as persistence
import poco_persistence_optimization_admission as admission

PACKAGE='com.monumentogram.dora.audio.test'
PREFIX='com.monumentogram.dora.audio.diagnostic.'
CLASSES={PREFIX+name for name in ('DiagnosticKeyFenceTest','DiagnosticPolicyLoaderTest',
                                'DiagnosticSuccessorLoaderTest','ProtectedHistoricalVaultTest')}
RUNNER=PACKAGE+'/androidx.test.runner.AndroidJUnitRunner'


def require(condition):
    if not condition: raise ValueError('PROTECTED_DIAGNOSTIC_REJECTED')


def validate_inventory(value):
    require(type(value) is dict and set(value)=={'schema_version','tests'} and
            type(value['schema_version']) is int and value['schema_version']==1)
    names=value['tests']
    require(type(names) is list and all(type(name) is str and re.fullmatch(r'[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+',name) for name in names))
    require(len(names)==len(set(names)) and {name.split('#')[0] for name in names}==CLASSES)
    return set(names)


def credential_phase(command,action,pin):
    """Cleanup is attempted even when setup is interrupted after applying the PIN."""
    try:
        require('Pin set to' in command('shell','locksettings','set-pin',pin))
        command('shell','input','keyevent','82')
        return action()
    finally:
        try:
            require('Lock credential cleared' in command('shell','locksettings','clear','--old',pin))
        except BaseException:
            raise diagnostics.InstrumentationFailure('CREDENTIAL_CLEANUP_UNCONFIRMED') from None


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--expected-api',type=int,choices=(28,36),required=True)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--inventory',type=Path,required=True)
    parser.add_argument('--receipt',type=Path,required=True)
    args=parser.parse_args()
    require(re.fullmatch(r'emulator-\d+',args.serial) is not None)
    require(not args.receipt.exists() and not args.receipt.is_symlink())
    require(args.inventory.resolve()==(admission.ROOT/admission.DIAGNOSTIC_INVENTORY).resolve())
    contract=admission.load(admission.ROOT)
    raw=args.inventory.read_bytes()
    require(admission.digest(raw)==contract['files'][admission.DIAGNOSTIC_INVENTORY])
    expected=validate_inventory(json.loads(raw))
    sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(bool(sdk))
    adb=str(Path(sdk)/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb'))
    aapt=str(Path(sdk)/'build-tools'/'36.0.0'/('aapt.exe' if os.name=='nt' else 'aapt'))
    # Inspect the exact APK before any installation; no production package admitted.
    badging=subprocess.run([aapt,'dump','badging',str(args.apk.resolve())],capture_output=True,
                           text=True,timeout=60,check=True).stdout
    require(re.findall(r"^package: name='([^']+)'",badging,re.M)==[PACKAGE])
    def command(*parts):
        try:
            result=subprocess.run([adb,'-s',args.serial,*parts],capture_output=True,text=True,
                                  timeout=120,check=True)
            return result.stdout.replace('\r\n','\n').strip()
        except BaseException:
            raise diagnostics.InstrumentationFailure('ADB_COMMAND_FAILED_OR_INTERRUPTED') from None
    require(command('get-state')=='device' and command('shell','getprop','ro.kernel.qemu')=='1')
    require(command('shell','getprop','ro.hardware') in ('ranchu','goldfish'))
    require(command('shell','getprop','ro.build.version.sdk')==str(args.expected_api))
    progress=args.receipt.parent/(args.receipt.stem+'-diagnostics')
    progress.mkdir(parents=True,exist_ok=False)
    receipt=dict(schema_version=1,status='FAILED',api=args.expected_api,
                 apk_sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                 inventory_sha256=hashlib.sha256(raw).hexdigest(),physical_device=False,
                 stage_acceptance='NOT_CLAIMED',tests=[],synthetic_credential_cleanup='NOT_ATTEMPTED')
    def instrument(names,phase):
        try:
            output=diagnostics.stream([adb,'-s',args.serial,'shell','am','instrument','-w','-r',
                                       '-e','class',','.join(sorted(names)),RUNNER],names,
                                      progress/(phase+'.json'),hard_timeout=900 if phase=='protected' else 120)
            rows=persistence.parse_results(output)
            diagnostics.require_complete([r['name'] for r in rows],names)
            return rows
        except BaseException:
            command('shell','am','force-stop',PACKAGE)
            raise diagnostics.InstrumentationFailure('INSTRUMENTATION_FAILED_OR_INTERRUPTED') from None
    try:
        require(command('install','-r',str(args.apk.resolve())).endswith('Success'))
        require(command('shell','pm','clear',PACKAGE)=='Success')
        instrument({persistence.NO_CREDENTIAL},'initial-no-credential')
        receipt['synthetic_credential_cleanup']='UNCONFIRMED'
        receipt['tests']=credential_phase(command,lambda:instrument(expected,'protected'),
                                          str(secrets.randbelow(900000)+100000))
        receipt['synthetic_credential_cleanup']='CLEARED'
        instrument({persistence.NO_CREDENTIAL},'final-no-credential')
        receipt['synthetic_credential_cleanup']='VERIFIED'
        receipt['status']='PASS_COMPONENT_RUNTIME_ONLY'
    except BaseException:
        receipt['failure']='BOUNDED_DIAGNOSTIC_RUN_FAILED'
    finally:
        args.receipt.parent.mkdir(parents=True,exist_ok=True)
        with args.receipt.open('x',encoding='utf-8') as stream:
            json.dump(receipt,stream,indent=2,sort_keys=True)
            stream.write('\n')
    print(receipt['status'])
    return 0 if receipt['status']=='PASS_COMPONENT_RUNTIME_ONLY' else 1


if __name__=='__main__': raise SystemExit(main())
