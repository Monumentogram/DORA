"""Exact additive Stage 8.3 gate; all accepted parent jobs remain mandatory."""
GATE = '''      - name: Verify Stage 8.3 product recording admission
        run: |
          python3 tools/validate_product_recording.py
          python3 -m unittest discover -s tools -p 'test_product_recording.py'

'''
ANCHOR = '      - name: Verify locked search dependency artifact inventory\n'
DEVICE_ANCHOR = '      - name: Upload content-free persistence runtime receipt\n'
DEVICE_GATE = '''      - name: Build Stage 8.3 product UI instrumentation
        working-directory: android
        run: ./gradlew --no-daemon --stacktrace --dependency-verification strict :app:assembleDebug :app:assembleDebugAndroidTest

      - name: Verify Stage 8.3 product UI controls
        run: >-
          python3 tools/run_product_recording_device.py
          --serial emulator-5554 --expected-api ${{ matrix.api }}
          --apk android/app/build/outputs/apk/debug/app-debug.apk
          --test-apk android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
          --receipt "${RUNNER_TEMP}/product-recording-ui-receipt.json"

      - name: Upload content-free product recording UI receipt
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: product-recording-ui-api${{ matrix.api }}
          path: ${{ runner.temp }}/product-recording-ui-receipt.json
          if-no-files-found: error
          retention-days: 7

'''


def upgrade(workflow):
    if workflow.count(ANCHOR) != 1 or workflow.count(DEVICE_ANCHOR) != 1 or GATE in workflow or DEVICE_GATE in workflow:
        raise ValueError('Recording CI anchor mismatch')
    return workflow.replace(ANCHOR, GATE + ANCHOR).replace(DEVICE_ANCHOR, DEVICE_GATE + DEVICE_ANCHOR)


def normalize(workflow):
    if 'Verify Stage 8.3' not in workflow:
        return workflow
    if workflow.count(GATE) != 1 or workflow.count(DEVICE_GATE) != 1:
        raise ValueError('Recording gate omitted or changed')
    parent = workflow.replace(GATE, '', 1).replace(DEVICE_GATE, '', 1)
    if upgrade(parent) != workflow:
        raise ValueError('Recording gate order changed')
    return parent
