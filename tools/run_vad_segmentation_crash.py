"""Reuse the admitted emulator-only death driver with one exact Stage8.4 synthetic test.

No product microphone, private runtime or POCO acoustic proof. The predecessor driver and
its process/PID/absence/timeout checks are unchanged and are part of this receipt identity.
"""
import hashlib
import json
from pathlib import Path
import re
import sys

import run_encrypted_persistence_crash as driver

TEST = 'com.monumentogram.dora.audio.persistence.SegmentationProcessDeathTest#verifySegmentationProcessDeath'
PHASES = ('OPEN_SPEECH', 'BEFORE_SEMANTIC', 'AFTER_SEMANTIC', 'BEFORE_ROTATION',
          'AFTER_ROTATION', 'OVERLAP_PENDING', 'DURABILITY_PENDING')


def main():
    driver.require('--original-audio' not in sys.argv, 'Only Stage8.4 segmentation test is admitted')
    driver.TEST = TEST
    driver.PHASES = PHASES
    driver.MARKER = re.compile(
        r'^INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_(READY|VERIFIED):('
        + '|'.join(PHASES) + r'):([1-9][0-9]*)\nINSTRUMENTATION_STATUS_CODE: 2$', re.MULTILINE)
    # The predecessor's default argument is frozen at definition time; explicitly select our test.
    original_verify = driver.verified_pid
    driver.verified_pid = lambda output, phase, previous_pid, **kw: original_verify(
        output, phase, previous_pid, expected_test=TEST)
    driver.main()
    receipt_path = Path(sys.argv[sys.argv.index('--receipt') + 1])
    receipt = json.loads(receipt_path.read_text(encoding='utf-8'))
    receipt['shared_driver_sha256'] = receipt.pop('driver_sha256')
    receipt['driver_sha256'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    receipt['profile_sha256'] = '1c5ceb70f08593dfa96e522f43c51b7b28e36ab8b65a423fae417cc4e1cd2292'
    receipt_path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
