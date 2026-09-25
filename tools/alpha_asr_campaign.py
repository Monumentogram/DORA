"""Stage-0 5.4 primitives. Private inputs/results; no production integration.

The 5.3A contract and Java oracle are used unchanged. No corpus is opened on import.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import struct
import subprocess
import alpha_asr_eval_text_contract as frozen

VERSION = 'dora-alpha-asr-campaign-v0.1'


def require(ok, code):
    if not ok: raise ValueError(code)


def canonical(value):
    return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False).encode('utf-8')


def digest(value): return hashlib.sha256(canonical(value)).hexdigest()


def file_sha(path):
    with Path(path).open('rb') as f: return hashlib.file_digest(f,'sha256').hexdigest()


def file_pins(paths): return {str(Path(p).resolve()):file_sha(p) for p in paths}


def verify_pins(pins):
    require(all(file_sha(p)==v for p,v in pins.items()),'FROZEN_IMPLEMENTATION_CHANGED')


def save_new(path,value):
    with Path(path).open('xb') as f:
        f.write(canonical(value)+b'\n'); f.flush(); os.fsync(f.fileno())


def thermal_status(raw):
    found=re.findall(rb'^Thermal Status: ([0-6])\r?$',raw,re.M)
    require(len(found)==1,'THERMAL_INSTRUMENTATION_UNAVAILABLE')
    return int(found[0])


def memory_summary(raw):
    try: rows=[tuple(map(int,line.split(b','))) for line in raw.splitlines()]
    except ValueError: raise ValueError('MEMORY_INSTRUMENTATION_UNAVAILABLE') from None
    require(len(rows)>=2 and all(len(r)==3 and all(v>0 for v in r) for r in rows),
            'MEMORY_INSTRUMENTATION_UNAVAILABLE')
    require(all(a[0]<b[0] for a,b in zip(rows,rows[1:])),'MEMORY_CLOCK_INVALID')
    require(all(b[0]-a[0]<=1000000 for a,b in zip(rows,rows[1:])),'MEMORY_SAMPLING_GAP')
    return {'peakPssBytes':max(r[1] for r in rows),'peakNativeHeapBytes':max(r[2] for r in rows),
            'samples':len(rows)}


class Journal:
    """Append-only attempt identities with transactional single finalization.

    Caller owns an exclusive campaign directory/process lock. Interrupted RUNNING
    records remain untouched and block later starts, never trigger an automatic retry.
    """
    def __init__(self,path):
        self.db=sqlite3.connect(path,isolation_level=None)
        self.db.execute('PRAGMA synchronous=FULL')
        self.db.execute('CREATE TABLE IF NOT EXISTS attempts (id INTEGER PRIMARY KEY, case_id TEXT NOT NULL, locale TEXT NOT NULL, ordinal INTEGER NOT NULL, request TEXT NOT NULL, state TEXT NOT NULL, result TEXT, UNIQUE(case_id,ordinal))')

    def begin(self,attempt_id,case_id,locale,ordinal,request):
        self.db.execute('BEGIN IMMEDIATE')
        try:
            require(not self.db.execute("SELECT 1 FROM attempts WHERE state='RUNNING'").fetchone(),'INTERRUPTED_ATTEMPT')
            prev=self.db.execute('SELECT ordinal,result,request FROM attempts WHERE case_id=? ORDER BY ordinal',(case_id,)).fetchall()
            require(ordinal==len(prev)+1 and ordinal<=2,'ATTEMPT_POLICY_VIOLATION')
            if prev:
                require(json.loads(prev[-1][1])['state']!='SUCCEEDED','QUALITY_RETRY_FORBIDDEN')
                require(prev[-1][2]==canonical(request).decode(),'RETRY_CONFIG_CHANGED')
            maximum=self.db.execute('SELECT COALESCE(MAX(id),0) FROM attempts').fetchone()[0]
            require(type(attempt_id) is int and attempt_id==maximum+1,'ATTEMPT_SEQUENCE_INVALID')
            self.db.execute('INSERT INTO attempts VALUES (?,?,?,?,?,?,NULL)',
                (attempt_id,case_id,locale,ordinal,canonical(request).decode(),'RUNNING'))
            self.db.execute('COMMIT')
        except Exception:
            self.db.execute('ROLLBACK'); raise

    def finish(self,attempt_id,result):
        require(result.get('state') in ('SUCCEEDED','FAILED','INVALID'),'INVALID_TERMINAL_STATE')
        cur=self.db.execute("UPDATE attempts SET state='FINISHED',result=? WHERE id=? AND state='RUNNING'",
                            (canonical(result).decode(),attempt_id))
        require(cur.rowcount==1,'ALREADY_FINALIZED')

    def records(self):
        return [{'id':r[0],'caseId':r[1],'locale':r[2],'ordinal':r[3],'request':json.loads(r[4]),
                 'state':r[5],'result':json.loads(r[6]) if r[6] else None}
                for r in self.db.execute('SELECT * FROM attempts ORDER BY id')]

    def close(self): self.db.close()


class Oracle:
    def __init__(self,directory): self.directory=Path(directory)

    def compile(self):
        repo=Path(__file__).resolve().parents[1]
        original=repo/'tools/asr_i1_synthetic_scoring_oracle/src/main/java/com/monumentogram/dora/stage0/asr/i1/AsrSyntheticScoringOracle.java'
        require(hashlib.sha256(original.read_bytes().replace(b'\r\n',b'\n')).hexdigest()==frozen.ORACLE_SHA256,'ORACLE_CHANGED')
        self.directory.mkdir(parents=True,exist_ok=True)
        bridge=repo/'tools/alpha_asr_campaign_native/CampaignOracle.java'
        p=subprocess.run(['javac','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-d',str(self.directory),str(original),str(bridge)],capture_output=True,timeout=60)
        require(p.returncode==0,'ORACLE_COMPILATION_FAILED')

    def score(self,prepared):
        def string(s):
            b=s.encode('utf-8'); return struct.pack('>I',len(b))+b
        payload=string(prepared['caseId'])+string(prepared['locale'].upper())
        for k in ('rawReferenceTokens','rawHypothesisTokens','normalizedReferenceTokens','normalizedHypothesisTokens'):
            arr=prepared[k]; payload+=struct.pack('>I',len(arr))+b''.join(string(s) for s in arr)
        p=subprocess.run(['java','-ea','-Xverify:all','-cp',str(self.directory),'CampaignOracle'],
                         input=payload,capture_output=True,timeout=60)
        require(p.returncode==0,'ORACLE_REJECTED')
        return json.loads(p.stdout)['overall']


def aggregate(selected,results):
    expected={r['sampleId']:r['locale'] for r in selected}
    require(len(selected)==len(expected)==48 and list(expected.values())==['ru']*24+['en']*24,'SELECTED_SET_INVALID')
    seen=set(); out={}
    for r in results:
        require(r['caseId'] in expected and r['caseId'] not in seen and r['locale']==expected[r['caseId']], 'RESULT_MEMBERSHIP_INVALID')
        seen.add(r['caseId'])
    for locale in ('ru','en'):
        valid=[r for r in results if r['locale']==locale and r['executionAttemptState']=='SUCCEEDED']
        item={'completedValidCases':len(valid),'gate':'NOT_EVALUABLE','reason':'INCOMPLETE_LANGUAGE_COVERAGE'}
        for stream in ('raw','normalized'):
            counts={k:sum(r['oracleCounts'][stream][k] for r in valid) for k in ('substitutions','deletions','insertions')}
            counts['referenceTokens']=sum(r[stream+'TokenCounts']['reference'] for r in valid)
            counts['errors']=sum(counts[k] for k in ('substitutions','deletions','insertions'))
            counts['wer']=counts['errors']/counts['referenceTokens'] if counts['referenceTokens'] else None
            item[stream]=counts
        if len(valid)==24:
            item['gate']=frozen.language_gate(locale,item['normalized']['errors'],item['normalized']['referenceTokens'])
            item['reason']='COMPLETE_LANGUAGE_COVERAGE'
        out[locale]=item
    return out
