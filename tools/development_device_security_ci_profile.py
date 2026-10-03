"""Additive debug/release no-device-lock negative gate; prior CI remains exact."""
GATE = """      - name: Verify owner-authorized development device security
        run: |
          python3 tools/validate_development_device_security.py
          python3 -m unittest discover -s tools -p 'test_development_device_security.py'
          cd android
          ./gradlew --no-daemon --stacktrace --dependency-verification strict :core:audio:testDebugUnitTest :core:audio:testReleaseUnitTest

"""
ANCHOR = '      - name: Verify Stage 8.3 instant recording controls admission\n'


def upgrade(workflow):
    if workflow.count(ANCHOR) != 1 or GATE in workflow:
        raise ValueError('Development security CI anchor mismatch')
    return workflow.replace(ANCHOR, GATE + ANCHOR)


def normalize(workflow):
    if 'owner-authorized development device security' not in workflow:
        return workflow
    if workflow.count(GATE) != 1:
        raise ValueError('Development security gate omitted or changed')
    parent = workflow.replace(GATE, '', 1)
    if upgrade(parent) != workflow:
        raise ValueError('Development security CI order changed')
    return parent
