"""Exact additive latency gate and expanded device inventory; no parent checks removed."""
GATE = '''      - name: Verify Stage 8.3 recording latency remediation admission
        run: |
          python3 tools/validate_recording_latency.py
          python3 -m unittest discover -s tools -p 'test_recording_latency.py'

'''
ANCHOR = '      - name: Verify Stage 8.3 product recording admission\n'
OLD_INVENTORY = 'docs/contracts/original-audio-8.2c-device-tests.json'
INVENTORY = 'docs/contracts/recording-latency-8.3-device-tests.json'


def upgrade(workflow):
    if workflow.count(ANCHOR) != 1 or workflow.count(OLD_INVENTORY) != 1 or GATE in workflow:
        raise ValueError('Latency CI anchor mismatch')
    return workflow.replace(ANCHOR, GATE + ANCHOR).replace(OLD_INVENTORY, INVENTORY)


def normalize(workflow):
    if 'recording latency remediation' not in workflow and INVENTORY not in workflow:
        return workflow
    if workflow.count(GATE) != 1 or workflow.count(INVENTORY) != 1 or OLD_INVENTORY in workflow:
        raise ValueError('Latency gate or expanded inventory omitted or changed')
    parent = workflow.replace(GATE, '', 1).replace(INVENTORY, OLD_INVENTORY)
    if upgrade(parent) != workflow:
        raise ValueError('Latency CI order changed')
    return parent
