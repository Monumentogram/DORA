"""Exact additive credential-free Stage 8.4 checks; preserve every predecessor step."""
INVENTORY = 'docs/contracts/vad-runtime-8.4-device-tests.json'
OLD_INVENTORY = 'docs/contracts/recording-latency-8.3-device-tests.json'
ANCHOR = '      - name: Verify locked search dependency artifact inventory\n'
GATE = '''      - name: Verify Stage 8.4 repository logic without private runtime
        run: |
          python3 tools/validate_vad_runtime.py
          python3 -m unittest discover -s tools -p 'test_vad_runtime*.py'
          python3 -m unittest discover -s tools -p 'test_vad_artifact_admission.py'
          cd android
          ./gradlew --no-daemon --stacktrace --dependency-verification strict :ml:vad-api:testDebugUnitTest :ml:vad-sherpa:testDebugUnitTest :ml:vad-api:lintDebug :ml:vad-sherpa:lintDebug

'''
CRASH_ANCHOR = '      - name: Build Stage 8.3 product UI instrumentation\n'
CRASH_GATE = '      - name: Verify Stage 8.4 segmentation abrupt process death\n        run: >-\n          python3 tools/run_vad_segmentation_crash.py\n          --serial emulator-5554 --expected-api ${{ matrix.api }} --expected-page-size 4096\n          --apk android/core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk\n          --receipt "${{ runner.temp }}/vad-segmentation-process-death.json"\n\n      - name: Upload content-free Stage 8.4 process death receipt\n        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1\n        with:\n          name: vad-segmentation-process-death-api${{ matrix.api }}\n          path: ${{ runner.temp }}/vad-segmentation-process-death.json\n          if-no-files-found: error\n          retention-days: 7\n\n'



def upgrade(workflow):
    if workflow.count(ANCHOR) != 1 or GATE in workflow or workflow.count(OLD_INVENTORY) != 1 or workflow.count(CRASH_ANCHOR) != 1:
        raise ValueError('Stage8.4 CI anchor/inventory mismatch')
    return workflow.replace(ANCHOR, GATE + ANCHOR).replace(OLD_INVENTORY, INVENTORY).replace(CRASH_ANCHOR, CRASH_GATE + CRASH_ANCHOR)


def normalize(workflow):
    if 'Stage 8.4 repository logic' not in workflow and INVENTORY not in workflow:
        return workflow
    if workflow.count(GATE) != 1 or workflow.count(INVENTORY) != 1 or OLD_INVENTORY in workflow or workflow.count(CRASH_GATE) != 1:
        raise ValueError('Stage8.4 CI checks removed or modified')
    parent = workflow.replace(GATE, '', 1).replace(CRASH_GATE, '', 1).replace(INVENTORY, OLD_INVENTORY)
    if upgrade(parent) != workflow:
        raise ValueError('Stage8.4 CI order changed')
    return parent
