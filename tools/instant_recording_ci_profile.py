"""Exact additive instant-control gate; all prior checks and inventory remain."""
GATE = """      - name: Verify Stage 8.3 instant recording controls admission
        run: |
          python3 tools/validate_instant_recording.py
          python3 -m unittest discover -s tools -p 'test_instant_recording.py'

"""
ANCHOR = '      - name: Verify Stage 8.3 recording latency remediation admission\n'
INVENTORY = 'docs/contracts/recording-latency-8.3-device-tests.json'

def upgrade(workflow):
    if workflow.count(ANCHOR) != 1 or GATE in workflow:
        raise ValueError('Instant-control CI anchor mismatch')
    return workflow.replace(ANCHOR, GATE + ANCHOR)

def normalize(workflow):
    if 'instant recording controls admission' not in workflow:
        return workflow
    if workflow.count(GATE) != 1:
        raise ValueError('Instant-control gate omitted or changed')
    parent = workflow.replace(GATE, '', 1)
    if upgrade(parent) != workflow:
        raise ValueError('Instant-control CI order changed')
    return parent
