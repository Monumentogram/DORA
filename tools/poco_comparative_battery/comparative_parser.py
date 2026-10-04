"""POCO checkin9/report36 subset; explicit estimates and missing-data semantics."""
import csv,re
from decimal import Decimal,InvalidOperation

def baseline_covers_slice(receipt, rows):
    summary=summarize_slice(rows)
    samples=[r for r in rows if r.get('phase')=='MEASURING']
    required={'mode':'MINIMAL_AUDIO_RECORD_FGS_BASELINE','outcome':'STOPPED','readErrors':0,'sampleRate':16000,'channels':1,'encoding':'PCM_S16LE','routeType':15,'audioSaved':False}
    if any(receipt.get(k)!=v for k,v in required.items()):return False
    start=receipt.get('startElapsedMs');end=receipt.get('endElapsedMs');frames=receipt.get('framesReadAndDiscarded')
    if any(type(v) is not int for v in (start,end,frames)) or start<=0 or frames<=0:return False
    return bool(summary['windowIdentityConsistent'] and summary['terminalConsistent'] and summary['probeId'].startswith('B-') and summary['tenMinuteSampleSpan'] and summary['allSamplesUnplugged'] and summary['allSamplesScreenOff'] and start<=samples[0]['elapsedRealtimeMs'] and end>=samples[-1].get('snapshotEndMs',samples[-1]['elapsedRealtimeMs']))

def nonnegative(raw):
    try: value=Decimal(raw)
    except InvalidOperation as exc: raise ValueError('Invalid numeric field') from exc
    if not value.is_finite() or value<0: raise ValueError('Invalid numeric field')
    return value

def parse_checkin(text, uid):
    if type(uid) is not int or uid<0:raise ValueError('Explicit target UID required')
    rows=list(csv.reader(text.splitlines()))
    headers=[r for r in rows if len(r)>4 and r[1:4]==['0','i','vers']]
    if len(headers)!=1 or headers[0][0]!='9' or headers[0][4]!='36':raise ValueError('Unvalidated checkin schema')
    result={'schema':'checkin9/report36','uid':uid,'estimatedUidChargeMah':None,'cpuMs':None,'semantics':'MODEL_ESTIMATED_UID_CHARGE_NOT_MEASURED_ENERGY','systemBatteryTimeMs':None,'systemEstimatedConsumedMah':None}
    seen=set()
    for r in rows:
        if len(r)<4 or r[2]!='l' or r[1] not in ('0',str(uid)):continue
        tag=r[3];values=r[4:]
        supported=(r[1]==str(uid) and tag in ('cpu','pwi')) or (r[1]=='0' and tag in ('bt','pws'))
        if not supported:continue
        if r[0]!='9':raise ValueError('Mixed checkin versions')
        if tag=='pwi' and (not values or values[0]!='uid'):continue
        key=(r[1],tag)
        if key in seen:raise ValueError('Ambiguous duplicate statistic')
        seen.add(key)
        if tag=='pwi':
            if len(values)!=5:raise ValueError('Unvalidated pwi layout')
            result['estimatedUidChargeMah']=str(nonnegative(values[1]))
        elif tag=='cpu':
            if len(values)!=3 or any(not v.isdecimal() for v in values):raise ValueError('Unvalidated cpu layout')
            result['cpuMs']={'user':int(values[0]),'system':int(values[1])}
        elif tag=='bt':
            if len(values)!=12 or any(not v.isdecimal() for v in values):raise ValueError('Unvalidated bt layout')
            result['systemBatteryTimeMs']={'batteryRealtime':int(values[1]),'batteryUptime':int(values[2]),'totalRealtime':int(values[3]),'startEpochMs':int(values[5]),'batteryScreenOffRealtime':int(values[6])}
        elif tag=='pws':
            if len(values)!=4:raise ValueError('Unvalidated pws layout')
            for value in values:nonnegative(value)
            result['systemEstimatedConsumedMah']=str(nonnegative(values[1]))
    return result

def summarize_slice(rows):
    samples=[r for r in rows if r.get('phase')=='MEASURING']
    if len(samples)<2:raise ValueError('At least two physical samples required')
    times=[r['elapsedRealtimeMs'] for r in samples]
    if any(type(t) is not int for t in times) or any(b<=a for a,b in zip(times,times[1:])):raise ValueError('Nonmonotonic samples')
    span=times[-1]-times[0]
    probe=samples[0].get('probeId')
    identity=bool(isinstance(probe,str) and re.fullmatch(r'[ABC]-[0-9]{2}',probe)) and all(r.get('probeId')==probe and r.get('windowStartMs')==times[0] for r in samples)
    ends=[r for r in rows if r.get('phase')=='END']
    terminal=len(ends)==1 and rows[-1] is ends[0]
    if terminal:
        e=ends[0]
        terminal=e.get('probeId')==probe and e.get('actualSampleSpanMs')==span and e.get('samples')==len(samples) and e.get('screenOffUnpluggedObserved') is True and e.get('tenMinuteWindowComplete') is True
    powerKeys=('AC powered','USB powered','Wireless powered','Dock powered')
    unplugged=all(all(r.get(k) is False for k in powerKeys) for r in samples)
    screenOff=all(r.get('interactive') is False for r in samples)
    charge=[r.get('chargeCounterMicroAh') for r in samples]
    supported=all(r.get('chargeCounterMicroAhSupported') is True for r in samples)
    sane=supported and all(type(v) is int and v>=0 for v in charge)
    monotonic=sane and all(b<=a for a,b in zip(charge,charge[1:]))
    delta=charge[0]-charge[-1] if monotonic else None
    candidate=delta if identity and terminal and unplugged and screenOff and span>=600000 and delta is not None and delta>0 else None
    return {'probeId':probe if identity else None,'windowIdentityConsistent':identity,'terminalConsistent':terminal,'sampleCount':len(samples),'actualSampleSpanMs':span,'tenMinuteSampleSpan':span>=600000,'maxObservedGapMs':max(b-a for a,b in zip(times,times[1:])),'allSamplesUnplugged':unplugged,'allSamplesScreenOff':screenOff,'chargeCounterMonotonic':monotonic,'observedChargeDeltaMicroAh':delta,'candidateChargeDeltaMicroAh':candidate,'continuousPowerProof':False,'acceptance':'NOT_EVALUATED','limitation':'Periodic samples alone cannot exclude between-sample charging or screen wake; inspect system history. No energy conversion or current integration.'}
