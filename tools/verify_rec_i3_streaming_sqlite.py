"""Executable host-SQLite proof for the admitted REC-I3 journal schema v4."""

from __future__ import annotations

import re
import sqlite3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ADR = ROOT / "docs/adr/ADR-0005-poc-recovery-streaming-persistence-and-range-quarantine.md"
KOTLIN = ROOT / (
    "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/"
    "journal/AndroidRecoveryJournalDatabase.kt"
)
E36_PREFLIGHT = ROOT / (
    "android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/"
    "candidate/RecoveryE36GapiPreflightInstrumentedTest.kt"
)
E36_SQLITE_RUNTIME_PIN = "4c318c054da39768340f059db5687051dde8a843"

V4_NAMES = (
    "CREATE_QUARANTINE_TABLE",
    "CREATE_STREAM_CHECKPOINT_TABLE",
    "CREATE_STREAM_OUTCOME_TABLE",
    "CREATE_STREAM_RANGE_TABLE",
    "CREATE_STREAM_ACTIVE_RANGE_INDEX",
)

EXPECTED_OBJECTS = {
    *(('table', name, name) for name in (
        'recovery_run_bootstrap_v1',
        'recovery_microfile_unit_v2',
        'recovery_manifest_publication_v2',
        'recovery_quarantine_intent_v4',
        'recovery_stream_checkpoint_v4',
        'recovery_stream_outcome_v4',
        'recovery_stream_range_quarantine_v4',
    )),
    ('index', 'recovery_run_candidate_v2', 'recovery_run_bootstrap_v1'),
    ('index', 'recovery_stream_active_range_v4', 'recovery_stream_range_quarantine_v4'),
    *(('index', name, table) for name, table in (
        ('sqlite_autoindex_recovery_run_bootstrap_v1_1', 'recovery_run_bootstrap_v1'),
        ('sqlite_autoindex_recovery_microfile_unit_v2_1', 'recovery_microfile_unit_v2'),
        ('sqlite_autoindex_recovery_microfile_unit_v2_2', 'recovery_microfile_unit_v2'),
        ('sqlite_autoindex_recovery_manifest_publication_v2_1', 'recovery_manifest_publication_v2'),
        ('sqlite_autoindex_recovery_quarantine_intent_v4_1', 'recovery_quarantine_intent_v4'),
        ('sqlite_autoindex_recovery_quarantine_intent_v4_2', 'recovery_quarantine_intent_v4'),
        ('sqlite_autoindex_recovery_stream_checkpoint_v4_1', 'recovery_stream_checkpoint_v4'),
        ('sqlite_autoindex_recovery_stream_checkpoint_v4_2', 'recovery_stream_checkpoint_v4'),
        ('sqlite_autoindex_recovery_stream_checkpoint_v4_3', 'recovery_stream_checkpoint_v4'),
        ('sqlite_autoindex_recovery_stream_outcome_v4_1', 'recovery_stream_outcome_v4'),
        ('sqlite_autoindex_recovery_stream_outcome_v4_2', 'recovery_stream_outcome_v4'),
        ('sqlite_autoindex_recovery_stream_outcome_v4_3', 'recovery_stream_outcome_v4'),
        ('sqlite_autoindex_recovery_stream_range_quarantine_v4_1', 'recovery_stream_range_quarantine_v4'),
        ('sqlite_autoindex_recovery_stream_range_quarantine_v4_2', 'recovery_stream_range_quarantine_v4'),
        ('sqlite_autoindex_recovery_stream_range_quarantine_v4_3', 'recovery_stream_range_quarantine_v4'),
    )),
}


def kotlin_sql(name: str, source: str) -> str:
    triple = re.search(rf"const val {name}\s*=\s*\"\"\"(.*?)\"\"\"", source, re.DOTALL)
    if triple:
        return triple.group(1)
    quoted = re.search(rf'const val {name}\s*=\s*\n?\s*"([^"\r\n]+)"', source)
    if quoted:
        return quoted.group(1)
    raise AssertionError(f"missing Kotlin SQL constant {name}")


def normalized(sql: str) -> str:
    return " ".join(sql.split())


def production_sql() -> dict[str, str]:
    source = KOTLIN.read_text(encoding="utf-8")
    result = {name: kotlin_sql(name, source) for name in (
        "CREATE_RUN_TABLE", "CREATE_RUN_IDENTITY_INDEX", "CREATE_UNIT_TABLE",
        "CREATE_PUBLICATION_TABLE", "CREATE_QUARANTINE_V3_TABLE", *V4_NAMES,
    )}
    adr_blocks = re.findall(r"```sql\s*(.*?)\s*```", ADR.read_text(encoding="utf-8"), re.DOTALL)
    assert len(adr_blocks) == 5, f"expected five exact ADR SQL blocks, found {len(adr_blocks)}"
    for name, block in zip(V4_NAMES, adr_blocks, strict=True):
        assert normalized(result[name]) == normalized(block), f"{name} differs from the ADR"
    return result


def assert_android_configuration_uses_open_params_and_query_api() -> None:
    source = KOTLIN.read_text(encoding="utf-8")
    helper = re.search(
        r"private class RecoveryJournalSqliteHelper\(context: Context\)\s*:\s*"
        r"SQLiteOpenHelper\((.*?)\)\s*\{",
        source,
        re.DOTALL,
    )
    assert helper, "missing Recovery journal SQLiteOpenHelper construction"
    constructor = helper.group(1)
    assert re.search(
        r"SQLiteDatabase\.OpenParams\.Builder\(\).*?"
        r"\.addOpenFlags\(SQLiteDatabase\.ENABLE_WRITE_AHEAD_LOGGING\).*?"
        r"\.setSynchronousMode\(SQLiteDatabase\.SYNC_MODE_FULL\).*?"
        r"\.build\(\)",
        constructor,
        re.DOTALL,
    ), "Recovery journal must select WAL and synchronous FULL in pre-open OpenParams"
    assert "setWriteAheadLoggingEnabled(true)" not in source, (
        "Recovery journal WAL selection must be part of pre-open OpenParams"
    )
    on_configure = re.search(
        r"override fun onConfigure\(database: SQLiteDatabase\) \{(.*?)\n    \}",
        source,
        re.DOTALL,
    )
    assert on_configure, "missing Recovery journal onConfigure"
    body = on_configure.group(1)
    assert 'execSQL("PRAGMA synchronous=FULL")' not in body, (
        "connection-local synchronous assignment cannot configure every WAL pool connection"
    )
    assert 'execSQL("PRAGMA wal_autocheckpoint=0")' not in body, (
        "wal_autocheckpoint returns data on Android and cannot use execSQL"
    )
    assert re.search(
        r'rawQuery\("PRAGMA wal_autocheckpoint=0", null\)\.use \{ cursor ->.*?'
        r'cursor\.moveToFirst\(\).*?cursor\.getInt\(0\) == 0',
        body,
        re.DOTALL,
    ), "wal_autocheckpoint must use the query API and verify SQLite accepted zero"
    preflight = E36_PREFLIGHT.read_text(encoding="utf-8")
    assert f'.put("integratedRuntimePin", "{E36_SQLITE_RUNTIME_PIN}")' in preflight, (
        "E36 evidence must identify the exact SQLite-remediation runtime commit"
    )


def connect() -> sqlite3.Connection:
    database = sqlite3.connect(":memory:")
    database.execute("PRAGMA foreign_keys=ON")
    return database


def create_version(database: sqlite3.Connection, version: int, sql: dict[str, str]) -> None:
    database.execute(sql["CREATE_RUN_TABLE"])
    if version >= 2:
        database.execute(sql["CREATE_RUN_IDENTITY_INDEX"])
        database.execute(sql["CREATE_UNIT_TABLE"])
        database.execute(sql["CREATE_PUBLICATION_TABLE"])
    if version >= 3:
        database.execute(sql["CREATE_QUARANTINE_V3_TABLE"])
    database.execute(f"PRAGMA user_version={version}")


def seed_v3(database: sqlite3.Connection) -> tuple[object, ...]:
    database.execute(
        "INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",
        ("run-alpha", "REC-MICROFILE-TINK", "key-confirmation/run.kc", 1,
         bytes(range(32)), bytes(reversed(range(32))), "VALID"),
    )
    row = (
        b"i" * 32, "run-alpha", "REC-MICROFILE-TINK", "PRESENT", "run-alpha",
        "REC-MICROFILE-TINK", "MICROFILE_CIPHERTEXT", "TEMP_ONLY",
        "units/source-é.ct", "quarantine/destination-é.ct", 7, b"s" * 32, "PENDING",
    )
    database.execute(
        "INSERT INTO recovery_quarantine_intent_v3 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", row
    )
    return row


def mutation_statements(sql: dict[str, str]) -> list[str]:
    return [
        sql["CREATE_QUARANTINE_TABLE"],
        "INSERT INTO recovery_quarantine_intent_v4 SELECT * FROM recovery_quarantine_intent_v3",
        sql["CREATE_STREAM_CHECKPOINT_TABLE"],
        sql["CREATE_STREAM_OUTCOME_TABLE"],
        sql["CREATE_STREAM_RANGE_TABLE"],
        sql["CREATE_STREAM_ACTIVE_RANGE_INDEX"],
        "DROP TABLE recovery_quarantine_intent_v3",
    ]


def migrate_v3_to_v4(database: sqlite3.Connection, sql: dict[str, str], stop_after: int | None = None) -> None:
    for position, statement in enumerate(mutation_statements(sql), start=1):
        database.execute(statement)
        if stop_after == position:
            raise RuntimeError(f"failpoint {position}")


def upgrade(database: sqlite3.Connection, old: int, sql: dict[str, str]) -> None:
    if old == 1:
        database.execute(sql["CREATE_RUN_IDENTITY_INDEX"])
        database.execute(sql["CREATE_UNIT_TABLE"])
        database.execute(sql["CREATE_PUBLICATION_TABLE"])
    if old <= 2:
        database.execute(sql["CREATE_QUARANTINE_V3_TABLE"])
    migrate_v3_to_v4(database, sql)
    database.execute("PRAGMA user_version=4")


def inventory(database: sqlite3.Connection) -> set[tuple[str, str, str]]:
    rows = database.execute(
        "SELECT type,name,tbl_name FROM sqlite_master "
        "WHERE name LIKE 'recovery_%' OR name LIKE 'sqlite_autoindex_recovery_%'"
    ).fetchall()
    result = set(rows)
    assert len(rows) == len(result), "duplicate SQLite object triples"
    return result


def assert_v4(database: sqlite3.Connection, sql: dict[str, str]) -> None:
    assert database.execute("PRAGMA user_version").fetchone()[0] == 4
    assert inventory(database) == EXPECTED_OBJECTS
    assert database.execute("PRAGMA foreign_key_check").fetchall() == []
    actual_sql = dict(database.execute(
        "SELECT name,sql FROM sqlite_master WHERE sql IS NOT NULL AND "
        "(name LIKE 'recovery_%' OR name LIKE 'sqlite_autoindex_recovery_%')"
    ))
    for name in V4_NAMES:
        object_name = re.search(r"CREATE (?:TABLE|INDEX)\s+(\w+)", sql[name]).group(1)
        assert normalized(actual_sql[object_name]) == normalized(sql[name])


def assert_checkpoint_requires_bootstrap_parent(database: sqlite3.Connection) -> None:
    checkpoint = (
        "run-stream-parent", "REC-STREAM-TINK", "CHECKPOINT", 1, 0, 0,
        b"p" * 32, 0, "checkpoints/g-00000000000000000001.ct", 1, b"c" * 32,
        "key-envelopes/checkpoint-g-00000000000000000001.ks", 1, b"e" * 32,
        "stream/stream.ct", "key-envelopes/stream.ks", 1, b"s" * 32,
        b"z" * 32, b"i" * 32, "VALID",
    )
    insert_checkpoint = "INSERT INTO recovery_stream_checkpoint_v4 VALUES (" + ",".join("?" * 21) + ")"

    try:
        database.execute(insert_checkpoint, checkpoint)
    except sqlite3.IntegrityError as error:
        assert "FOREIGN KEY constraint failed" in str(error)
    else:
        raise AssertionError("orphan checkpoint insert did not fail its bootstrap foreign key")

    database.execute(
        "INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",
        (
            "run-stream-parent", "REC-STREAM-TINK", "key-confirmation/run.kc", 1,
            b"k" * 32, b"a" * 32, "VALID",
        ),
    )
    database.execute(insert_checkpoint, checkpoint)
    assert database.execute(
        "SELECT COUNT(*) FROM recovery_stream_checkpoint_v4 WHERE run_id=? AND candidate_id=?",
        checkpoint[:2],
    ).fetchone()[0] == 1
    database.execute(
        "DELETE FROM recovery_stream_checkpoint_v4 WHERE run_id=? AND candidate_id=?",
        checkpoint[:2],
    )
    database.execute(
        "DELETE FROM recovery_run_bootstrap_v1 WHERE run_id=? AND candidate_id=?",
        checkpoint[:2],
    )
    assert database.execute("PRAGMA foreign_key_check").fetchall() == []


def verify() -> None:
    assert_android_configuration_uses_open_params_and_query_api()
    sql = production_sql()

    fresh = connect()
    create_version(fresh, 2, sql)
    for name in V4_NAMES:
        fresh.execute(sql[name])
    fresh.execute("PRAGMA user_version=4")
    assert_v4(fresh, sql)
    assert_checkpoint_requires_bootstrap_parent(fresh)

    for old in (1, 2, 3):
        database = connect()
        create_version(database, old, sql)
        expected = seed_v3(database) if old == 3 else None
        upgrade(database, old, sql)
        assert_v4(database, sql)
        if expected is not None:
            actual = database.execute(
                "SELECT * FROM recovery_quarantine_intent_v4 WHERE intent_id=?", (expected[0],)
            ).fetchone()
            assert actual == expected, "v3 quarantine row did not migrate byte-for-byte"

    for failpoint in range(1, len(mutation_statements(sql)) + 1):
        database = connect()
        create_version(database, 3, sql)
        expected = seed_v3(database)
        database.commit()
        before = list(database.iterdump())
        try:
            database.execute("BEGIN")
            migrate_v3_to_v4(database, sql, stop_after=failpoint)
        except RuntimeError:
            database.rollback()
        else:
            raise AssertionError(f"failpoint {failpoint} did not fire")
        assert list(database.iterdump()) == before, f"failpoint {failpoint} did not restore exact v3"
        assert database.execute("PRAGMA user_version").fetchone()[0] == 3
        assert database.execute(
            "SELECT * FROM recovery_quarantine_intent_v3 WHERE intent_id=?", (expected[0],)
        ).fetchone() == expected


if __name__ == "__main__":
    verify()
    print(
        f"PASS REC-I3 streaming SQLite schema v4 ({sqlite3.sqlite_version}); "
        "orphan checkpoint rejected; bootstrap parent accepted"
    )
