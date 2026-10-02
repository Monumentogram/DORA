"""Exact additive Stage 8.2C workflow; the parent CI profile stays mandatory."""
OLD_INVENTORY = 'docs/contracts/persistence-8.2-device-tests.json'
INVENTORY = 'docs/contracts/original-audio-8.2c-device-tests.json'
GATE = "          python3 tools/validate_original_audio_lifecycle.py\n          python3 -m unittest discover -s tools -p 'test_original_audio_lifecycle.py'\n"
ANCHOR = "          python3 -m unittest discover -s tools -p 'test_encrypted_persistence*.py'\n"
EXTRA_STEP = '''      - name: Verify original audio lifecycle abrupt process death
        run: >-
          python3 tools/run_encrypted_persistence_crash.py --original-audio
          --serial emulator-5554 --expected-api ${{ matrix.api }} --expected-page-size 4096
          --apk android/core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk
          --receipt "${RUNNER_TEMP}/original-audio-process-death-receipt.json"

'''
UPLOAD = '      - name: Upload content-free persistence runtime receipt\n'
RECEIPT = '            ${{ runner.temp }}/original-audio-process-death-receipt.json\n'
PARENT_RECEIPT = '            ${{ runner.temp }}/persistence-process-death-receipt.json\n'


def require(condition, message):
    if not condition: raise ValueError(message)


def upgrade(workflow):
    for marker in (OLD_INVENTORY, ANCHOR, UPLOAD, PARENT_RECEIPT):
        require(workflow.count(marker) == 1, 'Parent workflow anchor mismatch')
    return (workflow.replace(OLD_INVENTORY, INVENTORY).replace(ANCHOR, ANCHOR + GATE)
            .replace(UPLOAD, EXTRA_STEP + UPLOAD).replace(PARENT_RECEIPT, PARENT_RECEIPT + RECEIPT))


def normalize(workflow):
    if INVENTORY not in workflow and EXTRA_STEP not in workflow: return workflow
    for marker in (INVENTORY, GATE, EXTRA_STEP, RECEIPT):
        require(workflow.count(marker) == 1, 'Exact original audio lifecycle CI gate missing or changed')
    original = (workflow.replace(INVENTORY, OLD_INVENTORY).replace(GATE, '')
                .replace(EXTRA_STEP, '').replace(RECEIPT, ''))
    require(upgrade(original) == workflow, 'Original audio workflow order changed')
    return original
