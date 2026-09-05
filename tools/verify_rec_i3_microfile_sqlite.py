import hashlib
import pathlib
import re
import sqlite3
import tempfile

root = pathlib.Path(__file__).resolve().parents[1]
source = (root / "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryJournalDatabase.kt").read_text(encoding="utf-8")
def triple(name):
    m = re.search(rf"const val {name} =\s*\"\"\"(.*?)\"\"\"", source, re.S)
    assert m, name
    return m.group(1)
def quoted(name):
    m = re.search(rf"const val {name} =\s*\"([^\"]+)\"", source)
    assert m, name
    return m.group(1)

run_ddl, unit_ddl, pub_ddl = map(triple, ("CREATE_RUN_TABLE", "CREATE_UNIT_TABLE", "CREATE_PUBLICATION_TABLE"))
index_ddl = quoted("CREATE_RUN_IDENTITY_INDEX")
run_id = "00112233-4455-6677-8899-aabbccddeeff"
candidate = "REC-MICROFILE-TINK"
blob = bytes(32)

def normalize_sql(value):
    return " ".join(value.split()) if value is not None else None

def require_exact_v1(db):
    expected_columns = [
        (0,"run_id","TEXT",1,None,1),
        (1,"candidate_id","TEXT",1,None,0),
        (2,"key_confirmation_relative_name","TEXT",1,None,0),
        (3,"key_confirmation_bytes","INTEGER",1,None,0),
        (4,"key_confirmation_sha256","BLOB",1,None,0),
        (5,"canonical_alias_sha256","BLOB",1,None,0),
        (6,"key_confirmation_state","TEXT",1,None,0),
    ]
    actual_columns = db.execute("PRAGMA table_info(recovery_run_bootstrap_v1)").fetchall()
    table_sql = db.execute(
        "SELECT sql FROM sqlite_master WHERE type='table' AND name='recovery_run_bootstrap_v1'"
    ).fetchone()
    foreign_keys = db.execute("PRAGMA foreign_key_list(recovery_run_bootstrap_v1)").fetchall()
    indexes = db.execute("PRAGMA index_list(recovery_run_bootstrap_v1)").fetchall()
    triggers = db.execute(
        "SELECT name FROM sqlite_master WHERE type='trigger' AND tbl_name='recovery_run_bootstrap_v1'"
    ).fetchall()
    if not (
        actual_columns == expected_columns
        and table_sql is not None
        and normalize_sql(table_sql[0]) == normalize_sql(run_ddl)
        and foreign_keys == []
        and indexes == [(0,"sqlite_autoindex_recovery_run_bootstrap_v1_1",1,"pk",0)]
        and triggers == []
    ):
        raise ValueError("Recovery journal v1 schema is not exact")

def bootstrap(db):
    db.execute(run_ddl); db.execute(index_ddl)
    db.execute("INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",
               (run_id,candidate,"key-confirmation/run.kc",1,blob,blob,"VALID"))
def insert_children(db, candidate_id=candidate, cadence=5, plaintext_end=1, publication_kind="MANIFEST"):
    db.execute(unit_ddl); db.execute(pub_ddl)
    db.execute("""INSERT INTO recovery_microfile_unit_v2
               (run_id,candidate_id,unit_index,plaintext_start,plaintext_end,cadence_seconds,
                ciphertext_relative_name,ciphertext_bytes,ciphertext_sha256,
                key_envelope_relative_name,key_envelope_bytes,key_envelope_sha256,
                manifest_generation,processing_intent_id,state)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
               (run_id,candidate_id,0,0,plaintext_end,cadence,"units/u-0000000000.ct",1,blob,"key-envelopes/u-0000000000.ks",1,blob,1,bytes([1])*32,"VALID"))
    db.execute("""INSERT INTO recovery_manifest_publication_v2
               (run_id,candidate_id,publication_kind,generation,committed_end,
                publication_relative_name,publication_bytes,publication_sha256,
                key_envelope_relative_name,key_envelope_bytes,key_envelope_sha256,
                previous_publication_sha256,state)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
               (run_id,candidate_id,publication_kind,1,plaintext_end,"manifests/g-00000000000000000001.ct",1,blob,"key-envelopes/manifest-g-00000000000000000001.ks",1,blob,blob,"VALID"))

db=sqlite3.connect(":memory:"); db.execute("PRAGMA foreign_keys=ON"); bootstrap(db); insert_children(db)
assert db.execute("SELECT count(*) FROM recovery_microfile_unit_v2").fetchone()[0] == 1

for kind in ("cross_candidate", "bad_cadence", "oversized_unit", "wrong_publication_kind"):
    test=sqlite3.connect(":memory:"); test.execute("PRAGMA foreign_keys=ON"); bootstrap(test)
    try:
        insert_children(
            test,
            "REC-STREAM-TINK" if kind=="cross_candidate" else candidate,
            6 if kind=="bad_cadence" else 5,
            160001 if kind=="oversized_unit" else 1,
            "CHECKPOINT" if kind=="wrong_publication_kind" else "MANIFEST",
        )
        raise AssertionError(kind + " unexpectedly accepted")
    except sqlite3.IntegrityError:
        pass

for cadence, limit in ((5,160000),(15,480000),(30,960000)):
    boundary=sqlite3.connect(":memory:"); boundary.execute("PRAGMA foreign_keys=ON"); bootstrap(boundary)
    insert_children(boundary, cadence=cadence, plaintext_end=limit)
    assert boundary.execute("SELECT publication_kind FROM recovery_manifest_publication_v2").fetchone() == ("MANIFEST",)

migration=sqlite3.connect(":memory:"); migration.execute("PRAGMA foreign_keys=ON")
migration.execute(run_ddl)
migration.execute("INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",(run_id,candidate,"key-confirmation/run.kc",1,blob,blob,"VALID"))
require_exact_v1(migration)
migration.execute(index_ddl); migration.execute(unit_ddl); migration.execute(pub_ddl)
assert migration.execute("SELECT candidate_id FROM recovery_run_bootstrap_v1 WHERE run_id=?",(run_id,)).fetchone() == (candidate,)
assert "beginTransaction" not in re.search(r"fun migrateV1ToV2\(.*?\n    }", source, re.S).group(0)
assert "oldVersion == 1 && newVersion == 2" in source and "UpgradePlan.REJECT" in source and "onDowngrade" in source
for required in ("PRAGMA table_info", "sqlite_master WHERE type='table'", "PRAGMA foreign_key_list", "type='trigger'", "PRAGMA index_list"):
    assert required in source, "production exact-v1 inspection missing: " + required

malformed_v1 = {
    "nullable_and_no_pk": run_ddl.replace("run_id TEXT NOT NULL PRIMARY KEY", "run_id TEXT"),
    "weakened_candidate_check": run_ddl.replace(" CHECK(candidate_id IN ('REC-STREAM-TINK','REC-MICROFILE-TINK'))", ""),
    "weakened_digest_check": run_ddl.replace(" CHECK(length(key_confirmation_sha256) = 32)", ""),
}
for name, ddl in malformed_v1.items():
    malformed=sqlite3.connect(":memory:"); malformed.execute(ddl)
    try:
        require_exact_v1(malformed)
        raise AssertionError(name + " same-column v1 unexpectedly accepted")
    except ValueError:
        pass

extra_index=sqlite3.connect(":memory:"); extra_index.execute(run_ddl)
extra_index.execute("CREATE INDEX unexpected_v1_index ON recovery_run_bootstrap_v1(candidate_id)")
try:
    require_exact_v1(extra_index)
    raise AssertionError("extra v1 index unexpectedly accepted")
except ValueError:
    pass

extra_trigger=sqlite3.connect(":memory:"); extra_trigger.execute(run_ddl)
extra_trigger.execute("CREATE TRIGGER unexpected_v1_trigger AFTER INSERT ON recovery_run_bootstrap_v1 BEGIN SELECT 1; END")
try:
    require_exact_v1(extra_trigger)
    raise AssertionError("extra v1 trigger unexpectedly accepted")
except ValueError:
    pass

rollback=sqlite3.connect(":memory:"); rollback.execute(run_ddl)
rollback.execute("INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",(run_id,candidate,"key-confirmation/run.kc",1,blob,blob,"VALID"))
rollback.commit()
try:
    rollback.execute("BEGIN")
    rollback.execute(index_ddl); rollback.execute(unit_ddl); rollback.execute(unit_ddl)
except sqlite3.OperationalError:
    rollback.rollback()
assert rollback.execute("SELECT count(*) FROM recovery_run_bootstrap_v1").fetchone()[0] == 1
assert rollback.execute("SELECT count(*) FROM sqlite_master WHERE name IN ('recovery_run_candidate_v2','recovery_microfile_unit_v2')").fetchone()[0] == 0

with tempfile.TemporaryDirectory(prefix="dora-rec-i3-sqlite-") as temporary:
    database_path = pathlib.Path(temporary) / "journal.db"
    first = sqlite3.connect(database_path, timeout=0)
    second = sqlite3.connect(database_path, timeout=0)
    first.execute("PRAGMA journal_mode=WAL")
    first.execute(run_ddl)
    first.commit()
    first.execute("BEGIN IMMEDIATE")
    try:
        second.execute("BEGIN IMMEDIATE")
        raise AssertionError("second SQLite writer unexpectedly acquired")
    except sqlite3.OperationalError as error:
        assert "locked" in str(error)
    finally:
        first.rollback()
        first.close()
        second.close()
print("PASS host sqlite", sqlite3.sqlite_version)
print("ddl_sha256", hashlib.sha256((run_ddl+index_ddl+unit_ddl+pub_ddl).encode()).hexdigest())
print("fresh_v2_rows", 1, 1, 1)
print("migration_preserved", run_id, candidate)
print("cadence_boundaries", "5:160000", "15:480000", "30:960000")
print("rejected", "cross_candidate", "bad_cadence", "oversized_unit", "wrong_publication_kind")
print("failed_migration_rollback", "preserved_v1", "no_partial_v2")
print("rejected_malformed_v1", *malformed_v1, "extra_index", "extra_trigger")
print("different_run_writer_serialization", "second_writer_locked")
