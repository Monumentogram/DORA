"""Execute exact production v5 SQL on real host SQLite; no Android durability claim."""
import re
import sqlite3
import unittest
from pathlib import Path

import verify_rec_i3_streaming_sqlite as v4

SOURCE = v4.ROOT / "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryStreamPrefixSchema.kt"


def statements():
    source = SOURCE.read_text(encoding="utf-8")
    def literal(name):
        match = re.search(r"const val " + name + r'\s*=\s*(""".*?"""|"[^"\n]*")', source, re.S)
        return match.group(1)[3:-3] if match.group(1).startswith('"""') else match.group(1)[1:-1]
    sql = v4.production_sql()
    old, new = literal("OLD_INTERSECTION"), literal("NEW_INTERSECTION")
    assert sql["CREATE_STREAM_OUTCOME_TABLE"].count(old) == 2
    outcome = sql["CREATE_STREAM_OUTCOME_TABLE"].replace(old, new).replace(
        "('VERIFIED_SAME_DESCRIPTOR','UNPROVEN_OR_MISMATCH')",
        "('VERIFIED_SAME_DESCRIPTOR','UNPROVEN_OR_MISMATCH','VERIFIED_SURVIVING_CHECKPOINT_PREFIX')",
    )
    return [literal("COPY_OUTCOMES"), literal("COPY_RANGES"), literal("DROP_RANGES"),
            literal("DROP_OUTCOMES"), outcome, sql["CREATE_STREAM_RANGE_TABLE"],
            literal("RESTORE_OUTCOMES"), literal("RESTORE_RANGES"), sql["CREATE_STREAM_ACTIVE_RANGE_INDEX"],
            literal("DROP_OUTCOME_BACKUP"), literal("DROP_RANGE_BACKUP")]


def old_database():
    db = v4.connect()
    sql = v4.production_sql()
    v4.create_version(db, 2, sql)
    for name in v4.V4_NAMES:
        db.execute(sql[name])
    db.execute("PRAGMA user_version=4")
    db.execute("INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",
               ("run", "REC-STREAM-TINK", "key-confirmation/run.kc", 1, b"k"*32, b"a"*32, "VALID"))
    db.execute("INSERT INTO recovery_stream_checkpoint_v4 VALUES (" + ",".join("?"*21) + ")",
               ("run", "REC-STREAM-TINK", "CHECKPOINT", 1, 2, 8192, b"p"*32, 4056,
                "checkpoints/g-00000000000000000001.ct", 1, b"c"*32,
                "key-envelopes/checkpoint-g-00000000000000000001.ks", 1, b"e"*32,
                "stream/stream.ct", "key-envelopes/stream.ks", 1, b"s"*32, b"z"*32, b"i"*32, "VALID"))
    return db


def outcome(db, valid=False):
    row = {r[1]: None for r in db.execute("PRAGMA table_info(recovery_stream_outcome_v4)")}
    row.update(outcome_id=b"o"*32, run_id="run", candidate_id="REC-STREAM-TINK", checkpoint_generation=1,
               checkpoint_identity=b"i"*32, checkpoint_context_end=4056, checkpoint_prefix_bytes=8192,
               checkpoint_artifact_state="CRYPTOGRAPHICALLY_VALIDATED", source_witness_id=b"w"*32,
               witness_capability_state="INTERNALLY_VERIFIED", controller_snapshot_sha256=b"d"*32,
               oracle_identity_sha256=b"a"*32, oracle_plaintext_sha256=b"b"*32, accepted_end=8137,
               source_relative_name="stream/stream.ct", pre_fault_source_bytes=12288,
               pre_fault_source_sha256=b"s"*32, observed_source_bytes=10240, observed_source_sha256=b"e"*32,
               pre_fault_source_match_state="UNPROVEN_OR_MISMATCH", checkpoint_intersection_state="CONTEXT_ONLY",
               decision="FATAL", diagnostic_branch="PRE_INTERSECTION", terminal_outcome="NOT_REACHED",
               required_range_start=0, required_range_certainty="CONSERVATIVE_WHOLE_SOURCE",
               diagnostic_stage="STREAM_SOURCE_EXTENT", diagnostic_classification="STREAM_SOURCE_TRUNCATED",
               metadata_adopted=0, semantic_commit_adopted=0, processing_intent_adopted=0, state="SEALED")
    if valid:
        row.update(pre_fault_source_match_state="VERIFIED_SURVIVING_CHECKPOINT_PREFIX",
                   checkpoint_intersection_state="PROVEN", decision="VALID", diagnostic_branch="NONE",
                   terminal_outcome="AUTHENTICATION_FAILURE", recovered_end=8136,
                   recovered_beyond_checkpoint_bytes=4080, tail_loss_bytes=1, returned_plaintext_sha256=b"r"*32,
                   remainder_boundary_bytes=8192, remainder_certainty="EXACT_FORMAT_BOUNDARY",
                   required_range_start=8192, required_range_certainty="EXACT_FORMAT_BOUNDARY",
                   diagnostic_stage="NONE", diagnostic_classification="NONE")
    return row


def insert(db, row):
    db.execute("INSERT INTO recovery_stream_outcome_v4 (" + ",".join(row) + ") VALUES (" +
               ",".join("?"*len(row)) + ")", tuple(row.values()))


def seed_fatal_and_range(db):
    row = outcome(db)
    insert(db, row)
    db.execute("INSERT INTO recovery_stream_range_quarantine_v4 VALUES (" + ",".join("?"*17) + ")",
               (b"q"*32, row["outcome_id"], "run", "REC-STREAM-TINK", "FATAL", "PRE_INTERSECTION",
                "NOT_REACHED", "STREAM_SOURCE_TRUNCATED", "stream/stream.ct", 10240, b"e"*32,
                0, 10240, b"r"*32, "CONSERVATIVE_WHOLE_SOURCE", "RETAINED_IN_PLACE_DENY_APP_READS", "ACTIVE"))


def migrate(db):
    for statement in statements():
        db.execute(statement)
    db.execute("PRAGMA user_version=5")


class StreamPrefixSchemaTest(unittest.TestCase):
    def test_old_schema_rejects_new_proof(self):
        db = old_database()
        with self.assertRaises(sqlite3.IntegrityError):
            insert(db, outcome(db, True))

    def test_new_schema_accepts_only_precise_extent_and_proof(self):
        db = old_database()
        migrate(db)
        good = outcome(db, True)
        insert(db, good)
        db.execute("DELETE FROM recovery_stream_outcome_v4")
        for changes in [dict(observed_source_bytes=8191), dict(observed_source_bytes=12288),
                        dict(pre_fault_source_match_state="VERIFIED_SAME_DESCRIPTOR"),
                        dict(checkpoint_intersection_state="CONTEXT_ONLY"), dict(tail_loss_bytes=8161),
                        dict(recovered_end=4055), dict(processing_intent_adopted=1),
                        dict(required_range_start=None, required_range_certainty=None)]:
            with self.subTest(changes=changes), self.assertRaises(sqlite3.IntegrityError):
                insert(db, good | changes)

    def test_every_mutation_rolls_back_exact_schema_rows_and_version(self):
        for fail_after in range(1, len(statements())+1):
            db = old_database()
            seed_fatal_and_range(db)
            db.commit()
            before = list(db.iterdump())
            db.execute("BEGIN")
            for statement in statements()[:fail_after]:
                db.execute(statement)
            db.rollback()
            self.assertEqual(before, list(db.iterdump()), fail_after)
            self.assertEqual([], db.execute("SELECT name FROM sqlite_temp_master").fetchall())

    def test_historical_fatal_and_all_columns_unchanged(self):
        db = old_database()
        seed_fatal_and_range(db)
        before = db.execute("SELECT * FROM recovery_stream_outcome_v4").fetchall()
        ranges = db.execute("SELECT * FROM recovery_stream_range_quarantine_v4").fetchall()
        migrate(db)
        self.assertEqual(before, db.execute("SELECT * FROM recovery_stream_outcome_v4").fetchall())
        self.assertEqual(ranges, db.execute("SELECT * FROM recovery_stream_range_quarantine_v4").fetchall())
        self.assertEqual("FATAL", db.execute("SELECT decision FROM recovery_stream_outcome_v4").fetchone()[0])
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())
        self.assertEqual([("ok",)], db.execute("PRAGMA integrity_check").fetchall())
        self.assertEqual(v4.EXPECTED_OBJECTS, v4.inventory(db))


if __name__ == "__main__":
    unittest.main()
