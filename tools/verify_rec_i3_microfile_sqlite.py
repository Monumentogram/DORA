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

def bootstrap(db):
    db.execute(run_ddl); db.execute(index_ddl)
    db.execute("INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",
               (run_id,candidate,"key-confirmation/run.kc",1,blob,blob,"VALID"))
def insert_children(db, candidate_id=candidate, cadence=5):
    db.execute(unit_ddl); db.execute(pub_ddl)
    db.execute("INSERT INTO recovery_microfile_unit_v2 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
               (run_id,candidate_id,0,0,1,cadence,"units/u-0000000000.ct",1,blob,"key-envelopes/u-0000000000.ks",1,blob,1,bytes([1])*32,"VALID"))
    db.execute("INSERT INTO recovery_manifest_publication_v2 VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
               (run_id,candidate_id,1,1,"manifests/g-00000000000000000001.ct",1,blob,"key-envelopes/manifest-g-00000000000000000001.ks",1,blob,blob,"VALID"))

db=sqlite3.connect(":memory:"); db.execute("PRAGMA foreign_keys=ON"); bootstrap(db); insert_children(db)
assert db.execute("SELECT count(*) FROM recovery_microfile_unit_v2").fetchone()[0] == 1

for kind in ("cross_candidate", "bad_cadence"):
    test=sqlite3.connect(":memory:"); test.execute("PRAGMA foreign_keys=ON"); bootstrap(test)
    try:
        insert_children(test, "REC-STREAM-TINK" if kind=="cross_candidate" else candidate, 6 if kind=="bad_cadence" else 5)
        raise AssertionError(kind + " unexpectedly accepted")
    except sqlite3.IntegrityError:
        pass

migration=sqlite3.connect(":memory:"); migration.execute("PRAGMA foreign_keys=ON")
migration.execute(run_ddl)
migration.execute("INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",(run_id,candidate,"key-confirmation/run.kc",1,blob,blob,"VALID"))
migration.execute(index_ddl); migration.execute(unit_ddl); migration.execute(pub_ddl)
assert migration.execute("SELECT candidate_id FROM recovery_run_bootstrap_v1 WHERE run_id=?",(run_id,)).fetchone() == (candidate,)
assert "beginTransaction" not in re.search(r"fun migrateV1ToV2\(.*?\n    }", source, re.S).group(0)
assert "oldVersion == 1 && newVersion == 2" in source and "UpgradePlan.REJECT" in source and "onDowngrade" in source

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
print("rejected", "cross_candidate", "bad_cadence")
print("failed_migration_rollback", "preserved_v1", "no_partial_v2")
print("different_run_writer_serialization", "second_writer_locked")
