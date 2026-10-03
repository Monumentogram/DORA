"""Existing emulator-only host-kill driver with exact logical-source phases."""
import hashlib
import json
from pathlib import Path
import re
import sys
import run_encrypted_persistence_crash as driver

TEST = 'com.monumentogram.dora.audio.persistence.LogicalRecordingProcessDeathTest#verifyLogicalRecordingProcessDeath'
PHASES = ('SOURCE_FINALIZED', 'PROJECTION_READ', 'METADATA_PARTIAL', 'DELETE_PENDING')


def main():
    driver.require('--original-audio' not in sys.argv, 'Logical source phases only')
    driver.TEST = TEST
    driver.PHASES = PHASES
    driver.MARKER = re.compile(r'^INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_(READY|VERIFIED):('
                              + '|'.join(PHASES) + r'):([1-9][0-9]*)\nINSTRUMENTATION_STATUS_CODE: 2$', re.MULTILINE)
    original_verify = driver.verified_pid
    driver.verified_pid = lambda output, phase, previous_pid, **kw: original_verify(output, phase, previous_pid, expected_test=TEST)
    driver.main()
    path = Path(sys.argv[sys.argv.index('--receipt') + 1])
    receipt = json.loads(path.read_text(encoding='utf-8'))
    receipt['shared_driver_sha256'] = receipt.pop('driver_sha256')
    receipt['driver_sha256'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
