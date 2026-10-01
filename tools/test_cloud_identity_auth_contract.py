"""Synthetic metadata only; never a token, content, network or upload fixture."""
import copy
import json
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name('validate_cloud_identity_auth_contract.py')
if MODULE.exists():
    import validate_cloud_identity_auth_contract as v
else:
    v = None


def uid(n):
    return f'00000000-0000-0000-0000-{n:012d}'


def fixture(operation='DISPATCH_ASR'):
    source = dict(recording_id=uid(1), audio_asset_id=uid(2), sha256='a'*64)
    route = dict(route_id=uid(3), provider_id=uid(4), profile_id=uid(5),
                 profile_version=1, destination_id=uid(6), region_id=uid(7))
    start, now, end = '2026-09-30T00:00:00Z', '2026-09-30T01:00:00Z', '2026-09-30T02:00:00Z'
    principal = dict(principal_id=uid(10), installation_ref=uid(11), kind='INSTALLATION',
                     invitation='VERIFIED', key_binding_ref=uid(12))
    credential = dict(credential_ref=uid(13), principal_id=uid(10), issuer='DORA_CONTROL_PLANE',
                      issued_at=start, expires_at=end, status='ACTIVE', generation=1,
                      audience='DORA_CLOUD_CONTROL_PLANE', operations=list(v.contract()['enums']['Operation']),
                      replaces_ref=None, verification='VERIFIED', proof_request_id=uid(14), replay_check='VERIFIED')
    owner = dict(binding_ref=uid(15), principal_id=uid(10), source=source, version=1,
                 effective_at=start, state='ACTIVE', provenance_ref=uid(16), verification='VERIFIED')
    scope = dict(scope_ref=uid(17), disclosure_version=1, purpose='CLOUD_ASR',
                 selection='EXACT_SNAPSHOT', sources=[source], route=route,
                 operations=list(v.contract()['enums']['Operation']))
    grant = dict(grant_ref=uid(18), principal_id=uid(10), scope=scope, granted_at=start,
                 revision=1, state='ACTIVE', informed='VERIFIED', basis='EXPLICIT_RECORDING', verification='VERIFIED')
    auth = dict(authorization_ref=uid(19), principal_id=uid(10), source=source,
                state='EXPLICITLY_APPROVED', grant_ref=uid(18), scope_ref=uid(17), revision=1,
                decision_at=start, prompt_count=1, prompted_at=start, batch_ref=None,
                validity='ACTIVE', verification='VERIFIED')
    job = dict(binding_ref=uid(20), principal_id=uid(10), source=source,
               processing_action_id=uid(21), processing_job_id=uid(22), configuration_ref=uid(23),
               route=route, creation_decision_ref=uid(24), created_at=start, version=1,
               state='ACTIVE', verification='VERIFIED')
    request = dict(request_id=uid(14), principal_id=uid(10), credential_ref=uid(13),
                   ownership_binding_ref=uid(15), recording_authorization_ref=uid(19), grant_ref=uid(18),
                   source=source, processing_action_id=uid(21), processing_job_id=uid(22),
                   configuration_ref=uid(23), route=route, operation=operation, now=now,
                   attempt_id=uid(25), prior_attempt_id=None, trigger='USER')
    snapshot = dict(principal=principal, credential=credential, ownership=owner, grant=grant,
                    recording_authorization=auth, policy=dict(policy='ASK_EACH_RECORDING', revision=1),
                    job=None if operation=='CREATE_CLOUD_JOB' else job, batch=None,
                    current_source=source, source_availability='AVAILABLE', deletion_epoch=0,
                    recording_lifecycle='LIVE', current_route=route, configuration_ref=uid(23),
                    operation_policy='VERIFIED', resolved_at=now, attempt_history=[])
    # Break aliases so a single mutation never silently changes other evidence.
    return json.loads(json.dumps(request)), json.loads(json.dumps(snapshot))


def decision(r, s, number=30):
    return dict(decision_id=uid(number), request=copy.deepcopy(r),
                credential_ref=r['credential_ref'], ownership_binding_ref=r['ownership_binding_ref'],
                consent_ref=r['grant_ref'], scope_ref=s['grant']['scope']['scope_ref'] if s['grant'] else None,
                recording_authorization_ref=r['recording_authorization_ref'], vector=v.generation_vector(s),
                evaluated_at=r['now'], outcome='ALLOW', reason='ALLOWED')


def authority_fixture():
    r, s = fixture('ISSUE_UPLOAD_AUTHORITY')
    issued = decision(r,s)
    issuance_snapshot=copy.deepcopy(s)
    a = dict(authority_ref=uid(31), principal_id=r['principal_id'], source=copy.deepcopy(r['source']),
             processing_action_id=r['processing_action_id'], processing_job_id=r['processing_job_id'],
             operations=['UPLOAD_AUDIO','RETRY_UPLOAD'], max_bytes=1000, issued_at=r['now'],
             expires_at='2026-09-30T01:30:00Z', grant_ref=r['grant_ref'], issue_decision_ref=uid(30),
             vector=v.generation_vector(s), validity='ACTIVE')
    r['operation']='UPLOAD_AUDIO'
    r['request_id']=uid(32)
    s['credential']['proof_request_id']=uid(32)
    current = decision(r,s,33)
    return a,issued,issuance_snapshot,current,s


def prior_attempt(r):
    prior={k:copy.deepcopy(r[k]) for k in ('source','processing_action_id','processing_job_id','configuration_ref','route')}
    prior.update(attempt_id=r['prior_attempt_id'],verification='VERIFIED')
    return prior


class IdentityContractTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(v, 'offline logical validator is missing')
        self.c=v.contract()
        self.r,self.s=fixture()

    def check(self, expected):
        self.assertEqual(v.check_case(self.r,self.s,self.c),expected)

    def test_valid_intersection(self): self.check('ALLOWED')
    def test_installation_without_credential(self):
        self.s['credential']=None
        self.check('NOT_AUTHENTICATED')
    def test_expired(self):
        self.s['credential']['expires_at']=self.r['now']
        self.check('CREDENTIAL_EXPIRED')
    def test_revoked(self):
        self.s['credential']['status']='REVOKED'
        self.check('CREDENTIAL_REVOKED')
    def test_superseded(self):
        self.s['credential']['status']='SUPERSEDED'
        self.check('CREDENTIAL_SUPERSEDED')
    def test_unknown_expiry(self):
        self.s['credential']['expires_at']=None
        self.check('NOT_AUTHENTICATED')
    def test_proof_cannot_replay(self):
        self.s['credential']['proof_request_id']=uid(90)
        self.check('NOT_AUTHENTICATED')
    def test_invitation_required(self):
        self.s['principal']['invitation']='UNKNOWN'
        self.check('NOT_AUTHENTICATED')
    def test_unverified_credential(self):
        self.s['credential']['verification']='UNVERIFIED'
        self.check('NOT_AUTHENTICATED')
    def test_different_credential_principal(self):
        self.s['credential']['principal_id']=uid(90)
        self.check('NOT_AUTHENTICATED')
    def test_cross_owner(self):
        self.s['ownership']['principal_id']=uid(90)
        self.check('OWNERSHIP_MISMATCH')
    def test_client_supplied_owner_reference(self):
        self.s['ownership']['verification']='UNVERIFIED'
        self.check('OWNERSHIP_MISMATCH')
    def test_wrong_source(self):
        self.r['source']['audio_asset_id']=uid(90)
        self.check('SOURCE_MISMATCH')
    def test_wrong_job(self):
        self.r['processing_job_id']=uid(90)
        self.check('JOB_MISMATCH')
    def test_wrong_action(self):
        self.r['processing_action_id']=uid(90)
        self.check('JOB_MISMATCH')
    def test_job_not_verified(self):
        self.s['job']['verification']='UNKNOWN'
        self.check('JOB_MISMATCH')
    def test_no_consent(self):
        self.s['grant']=None
        self.check('NO_CONSENT')
    def test_policy_label_never_grant(self):
        self.s['policy']['policy']='ALWAYS'
        self.s['grant']=None
        self.check('NO_CONSENT')
    def inherited(self):
        self.s['policy']['policy']='ALWAYS'
        self.s['recording_authorization']['state']='INHERITED_ALWAYS'
        self.s['grant']['basis']='INFORMED_ALWAYS'
        self.s['grant']['scope']['selection']='ELIGIBLE_FUTURE'
        self.s['grant']['scope']['sources']=[]
    def test_inherited_always(self):
        self.inherited(); self.check('ALLOWED')
    def test_leaving_always(self):
        self.inherited(); self.s['policy']['policy']='ASK_EACH_RECORDING'
        self.check('NO_CONSENT')
    def test_explicit_survives_policy_change(self):
        self.s['policy']['policy']='ALWAYS'; self.check('ALLOWED')
    def test_revoked_consent(self):
        self.s['grant']['state']='REVOKED'; self.check('CONSENT_REVOKED')
    def test_pending(self):
        self.s['recording_authorization']['state']='PENDING'; self.check('RECORDING_PENDING')
    def test_deferred(self):
        self.s['recording_authorization']['state']='DEFERRED'; self.check('RECORDING_DEFERRED')
    def test_manual_selected(self):
        self.s['recording_authorization']['state']='MANUAL_USER_ACTION'
        self.s['grant']['basis']='MANUAL_RECORDING'
        self.s['policy']['policy']='MANUAL_ONLY'; self.check('ALLOWED')
    def test_manual_no_autonomous_initiation(self):
        self.s['policy']['policy']='MANUAL_ONLY'; self.r['trigger']='AUTOMATIC'
        self.check('NO_CONSENT')
    def test_scope_missing_operation(self):
        self.s['grant']['scope']['operations'].remove('DISPATCH_ASR'); self.check('CONSENT_SCOPE_MISMATCH')
    def test_provider_expansion(self):
        self.s['grant']['scope']['route']['provider_id']=uid(90); self.check('PROVIDER_SCOPE_MISMATCH')
    def test_region_expansion(self):
        self.s['grant']['scope']['route']['region_id']=uid(90); self.check('PROVIDER_SCOPE_MISMATCH')
    def test_exact_snapshot_source(self):
        self.s['grant']['scope']['sources'][0]['audio_asset_id']=uid(90); self.check('CONSENT_SCOPE_MISMATCH')
    def test_retry_after_revocation(self):
        self.check('ALLOWED'); self.r['operation']='RETRY_ASR'; self.r['prior_attempt_id']=uid(90)
        self.s['attempt_history']=[prior_attempt(self.r)]
        self.s['grant']['state']='REVOKED'; self.check('CONSENT_REVOKED')
    def test_retry_needs_new_attempt(self):
        self.r['operation']='RETRY_ASR'; self.r['prior_attempt_id']=self.r['attempt_id']; self.check('STALE_AUTHORIZATION')
    def test_retry_new_attempt(self):
        self.r['operation']='RETRY_ASR'; self.r['prior_attempt_id']=uid(90)
        self.s['attempt_history']=[prior_attempt(self.r)]; self.check('ALLOWED')
    def test_reconnect_not_consent(self):
        self.r['trigger']='RESUME'; self.s['grant']=None; self.check('NO_CONSENT')
    def test_unknown_operation(self):
        self.r['operation']='SOMETHING'; self.check('INVALID_OPERATION')
    def test_missing_fact(self):
        del self.s['ownership']; self.check('UNKNOWN')
    def test_stale_snapshot(self):
        self.s['resolved_at']='2026-09-30T00:00:00Z'; self.check('STALE_AUTHORIZATION')
    def test_no_source_no_dispatch(self):
        self.s['source_availability']='DELETED'; self.check('SOURCE_UNAVAILABLE')
    def test_read_survives_audio_deletion(self):
        self.r['operation']='READ_RESULT'; self.s['source_availability']='DELETED'; self.check('ALLOWED')
    def test_cancel_after_consent_revocation(self):
        self.r['operation']='CANCEL_JOB'; self.s['grant']['state']='REVOKED'
        self.s['source_availability']='DELETED'; self.check('ALLOWED')
    def test_cancel_no_grant(self):
        self.r['operation']='CANCEL_JOB'; self.s['grant']=None; self.s['recording_authorization']=None
        self.check('ALLOWED')
    def test_cancel_cross_owner_denies(self):
        self.r['operation']='CANCEL_JOB'; self.s['ownership']['principal_id']=uid(90); self.check('OWNERSHIP_MISMATCH')
    def test_operation_policy_unknown(self):
        self.s['operation_policy']='UNKNOWN'; self.check('OPERATION_POLICY_DENIED')
    def test_decision_stale_after_revision(self):
        d=decision(self.r,self.s); v.validate_decision(d,self.s,self.c)
        self.s['grant']['revision']+=1
        with self.assertRaises(ValueError): v.validate_decision(d,self.s,self.c)
    def test_forged_allow(self):
        d=decision(self.r,self.s); self.s['grant']['state']='REVOKED'
        with self.assertRaises(ValueError): v.validate_decision(d,self.s,self.c)
    def test_contract_frozen(self): v.validate_contract(self.c)
    def test_negative_fields(self):
        for field in ['jwt','access_token','refresh_token','password','api_key','aws_access_key',
                      'aws_secret','iam_role','firebase_uid','google_account','cognito','provider_secret','signed_url']:
            with self.subTest(field=field):
                c=copy.deepcopy(self.c); c['types']['CredentialBinding'][field]='nonempty'
                with self.assertRaises(ValueError): v.validate_contract(c)
                r=copy.deepcopy(self.r); r[field]='synthetic-forbidden-field'
                self.assertEqual(v.check_case(r,self.s,self.c),'UNKNOWN')
    def test_local_requires_nothing(self):
        self.assertEqual(self.c['local_prerequisites'],[])
        c=copy.deepcopy(self.c); c['local_prerequisites']=['CloudPrincipal']
        with self.assertRaises(ValueError): v.validate_contract(c)
    def test_runtime_and_future_gates_locked(self):
        for field in self.c['non_execution']:
            c=copy.deepcopy(self.c); c['non_execution'][field]='STARTED'
            with self.assertRaises(ValueError): v.validate_contract(c)
    def batch(self):
        source=copy.deepcopy(self.r['source'])
        self.s['batch']=dict(batch_ref=uid(40),principal_id=uid(10),presented=[source],selected=[uid(1)],
                             presented_at='2026-09-30T00:00:00Z',decided_at='2026-09-30T00:00:00Z',
                             grant_ref=uid(18),scope_ref=uid(17),verification='VERIFIED',presentation='AUTOMATIC')
        self.s['recording_authorization']['batch_ref']=uid(40)
        self.s['grant']['basis']='BATCH_SELECTION'
    def test_selected_batch(self): self.batch(); self.check('ALLOWED')
    def test_batch_new_arrival(self):
        self.batch(); self.s['batch']['selected']=[uid(90)]; self.check('CONSENT_SCOPE_MISMATCH')
    def test_batch_unselected(self):
        self.batch(); self.s['batch']['selected']=[]; self.check('CONSENT_SCOPE_MISMATCH')
    def test_batch_changed_source(self):
        self.batch(); self.s['batch']['presented'][0]['audio_asset_id']=uid(90); self.check('CONSENT_SCOPE_MISMATCH')
    def test_user_batch_preserves_old_prompt(self):
        self.batch(); self.s['batch']['presentation']='USER'
        later='2026-09-30T00:30:00Z'
        self.s['batch'].update(presented_at=later,decided_at=later)
        self.s['grant']['granted_at']=later; self.s['recording_authorization']['decision_at']=later
        self.check('ALLOWED')
    def test_independent_conditions_matrix(self):
        for auth_ok in (False,True):
            for owner_ok in (False,True):
                for consent_ok in (False,True):
                    for scope_ok in (False,True):
                        r,s=fixture()
                        if not auth_ok: s['credential']=None
                        if not owner_ok: s['ownership']['principal_id']=uid(90)
                        if not consent_ok: s['grant']=None
                        if not scope_ok: r['route']['region_id']=uid(90)
                        result=v.check_case(r,s,self.c)
                        self.assertEqual(result=='ALLOWED',auth_ok and owner_ok and consent_ok and scope_ok)


class UploadTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(v,'offline logical validator is missing')
        self.c=v.contract(); self.a,self.issued,self.issuance_snapshot,self.current,self.s=authority_fixture()
    def validate(self):
        v.validate_upload_authority(self.a,self.issued,self.issuance_snapshot,self.current,self.s,400,500,self.c)
    def test_exact_authority(self): self.validate()
    def test_other_recording(self):
        self.a['source']['recording_id']=uid(90)
        with self.assertRaises(ValueError): self.validate()
    def test_other_source(self):
        self.a['source']['audio_asset_id']=uid(90)
        with self.assertRaises(ValueError): self.validate()
    def test_expired_authority(self):
        self.a['expires_at']=self.current['evaluated_at']
        with self.assertRaises(ValueError): self.validate()
    def test_without_allow(self):
        self.issued['outcome']='DENY'
        with self.assertRaises(ValueError): self.validate()
    def test_wrong_issue_operation(self):
        self.issued['request']['operation']='READ_RESULT'
        with self.assertRaises(ValueError): self.validate()
    def test_current_revocation(self):
        self.s['grant']['state']='REVOKED'
        with self.assertRaises(ValueError): self.validate()
    def test_current_generation_changed(self):
        self.s['deletion_epoch']+=1
        with self.assertRaises(ValueError): self.validate()
    def test_unknown_byte_ceiling(self):
        self.a['max_bytes']=None
        with self.assertRaises(ValueError): self.validate()
    def test_cumulative_ceiling(self):
        with self.assertRaises(ValueError):
            v.validate_upload_authority(self.a,self.issued,self.issuance_snapshot,self.current,self.s,600,500,self.c)
    def test_no_secret_fields(self):
        self.a['provider_secret']='synthetic-forbidden-field'
        with self.assertRaises(ValueError): self.validate()
    def test_issue_permission_cannot_be_forged(self):
        self.s['credential']['operations'].remove('ISSUE_UPLOAD_AUTHORITY')
        self.s['grant']['scope']['operations'].remove('ISSUE_UPLOAD_AUTHORITY')
        self.issuance_snapshot['credential']['operations'].remove('ISSUE_UPLOAD_AUTHORITY')
        self.issuance_snapshot['grant']['scope']['operations'].remove('ISSUE_UPLOAD_AUTHORITY')
        with self.assertRaises(ValueError): self.validate()
    def test_contradictory_issue_evidence(self):
        self.issued['credential_ref']=uid(90)
        with self.assertRaises(ValueError): self.validate()


class PromptTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(v,'offline logical validator is missing')
        r,_=fixture(); self.p=dict(source=r['source'],state='PENDING',prompt_count=0,prompted_at=None)
        self.c=v.contract()
    def test_prompt_once(self):
        after=copy.deepcopy(self.p); after.update(prompt_count=1,prompted_at='2026-09-30T00:00:00Z')
        v.validate_prompt_transition(self.p,after,'PRESENT',self.c)
        with self.assertRaises(ValueError): v.validate_prompt_transition(after,after,'PRESENT',self.c)
    def test_restart_toggle_reconnect_chunks_preserve(self):
        self.p.update(state='DEFERRED',prompt_count=1,prompted_at='2026-09-30T00:00:00Z')
        for event in ['RESTART','POLICY_TOGGLE','RECONNECT','CHUNK']:
            v.validate_prompt_transition(self.p,self.p,event,self.c)
            after=copy.deepcopy(self.p); after['prompt_count']=0; after['prompted_at']=None
            with self.assertRaises(ValueError): v.validate_prompt_transition(self.p,after,event,self.c)
    def test_deferred_never_automatic_prompt(self):
        self.p['state']='DEFERRED'
        after=copy.deepcopy(self.p); after.update(prompt_count=1,prompted_at='2026-09-30T00:00:00Z')
        with self.assertRaises(ValueError): v.validate_prompt_transition(self.p,after,'PRESENT',self.c)


class TransitionReviewTests(unittest.TestCase):
    def setUp(self): self.c=v.contract()
    def test_retry_cannot_change_whole_context(self):
        r,s=fixture('RETRY_ASR'); r['prior_attempt_id']=uid(90)
        # A different prior attempt ID alone proves neither its existence nor scope.
        self.assertNotEqual(v.check_case(r,s,self.c),'ALLOWED')
    def test_batch_complete_transition_checker_exists(self):
        self.assertTrue(hasattr(v,'validate_batch_transition'))
    def test_job_creation_transition_checker_exists(self):
        self.assertTrue(hasattr(v,'validate_job_binding'))
    def test_retry_changed_source_or_provider(self):
        for field in ('source','route','processing_action_id','processing_job_id','configuration_ref'):
            r,s=fixture('RETRY_ASR'); r['prior_attempt_id']=uid(90)
            old=prior_attempt(r)
            if field=='source': old[field]['audio_asset_id']=uid(99)
            elif field=='route': old[field]['provider_id']=uid(99)
            else: old[field]=uid(99)
            s['attempt_history']=[old]
            self.assertEqual(v.check_case(r,s,self.c),'STALE_AUTHORIZATION')
    def test_all_operation_positive_controls(self):
        for op in self.c['enums']['Operation']:
            r,s=fixture(op)
            if op.startswith('RETRY_'):
                r['prior_attempt_id']=uid(90); s['attempt_history']=[prior_attempt(r)]
            self.assertEqual(v.check_case(r,s,self.c),'ALLOWED',op)
    def batch_fixture(self):
        r,s=fixture(); source=copy.deepcopy(r['source']); source['recording_id']=uid(91); source['audio_asset_id']=uid(92)
        b=dict(batch_ref=uid(40),principal_id=uid(10),presented=[r['source'],source],selected=[uid(1)],
               presented_at=r['now'],decided_at=r['now'],grant_ref=uid(18),scope_ref=uid(17),verification='VERIFIED',presentation='AUTOMATIC')
        before=[dict(source=src,state='PENDING',prompt_count=0,prompted_at=None) for src in b['presented']]
        after=copy.deepcopy(before)
        for i,p in enumerate(after): p.update(state='EXPLICITLY_APPROVED' if i==0 else 'DEFERRED',prompt_count=1,prompted_at=r['now'])
        return b,before,after
    def test_all_batch_presented_marked_and_unselected_deferred(self):
        b,before,after=self.batch_fixture(); v.validate_batch_transition(b,before,after,'AUTOMATIC',self.c)
        for field,value in [('state','PENDING'),('prompt_count',0),('prompted_at',None)]:
            bad=copy.deepcopy(after); bad[1][field]=value
            with self.assertRaises(ValueError): v.validate_batch_transition(b,before,bad,'AUTOMATIC',self.c)
    def test_automatic_batch_excludes_old_prompts_and_deferred(self):
        b,before,after=self.batch_fixture()
        for change in ({'state':'DEFERRED'},{'prompt_count':1,'prompted_at':b['presented_at']}):
            bad=copy.deepcopy(before); bad[1].update(change)
            with self.assertRaises(ValueError): v.validate_batch_transition(b,bad,after,'AUTOMATIC',self.c)
    def test_user_pending_list_batch_can_authorize_deferred(self):
        b,before,after=self.batch_fixture(); before[0].update(state='DEFERRED',prompt_count=1,prompted_at=b['presented_at'])
        b['presentation']='USER'
        v.validate_batch_transition(b,before,after,'USER',self.c)
    def test_new_arrival_not_batch(self):
        b,before,after=self.batch_fixture(); b['selected'].append(uid(99))
        with self.assertRaises(ValueError): v.validate_batch_transition(b,before,after,'AUTOMATIC',self.c)
    def test_authorized_creation_binding(self):
        r,s=fixture('CREATE_CLOUD_JOB'); d=decision(r,s)
        _,other=fixture(); binding=other['job']; binding.update(creation_decision_ref=d['decision_id'],created_at=r['now'])
        v.validate_job_binding(binding,d,s,self.c)
        binding['processing_job_id']=uid(99)
        with self.assertRaises(ValueError): v.validate_job_binding(binding,d,s,self.c)
    def test_denied_creation_cannot_bind_job(self):
        r,s=fixture('CREATE_CLOUD_JOB'); d=decision(r,s)
        _,other=fixture(); binding=other['job']; binding.update(creation_decision_ref=d['decision_id'],created_at=r['now'])
        s['grant']['state']='REVOKED'
        with self.assertRaises(ValueError): v.validate_job_binding(binding,d,s,self.c)


if __name__=='__main__': unittest.main()
