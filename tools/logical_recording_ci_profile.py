"""Additive Stage 8.4C CI, exactly reversible to accepted Stage 8.4."""
INVENTORY = 'docs/contracts/logical-recording-8.4c-device-tests.json'
OLD_INVENTORY = 'docs/contracts/vad-runtime-8.4-device-tests.json'
ANCHOR = '      - name: Verify Stage 8.4 repository logic without private runtime\n'
GATE = '''      - name: Verify Stage 8.4C logical recording contract
        run: |
          python3 tools/validate_logical_recording.py
          python3 -m unittest discover -s tools -p 'test_logical_recording*.py'

'''
CRASH_ANCHOR = '      - name: Build Stage 8.3 product UI instrumentation\n'
CRASH_GATE = '''      - name: Verify Stage 8.4C logical source abrupt process death
        run: >-
          python3 tools/run_logical_recording_crash.py
          --serial emulator-5554 --expected-api ${{ matrix.api }} --expected-page-size 4096
          --apk android/core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk
          --receipt "${{ runner.temp }}/logical-recording-process-death.json"

      - name: Upload content-free Stage 8.4C process death receipt
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: logical-recording-process-death-api${{ matrix.api }}
          path: ${{ runner.temp }}/logical-recording-process-death.json
          if-no-files-found: error
          retention-days: 7

'''


def upgrade(workflow):
    if workflow.count(ANCHOR) != 1 or GATE in workflow or workflow.count(OLD_INVENTORY) != 1 or workflow.count(CRASH_ANCHOR) != 1:
        raise ValueError('Stage8.4C CI anchors differ')
    return workflow.replace(ANCHOR, GATE + ANCHOR).replace(OLD_INVENTORY, INVENTORY).replace(CRASH_ANCHOR, CRASH_GATE + CRASH_ANCHOR)


def normalize(workflow):
    import logical_recovery_ci_profile as successor
    workflow = successor.normalize(workflow)
    if 'Stage 8.4C' not in workflow and INVENTORY not in workflow:
        return workflow
    if workflow.count(GATE) != 1 or workflow.count(CRASH_GATE) != 1 or workflow.count(INVENTORY) != 1 or OLD_INVENTORY in workflow:
        raise ValueError('Stage8.4C mandatory CI checks changed')
    parent = workflow.replace(GATE, '', 1).replace(CRASH_GATE, '', 1).replace(INVENTORY, OLD_INVENTORY)
    if upgrade(parent) != workflow:
        raise ValueError('Stage8.4C CI order changed')
    return parent
