"""Reversible C3 inventory extension and separate protected diagnostic CI step."""
OLD_INVENTORY='docs/contracts/logical-recovery-8.5-device-tests.json'
NEW_INVENTORY='docs/contracts/persistence-8.6c3-device-tests.json'
ANCHOR='      - name: Verify abrupt process death at durable persistence phases\n'
HOST_ANCHOR='      - name: Verify Stage 8.5 logical recording recovery contract\n'
HOST_GATE='''      - name: Verify Stage 8.6C.3 exact admission and host negative controls
        run: |
          python3 tools/poco_persistence_optimization_admission.py
          python3 -m unittest discover -s tools -p 'test*persistence_optimization_c3.py'
          python3 -m unittest discover -s tools/poco_non_battery -p 'test*policy.py'

'''
GATE='''      - name: Verify Stage 8.6C.3 protected diagnostic inventory
        run: >-
          python3 tools/run_protected_diagnostic_c3.py
          --serial emulator-5554 --expected-api ${{ matrix.api }}
          --apk android/core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk
          --inventory docs/contracts/protected-diagnostic-8.6c3-tests.json
          --receipt "${RUNNER_TEMP}/protected-diagnostic-c3-receipt.json"

      - name: Upload content-free Stage 8.6C.3 diagnostic receipt
        if: always()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: protected-diagnostic-c3-api${{ matrix.api }}
          path: ${{ runner.temp }}/protected-diagnostic-c3-receipt.json
          if-no-files-found: error
          retention-days: 7

'''


def upgrade(workflow):
    if workflow.count(OLD_INVENTORY)!=1 or NEW_INVENTORY in workflow or workflow.count(ANCHOR)!=1 or 'protected-diagnostic-c3' in workflow or workflow.count(HOST_ANCHOR)!=1 or HOST_GATE in workflow:
        raise ValueError('C3 CI anchor changed')
    return workflow.replace(OLD_INVENTORY,NEW_INVENTORY,1).replace(ANCHOR,GATE+ANCHOR,1).replace(HOST_ANCHOR,HOST_GATE+HOST_ANCHOR,1)


def normalize(workflow):
    if NEW_INVENTORY not in workflow and 'protected-diagnostic-c3' not in workflow and 'Stage 8.6C.3' not in workflow:
        return workflow
    if workflow.count(NEW_INVENTORY)!=1 or OLD_INVENTORY in workflow or workflow.count(GATE)!=1 or workflow.count(HOST_GATE)!=1:
        raise ValueError('C3 mandatory CI inventory or diagnostic gate changed')
    original=workflow.replace(GATE,'',1).replace(HOST_GATE,'',1).replace(NEW_INVENTORY,OLD_INVENTORY,1)
    if upgrade(original)!=workflow:
        raise ValueError('C3 CI gate order changed')
    return original
