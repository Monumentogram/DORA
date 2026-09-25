"""Private physical-device adapter for the separate 5.4 executable."""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid
from alpha_asr_campaign import require,file_sha,save_new,thermal_status,memory_summary


class Device:
    def __init__(self,adb,artifacts,identity):
        self.adb=str(adb); self.artifacts={k:Path(v) for k,v in artifacts.items()}
        self.identity=identity; self.serial=None; self.owned=False; self.active=None
        self.preserve=False; self.started=None
        self.directory='/data/local/tmp/dora-asr-campaign-'+uuid.uuid4().hex
        self.case=self.directory+'/case'

    def command(self,args,timeout=30):
        p=subprocess.run([self.adb]+(['-s',self.serial] if self.serial else [])+args,
                         capture_output=True,timeout=timeout)
        require(p.returncode==0,'ADB_OPERATION_FAILED')
        return p.stdout

    def shell(self,s): return self.command(['shell','-T',s])

    def connect(self):
        rows=[s.split() for s in self.command(['devices']).decode().splitlines()[1:] if s.strip()]
        require(len(rows)==1 and len(rows[0])==2 and rows[0][1]=='device','ADB_DEVICE_UNAVAILABLE')
        self.serial=rows[0][0]
        p={k:self.shell('getprop '+k).decode().strip() for k in ('ro.product.model','ro.product.marketname',
            'ro.product.device','ro.kernel.qemu','ro.build.version.sdk','ro.build.version.release',
            'ro.product.cpu.abilist','ro.build.type')}
        require(p['ro.product.model']=='22071219CG' and p['ro.product.marketname']=='POCO M5'
                and p['ro.product.device']=='stone' and p['ro.kernel.qemu']!='1','BLOCKED_WRONG_CAMPAIGN_DEVICE')
        require('arm64-v8a' in p['ro.product.cpu.abilist'].split(','),'ABI_UNAVAILABLE')
        return {'properties':p,'pageSize':int(self.shell('getconf PAGESIZE')),
            'memory':self.shell('cat /proc/meminfo').decode(),
            'storage':self.shell('df -k /data/local/tmp').decode(),
            'battery':self.shell('dumpsys battery').decode(),
            'thermalStatus':thermal_status(self.shell('dumpsys thermalservice')),
            'brightness':self.shell('settings get system screen_brightness').decode().strip(),
            'airplaneMode':self.shell('settings get global airplane_mode_on').decode().strip(),
            'wifiSetting':self.shell('settings get global wifi_on').decode().strip(),
            'mobileDataSetting':self.shell('settings get global mobile_data').decode().strip()}

    def prepare(self):
        self.shell('umask 077; mkdir '+self.directory); self.owned=True
        require(re.fullmatch('[0-9a-f]{64}',self.identity),'INVALID_OWNER_IDENTITY')
        self.shell("printf '%s' "+self.identity+' > '+self.directory+'/owner')
        for name,path in self.artifacts.items():
            require(re.fullmatch('[A-Za-z0-9_.+-]+',name),'INVALID_ARTIFACT_NAME')
            self.command(['push',str(path),self.directory+'/'+name])
        self.shell('chmod 700 '+self.directory+'/dora-asr-campaign')
        self.verify_remote()

    def verify_remote(self):
        for name,path in self.artifacts.items():
            parts=self.shell('sha256sum '+self.directory+'/'+name).decode().split()
            require(len(parts)==2 and parts[0]==file_sha(path),'DEVICE_ARTIFACT_HASH_MISMATCH')

    def profile(self,locale):
        require(locale in ('ru','en'),'INVALID_LOCALE')
        return json.loads(self.shell('env LD_LIBRARY_PATH='+self.directory+' '+self.directory+'/dora-asr-campaign profile '+locale))

    def _state(self):
        # An unreadable existing process is not evidence of its absence.
        script=('if test ! -e '+self.case+'/pid; then echo PENDING; exit 0; fi; '
            'p=$(cat '+self.case+'/pid) || exit 31; '
            "case $p in ''|0|*[!0-9]*) exit 31;; esac; "
            'if test ! -d /proc/$p; then echo GONE; exit 0; fi; '
            "c=$(tr '\\000' ' ' < /proc/$p/cmdline) || { test ! -d /proc/$p && { echo GONE; exit 0; }; exit 32; }; "
            "case $c in '"+self.directory+"/dora-asr-campaign '*) echo LIVE;; '') echo GONE;; *) exit 33;; esac")
        state=self.shell(script).decode().strip()
        require(state in ('PENDING','LIVE','GONE'),'DEVICE_PROCESS_IDENTITY_UNVERIFIED')
        return state

    def stop(self):
        if self.active is None: return
        state=self._state()
        if state=='PENDING':
            raise ValueError('DEVICE_START_UNVERIFIED')
        elif state=='LIVE':
            # No PID-based signalling: wait for our native process's 605 s alarm.
            # This avoids killing an unrelated process after PID reuse.
            deadline=(self.started or time.monotonic())+615
            while self._state()!='GONE' and time.monotonic()<deadline: time.sleep(.1)
            require(self._state()=='GONE','DEVICE_TERMINATION_UNVERIFIED')
        if self.active.poll() is None: self.active.terminate()
        self.active.wait(timeout=10); self.active=None

    def salvage(self,destination):
        failures=[]
        for name in ('metrics.json','memory.csv','hypothesis.txt','pid'):
            try:
                exists=self.shell('if test -f '+self.case+'/'+name+'; then echo YES; else echo NO; fi').strip()
                if exists==b'NO': continue
                require(exists==b'YES','RESULT_EXISTENCE_UNVERIFIED')
                expected=self.shell('sha256sum '+self.case+'/'+name).decode().split()[0]
                local=Path(destination)/name
                if not local.exists():
                    partial=local.with_suffix(local.suffix+'.partial')
                    self.command(['pull',self.case+'/'+name,str(partial)])
                    require(file_sha(partial)==expected,'RESULT_TRANSFER_MISMATCH')
                    partial.rename(local)
                require(file_sha(local)==expected,'RESULT_TRANSFER_MISMATCH')
            except Exception: failures.append(name)
        self.preserve=bool(failures)
        require(not failures,'PRIVATE_EVIDENCE_TRANSFER_UNVERIFIED')

    def clear_case(self):
        self.stop()
        require(self.shell('cat '+self.directory+'/owner').decode()==self.identity,'CLEANUP_OWNERSHIP_MISMATCH')
        self.shell('rm -rf '+self.case+'; test ! -e '+self.case)

    def execute(self,audio,locale,destination,mode='infer'):
        require(locale in ('ru','en') and mode in ('infer','decode'),'INVALID_EXECUTION')
        destination=Path(destination); destination.mkdir(parents=True,exist_ok=False)
        require(self.active is None,'OVERLAPPING_ATTEMPT')
        self.verify_remote()
        self.shell('umask 077; mkdir '+self.case+' && ln -s ../model.bin '+self.case+'/model.bin')
        self.command(['push',str(audio),self.case+'/clip.audio'])
        require(self.shell('sha256sum '+self.case+'/clip.audio').decode().split()[0]==file_sha(audio),'AUDIO_TRANSFER_MISMATCH')
        observations=[]; started=time.monotonic(); timeout=False
        telemetry=(destination/'thermal.jsonl').open('xb')
        def observe():
            raw=self.shell('dumpsys thermalservice'); stamp=time.monotonic()
            status=thermal_status(raw)
            row={'monotonicSeconds':stamp,'status':status}
            telemetry.write(json.dumps(row).encode()+b'\n'); telemetry.flush(); os.fsync(telemetry.fileno())
            observations.append(row)
            return stamp
        try:
            observe()
            save_new(destination/'power-before.json',{'battery':self.shell('dumpsys battery').decode()})
            script='cd '+self.case+' && exec env LD_LIBRARY_PATH='+self.directory+' '+self.directory+'/dora-asr-campaign '+mode+' '+locale
            with (destination/'native.stdout').open('xb') as out, (destination/'native.stderr').open('xb') as err:
                self.active=subprocess.Popen([self.adb,'-s',self.serial,'shell','-T',script],stdout=out,stderr=err)
                started=time.monotonic(); self.started=started; due=started
                while self.active.poll() is None:
                    if time.monotonic()-started>=600:
                        timeout=True; self.stop(); break
                    if time.monotonic()>=due:
                        observe(); due=time.monotonic()+1
                    time.sleep(.05)
                rc=None if timeout else self.active.returncode
            observe()
            save_new(destination/'power-after.json',{'battery':self.shell('dumpsys battery').decode()})
            self.stop()
            self.salvage(destination)
            require(not timeout,'RUNNER_TIMEOUT')
            require(rc==0,'NATIVE_EXECUTION_FAILED')
            require((destination/'native.stdout').stat().st_size==0,'UNEXPECTED_NATIVE_OUTPUT')
            metrics=json.loads((destination/'metrics.json').read_bytes())
            require(metrics['nativeCode']==0 and metrics['decodedFrames']>0,'NATIVE_RESULT_INVALID')
            memory=memory_summary((destination/'memory.csv').read_bytes())
            samples=[list(map(int,line.split(b','))) for line in (destination/'memory.csv').read_bytes().splitlines()]
            require(samples[0][0]<=metrics['measurementStartMicros']+1000000
                and samples[-1][0]>=metrics['measurementEndMicros']-1000000,'MEMORY_COVERAGE_INCOMPLETE')
            gaps=[b['monotonicSeconds']-a['monotonicSeconds'] for a,b in zip(observations,observations[1:])]
            require(len(observations)>=2 and max(gaps,default=0)<=3,'THERMAL_SAMPLING_GAP')
            result={'metrics':metrics,'memory':memory,'thermal':{'initial':observations[0]['status'],
                'maximum':max(r['status'] for r in observations),'final':observations[-1]['status'],
                'samples':len(observations),'maximumSampleGapSeconds':max(gaps,default=0)}}
            save_new(destination/'observation.json',result)
            return result
        finally:
            telemetry.close()
            self.stop()
            self.salvage(destination)
            self.clear_case()
            save_new(destination/'cleanup.json',{'status':'VERIFIED'})

    def cleanup(self):
        if not self.owned: return 'NOT_NEEDED'
        require(not self.preserve,'PRIVATE_EVIDENCE_PRESERVED_ON_DEVICE')
        self.stop()
        require(re.fullmatch('/data/local/tmp/dora-asr-campaign-[0-9a-f]{32}',self.directory),'CLEANUP_SCOPE_INVALID')
        require(self.shell('cat '+self.directory+'/owner').decode()==self.identity,'CLEANUP_OWNERSHIP_MISMATCH')
        self.shell('rm -rf '+self.directory+'; test ! -e '+self.directory)
        self.owned=False
        return 'VERIFIED'
