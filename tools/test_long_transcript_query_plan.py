"""Exercise the production Room page query against SQLite's actual keyset planner."""
from pathlib import Path
import re
import sqlite3
import unittest


class LongTranscriptQueryPlanTest(unittest.TestCase):
    def test_export_page_walk_seeks_after_cursor_instead_of_rescanning_prefix(self):
        source = (Path(__file__).resolve().parents[1] / "android/core/audio/src/main/kotlin/"
                  "com/monumentogram/dora/audio/persistence/journal/PagedTranscriptEntities.kt")
        query = re.search(r'@Query\(\s*"([^"]+)"\s*\)\s*fun nextPage\(',
                          source.read_text(encoding="utf-8")).group(1)

        def instructions(count):
            database = sqlite3.connect(":memory:")
            database.row_factory = sqlite3.Row
            database.execute("CREATE TABLE transcript_page(assetId TEXT,jobId TEXT,"
                             "partIndex INTEGER,pageIndex INTEGER,payload BLOB,"
                             "PRIMARY KEY(assetId,jobId,partIndex,pageIndex))")
            database.executemany("INSERT INTO transcript_page VALUES('a','j',?,?,?)",
                                 ((index // 16, index % 16, b"page") for index in range(count)))
            ticks = 0

            def tick():
                nonlocal ticks
                ticks += 1
                return 0

            database.set_progress_handler(tick, 100)
            part, page, visited = -1, -1, 0
            while True:
                row = database.execute(query, dict(asset="a", job="j", part=part, page=page)).fetchone()
                if row is None:
                    break
                part, page = row["partIndex"], row["pageIndex"]
                visited += 1
            self.assertEqual(count, visited)
            database.close()
            return ticks

        small, large = instructions(128), instructions(512)
        # Four times as many pages must stay close to four times the VM work, not sixteen.
        self.assertLess(large, small * 5.5)


if __name__ == "__main__":
    unittest.main()
