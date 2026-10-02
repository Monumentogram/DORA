"""Exact additive Stage 8.2 CI profile; existing historical steps remain byte-preserved."""
STEP = '''      - name: Verify Stage 8.2 encrypted persistence admission
        run: |
          python3 tools/validate_encrypted_persistence.py --compiled
          python3 tools/validate_encrypted_persistence_native.py
          python3 -m unittest discover -s tools -p 'test_encrypted_persistence*.py'

'''

JOBS = '''
  encrypted-persistence:
    name: encrypted-persistence-api${{ matrix.api }}
    runs-on: ubuntu-latest
    timeout-minutes: 45
    strategy:
      fail-fast: false
      matrix:
        api: [28, 36]
    steps:
      - name: Check out repository
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          fetch-depth: 0
          persist-credentials: false

      - name: Set up JDK 17
        uses: actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961 # v5.7.0
        with:
          distribution: temurin
          java-version: "17"
          check-latest: false

      - name: Set up Gradle and validate wrapper
        uses: gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0
        with:
          cache-provider: basic
          validate-wrappers: true

      - name: Install Android persistence test SDK
        run: >-
          "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
          "platform-tools" "emulator" "platforms;android-36" "build-tools;36.0.0"
          "system-images;android-${{ matrix.api }};google_apis;x86_64"

      - name: Build encrypted persistence instrumentation
        working-directory: android
        run: ./gradlew --no-daemon --stacktrace --dependency-verification strict :core:audio:assembleDebugAndroidTest

      - name: Create persistence test emulator
        env:
          ANDROID_AVD_HOME: ${{ runner.temp }}/dora-persistence-avd
        run: |
          mkdir -p "${ANDROID_AVD_HOME}"
          printf 'no\\n' |
            "${ANDROID_HOME}/cmdline-tools/latest/bin/avdmanager" create avd \\
              --force --name dora_persistence \\
              --package "system-images;android-${{ matrix.api }};google_apis;x86_64" \\
              --device pixel_6

      - name: Start persistence test emulator
        env:
          ANDROID_AVD_HOME: ${{ runner.temp }}/dora-persistence-avd
        run: |
          sudo chmod 666 /dev/kvm
          nohup "${ANDROID_HOME}/emulator/emulator" @dora_persistence \\
            -port 5554 -no-window -noaudio -no-boot-anim -no-snapshot \\
            -no-metrics -gpu swiftshader_indirect -accel on -memory 2048 \\
            > "${RUNNER_TEMP}/dora-persistence-emulator.log" 2>&1 &
          emulator_pid=$!
          for attempt in $(seq 1 120); do
            kill -0 "${emulator_pid}" 2>/dev/null || exit 1
            boot_completed=$("${ANDROID_HOME}/platform-tools/adb" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\\r' || true)
            if [ "${boot_completed}" = "1" ]; then
              exit 0
            fi
            sleep 5
          done
          exit 1

      - name: Verify exact persistence and system authentication runtime inventory
        run: >-
          python3 tools/run_encrypted_persistence_device.py
          --serial emulator-5554 --expected-api ${{ matrix.api }} --expected-page-size 4096
          --apk android/core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk
          --inventory docs/contracts/persistence-8.2-device-tests.json
          --receipt "${RUNNER_TEMP}/persistence-runtime-receipt.json"

      - name: Verify abrupt process death at durable persistence phases
        run: >-
          python3 tools/run_encrypted_persistence_crash.py
          --serial emulator-5554 --expected-api ${{ matrix.api }} --expected-page-size 4096
          --apk android/core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk
          --receipt "${RUNNER_TEMP}/persistence-process-death-receipt.json"

      - name: Upload content-free persistence runtime receipt
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: persistence-runtime-api${{ matrix.api }}
          path: |
            ${{ runner.temp }}/persistence-runtime-receipt.json
            ${{ runner.temp }}/persistence-process-death-receipt.json
          if-no-files-found: error
          retention-days: 7
'''

REPLACEMENTS = {
    'app/build/outputs/apk/release/app-release-unsigned.apk --allowlist native-libs-allowlist.txt':
        'app/build/outputs/apk/release/app-release-unsigned.apk --allowlist product-native-libs-allowlist.txt',
    '          android/app/build/outputs/apk/debug/app-debug.apk\n\n      - name: Verify capture PoC native':
        '          android/app/build/outputs/apk/debug/app-debug.apk\n          --allowlist android/product-native-libs-allowlist.txt\n\n      - name: Verify capture PoC native',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate(workflow):
    import original_audio_ci_profile
    workflow = original_audio_ci_profile.normalize(workflow)
    require(workflow.count(STEP) == 1 and workflow.count(JOBS) == 1,
            'Required exact encrypted persistence CI checks missing or changed')
    require(workflow.index('      - name: Verify unsigned release and locked runtime SBOM graph\n')
            < workflow.index(STEP), 'Compiled persistence admission must follow unsigned release build')
    for original, current in REPLACEMENTS.items():
        require(workflow.count(current) == 1 and original not in workflow,
                'Product/capture native admission boundaries changed')


def normalize(workflow):
    """Strip only the admitted exact additions before historical whole-workflow comparison."""
    import original_audio_ci_profile
    workflow = original_audio_ci_profile.normalize(workflow)
    if '      - name: Verify Stage 8.2' not in workflow and '\n  encrypted-persistence:' not in workflow:
        return workflow
    validate(workflow)
    workflow = workflow.replace(STEP, '', 1).replace(JOBS, '', 1)
    for original, current in REPLACEMENTS.items():
        workflow = workflow.replace(current, original, 1)
    return workflow
