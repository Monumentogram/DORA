"""Pure dev-partition selector. Inputs/outputs are private; no I/O or inference."""
from collections import Counter
import hashlib,json,re

def canonical(v):return json.dumps(v,sort_keys=True,separators=(',',':'),ensure_ascii=False,allow_nan=False).encode()
def digest(v):return hashlib.sha256(canonical(v)).hexdigest()
def source_key(r):return r['datasetId'],r['release'],r['locale'],r['upstreamRelativePath']
def rank(r):return hashlib.sha256(b'dora-alpha-asr-v0.1\0'+r['locale'].encode()+b'\0'+r['upstreamRelativePath'].encode()).digest(),r['upstreamRelativePath'].encode()
def select(rows,prior_sources,prior_audio,prior_participants):
    remaining=[];excluded=[]
    if len({source_key(r) for r in rows})!=len(rows):raise ValueError('DUPLICATE_SOURCE')
    for r in rows:
        reasons=[]
        if not (r['locale'] in ('ru','en') and r['sourceSplit']=='dev' and type(r['durationMs']) is int
                and 1000<=r['durationMs']<=20000 and r['decodeResult']=='VALIDATED' and r['referenceTextPresent'] is True
                and re.fullmatch('[0-9a-f]{64}',r['participantSha256'])):reasons.append('INELIGIBLE')
        if source_key(r) in prior_sources:reasons.append('PRIOR_SOURCE')
        if r['audioSha256'] in prior_audio:reasons.append('PRIOR_AUDIO')
        if r['participantSha256'] in prior_participants:reasons.append('PRIOR_PARTICIPANT')
        if reasons:excluded.append({'source':source_key(r),'audioSha256':r['audioSha256'],'reasons':reasons})
        else:remaining.append(r)
    frequency=Counter(r['audioSha256'] for r in remaining);pool=[];duplicates=0
    for r in remaining:
        if frequency[r['audioSha256']]>1:
            duplicates+=1;excluded.append({'source':source_key(r),'audioSha256':r['audioSha256'],'reasons':['DUPLICATE_AUDIO']})
        else:pool.append(r)
    pool.sort(key=lambda r:(('ru','en').index(r['locale']),rank(r)))
    counts={l:sum(r['locale']==l for r in pool) for l in ('ru','en')}
    if min(counts.values())<24:raise ValueError('INSUFFICIENT_FRESH_HOLDOUT')
    chosen=[r for l in ('ru','en') for r in [x for x in pool if x['locale']==l][:24]]
    audit={'eligibleCounts':counts,'eligiblePoolSha256':digest(pool),'eligiblePool':pool,
           'exclusionSetSha256':digest(sorted(excluded,key=canonical)),'exclusions':excluded,'duplicateRows':duplicates,
           'priorSourcesSha256':digest(sorted(prior_sources)),'priorAudioSha256':digest(sorted(prior_audio)),
           'priorParticipantsSha256':digest(sorted(prior_participants))}
    return chosen,audit
