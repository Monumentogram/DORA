"""Behavioral SQLite checks using production quarantine DDL; no Android durability claim."""
import re
import sqlite3
import unittest

import test_rec_stream_prefix_schema as prefix


Q = 'recovery_quarantine_intent_v4'
MICRO = 'REC-MICROFILE-TINK'
STREAM = 'REC-STREAM-TINK'
SOURCE = prefix.v4.ROOT / 'android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryMicrofileDispositionSchema.kt'
NEW_STATES = ('REFERENCED_REJECTED', 'REFERENCED_DEPENDENT')
ROLES = ('MICROFILE_CIPHERTEXT', 'MICROFILE_KEY_ENVELOPE', 'MANIFEST_CIPHERTEXT', 'MANIFEST_KEY_ENVELOPE')


def literal(name):
    text = SOURCE.read_text(encoding='utf-8')
    match = re.search(r'const val ' + name + r'\s*=\s*(""".*?"""|"[^"\n]*")', text, re.S)
    if match is None:
        raise ValueError('Missing production SQL literal: ' + name)
    token = match.group(1)
    return token[3:-3] if token.startswith('"""') else token[1:-1]


def quarantine_sql():
    old = prefix.v4.production_sql()['CREATE_QUARANTINE_TABLE']
    # Before implementation, exercise the actual existing schema, not a test replacement.
    if not SOURCE.exists():
        return old
    previous, replacement = literal('OLD_STATES'), literal('NEW_STATES')
    if old.count(previous) != 1 or old.count(literal('CONSTRAINT_ANCHOR')) != 1:
        raise ValueError('Unexpected historical quarantine DDL')
    return old.replace(previous, replacement).replace(
        literal('CONSTRAINT_ANCHOR'), literal('REFERENCED_CONSTRAINT') + literal('CONSTRAINT_ANCHOR'))


def database():
    db = prefix.old_database()
    prefix.seed_fatal_and_range(db)
    prefix.migrate(db)
    db.execute('INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)',
               ('micro', MICRO, 'key-confirmation/run.kc', 1, b'k'*32, b'a'*32, 'VALID'))
    return db


def row(observed='FINAL_ORPHAN', role='MICROFILE_CIPHERTEXT', **changes):
    result = dict(intent_id=b'q'*32, run_id='micro', candidate_id=MICRO,
                  bootstrap_binding='PRESENT', bootstrap_run_id='micro', bootstrap_candidate_id=MICRO,
                  artifact_role=role, observed_state=observed, source_relative_name='units/u-0000000001.ct',
                  destination_relative_name='objects/q-test.bin', source_bytes=17,
                  source_sha256=b's'*32, state='COMPLETED')
    return result | changes


def insert(db, value):
    db.execute('INSERT INTO ' + Q + ' (' + ','.join(value) + ') VALUES (' + ','.join('?'*len(value)) + ')',
               tuple(value.values()))


def install_current_quarantine(db):
    db.execute('DROP TABLE ' + Q)
    db.execute(quarantine_sql())


def snapshot(db):
    result = {}
    for (table,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'recovery_%' ORDER BY name"):
        info = db.execute('PRAGMA table_info(' + table + ')').fetchall()
        columns = [r[1] for r in info]
        keys = [r[1] for r in sorted(info, key=lambda r: r[5]) if r[5]]
        projection = ','.join('typeof(' + c + '),hex(CAST(' + c + ' AS BLOB))' for c in columns)
        result[table] = (columns, db.execute('SELECT ' + projection + ' FROM ' + table + ' ORDER BY ' + ','.join(keys)).fetchall())
    return result


class MicrofileDispositionSchemaTest(unittest.TestCase):
    def test_schema_six_retains_both_stream_proof_families_and_extent_rejections(self):
        for family in ('VERIFIED_SAME_DESCRIPTOR', 'VERIFIED_SURVIVING_CHECKPOINT_PREFIX'):
            db = prefix.old_database()
            prefix.migrate(db)
            install_current_quarantine(db)
            valid = prefix.outcome(db, True)
            if family == 'VERIFIED_SAME_DESCRIPTOR':
                valid.update(pre_fault_source_match_state=family, pre_fault_source_bytes=10240)
            prefix.insert(db, valid)
            db.execute('DELETE FROM recovery_stream_outcome_v4')
            cases = [dict(observed_source_bytes=8191), dict(checkpoint_intersection_state='CONTEXT_ONLY'),
                     dict(tail_loss_bytes=8161), dict(processing_intent_adopted=1),
                     dict(required_range_start=None, required_range_certainty=None)]
            cases.append(dict(observed_source_bytes=10239) if family == 'VERIFIED_SAME_DESCRIPTOR'
                         else dict(observed_source_bytes=12288))
            for changes in cases:
                with self.subTest(family=family, changes=changes), self.assertRaises(sqlite3.IntegrityError):
                    prefix.insert(db, valid | changes)
            db.close()

    def test_new_states_admit_exact_microfile_roles_in_both_durable_states(self):
        for observed in NEW_STATES:
            for role in ROLES:
                for state in ('PENDING', 'COMPLETED'):
                    with self.subTest(observed=observed, role=role, state=state):
                        db = database()
                        install_current_quarantine(db)
                        try:
                            insert(db, row(observed, role, state=state))
                        except sqlite3.IntegrityError as failure:
                            self.fail('Approved referenced MICROFILE disposition rejected: ' + str(failure))
                        self.assertEqual([(observed, role, state)], db.execute('SELECT observed_state,artifact_role,state FROM ' + Q).fetchall())
                        db.close()

    def test_new_states_reject_stream_absent_null_or_foreign_bootstrap_and_other_roles(self):
        changes = [
            dict(run_id='run', candidate_id=STREAM, bootstrap_run_id='run', bootstrap_candidate_id=STREAM, artifact_role='STREAM_CIPHERTEXT'),
            dict(bootstrap_binding='ABSENT', bootstrap_run_id=None, bootstrap_candidate_id=None),
            dict(bootstrap_run_id=None), dict(bootstrap_candidate_id=None),
            dict(bootstrap_run_id='foreign'), dict(bootstrap_candidate_id=STREAM),
            dict(artifact_role='KEY_CONFIRMATION'), dict(artifact_role='UNKNOWN_REGULAR'),
            dict(artifact_role='CHECKPOINT_CIPHERTEXT'),
        ]
        for observed in NEW_STATES:
            for change in changes:
                with self.subTest(observed=observed, change=change):
                    db = database()
                    install_current_quarantine(db)
                    with self.assertRaises(sqlite3.IntegrityError):
                        insert(db, row(observed, **change))
                    db.close()

    def test_historical_states_keep_stream_and_absent_bootstrap(self):
        for observed in ('TEMP_ONLY', 'TEMP_AND_FINAL', 'FINAL_ORPHAN', 'SQLITE_POINTS_TO_TEMP', 'UNKNOWN_OR_NON_ALLOWLISTED_NAME'):
            for candidate, role in ((MICRO, 'MICROFILE_CIPHERTEXT'), (STREAM, 'STREAM_CIPHERTEXT')):
                db = database()
                install_current_quarantine(db)
                insert(db, row(observed, role, candidate_id=candidate, bootstrap_binding='ABSENT', bootstrap_run_id=None, bootstrap_candidate_id=None))
                self.assertEqual([(observed, candidate)], db.execute('SELECT observed_state,candidate_id FROM ' + Q).fetchall())
                db.close()

    def test_every_sql_mutation_rolls_back_all_typed_rows_schema_and_version(self):
        if not SOURCE.exists():
            self.skipTest('Migration implementation absent; positive behavior test records RED')
        statements = [literal('CREATE_BACKUP'), literal('COPY_QUARANTINE'), literal('DROP_QUARANTINE'),
                      quarantine_sql(), literal('RESTORE_QUARANTINE'), literal('DROP_BACKUP')]
        for stop in range(1, len(statements) + 1):
            db = database()
            insert(db, row(source_relative_name=b'legacy\x00blob', destination_relative_name='legacy\x00destination'))
            db.commit()
            before = snapshot(db)
            schema = db.execute('SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name').fetchall()
            db.execute('BEGIN')
            for sql in statements[:stop]:
                db.execute(sql)
            db.rollback()
            self.assertEqual(before, snapshot(db), stop)
            self.assertEqual(schema, db.execute('SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name').fetchall(), stop)
            self.assertEqual([(5,)], db.execute('PRAGMA user_version').fetchall())
            self.assertEqual([], db.execute('SELECT name FROM sqlite_temp_master').fetchall())
            db.close()

    def test_migration_preserves_typed_quarantine_fatal_range_and_all_object_names(self):
        if not SOURCE.exists():
            self.skipTest('Migration implementation absent; positive behavior test records RED')
        db = database()
        insert(db, row(source_relative_name=b'legacy\x00blob', destination_relative_name='legacy\x00destination'))
        before = snapshot(db)
        objects = prefix.v4.inventory(db)
        for sql in (literal('CREATE_BACKUP'), literal('COPY_QUARANTINE'), literal('DROP_QUARANTINE'),
                    quarantine_sql(), literal('RESTORE_QUARANTINE'), literal('DROP_BACKUP')):
            db.execute(sql)
        self.assertEqual(before, snapshot(db))
        self.assertEqual(objects, prefix.v4.inventory(db))
        self.assertEqual([('FATAL', 'SEALED')], db.execute('SELECT decision,state FROM recovery_stream_outcome_v4').fetchall())
        self.assertEqual([], db.execute('PRAGMA foreign_key_check').fetchall())
        self.assertEqual([('ok',)], db.execute('PRAGMA integrity_check').fetchall())
        self.assertEqual([], db.execute('SELECT name FROM sqlite_temp_master').fetchall())
        db.close()


if __name__ == '__main__':
    unittest.main()
