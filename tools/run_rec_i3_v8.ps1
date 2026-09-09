[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$EvidenceBase,
    [Parameter(Mandatory = $true)][string]$StagingRoot,
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$AcceptedCommit,
    [Parameter(Mandatory = $true)][string]$AcceptedTree,
    [string]$AvdName = "dora_api36_recovery"
)

$ErrorActionPreference = "Stop"
$expectedFingerprint = "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys"
$packageNames = "com.monumentogram.dora.poc.recovery,com.monumentogram.dora.poc.recovery.test"
$connectedSelector = "com.monumentogram.dora.poc.recovery.candidate.RecoveryE36GapiPreflightInstrumentedTest#syntheticFreshReadbackCleanupReplayAndIdentityDenial"
$defaultTimeoutSeconds = 1800
$commandTimeoutSeconds = if ($env:DORA_REC_I3_COMMAND_TIMEOUT_SECONDS) { [int]$env:DORA_REC_I3_COMMAND_TIMEOUT_SECONDS } else { $defaultTimeoutSeconds }
$gitPath = if ($env:DORA_REC_I3_GIT_PATH) { $env:DORA_REC_I3_GIT_PATH } else { "git.exe" }
$pythonPath = if ($env:DORA_REC_I3_PYTHON_PATH) { $env:DORA_REC_I3_PYTHON_PATH } else { "python.exe" }
$repositoryFull = [System.IO.Path]::GetFullPath($Repository)
$evidenceFull = [System.IO.Path]::GetFullPath($EvidenceBase)
$stagingFull = [System.IO.Path]::GetFullPath($StagingRoot)
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if ($null -eq $sdkRoot) { $sdkRoot = "" }
$adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adbPath)) { $adbPath = Join-Path $sdkRoot "platform-tools\adb.cmd" }
$emulatorPath = Join-Path $sdkRoot "emulator\emulator.exe"
if (-not (Test-Path -LiteralPath $emulatorPath)) { $emulatorPath = Join-Path $sdkRoot "emulator\emulator.cmd" }
$gradlePath = Join-Path $repositoryFull "android\gradlew.bat"
$governancePath = Join-Path $repositoryFull "tools\validate_poc_recovery_governance.py"
$preservationPath = Join-Path $repositoryFull "tools\rec_i3_preserve_and_cleanup.ps1"
$dependencyPath = Join-Path $repositoryFull "tools\verify_poc_recovery_dependency_inventory.py"
$buildRoot = Join-Path $repositoryFull "android\poc\recovery\build"
$timestamp = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssfffZ")
$attemptId = "v8-$timestamp"
$rawPath = Join-Path $evidenceFull "REC-I3-V8-RAW-$timestamp"
$preservedPath = Join-Path $evidenceFull "REC-I3-V8-PRESERVED-$timestamp"
$observationPath = Join-Path $evidenceFull "REC-I3-V8-CLEANUP-$timestamp.json"
$ledgerPath = Join-Path $evidenceFull "REC-I3-V8-ATTEMPT-$AcceptedCommit.json"
$preflightPath = Join-Path $rawPath "preflight.json"
$reportPath = Join-Path $evidenceFull "REC-I3-V8-REPORT-$timestamp.json"
$startedEmulator = $false
$exitCode = 0
$primaryFailure = $null
$cleanupResult = $null
$preflightCommands = @()
$ledgerCreated = $false
$deviceIdentityVerified = $false
$ownedEmulatorProcess = $null

function Write-JsonFile([string]$Path, [object]$Value) {
    $parent = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetFullPath($Path))
    [System.IO.Directory]::CreateDirectory($parent) | Out-Null
    [System.IO.File]::WriteAllText(
        [System.IO.Path]::GetFullPath($Path),
        (($Value | ConvertTo-Json -Depth 12) + [Environment]::NewLine),
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Convert-ToPsLiteral([string]$Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-Sha256([string]$Path) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead([System.IO.Path]::GetFullPath($Path))
        try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace("-", "").ToLowerInvariant() }
        finally { $stream.Dispose() }
    } finally { $sha.Dispose() }
}

function Stop-ProcessTreeBounded([int]$ProcessId) {
    try {
        $killer = Start-Process -FilePath "taskkill.exe" -ArgumentList @("/PID", "$ProcessId", "/T", "/F") -PassThru -WindowStyle Hidden
        if (-not $killer.WaitForExit(10000)) { try { $killer.Kill() } catch {} }
    } catch {
        try { Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue } catch {}
    }
}

function Invoke-BoundedCommand(
    [string]$Name,
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [string]$LogPath,
    [int]$TimeoutSeconds = $commandTimeoutSeconds
) {
    $stderrPath = "$LogPath.stderr"
    $argumentText = "@(" + (($Arguments | ForEach-Object { Convert-ToPsLiteral $_ }) -join ",") + ")"
    $scriptText = "`$ErrorActionPreference='Continue'; `$ProgressPreference='SilentlyContinue'; Set-Location -LiteralPath $(Convert-ToPsLiteral $WorkingDirectory); & $(Convert-ToPsLiteral $FilePath) $argumentText; if (`$null -eq `$LASTEXITCODE) { exit 0 } else { exit `$LASTEXITCODE }"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($scriptText))
    $hostPowerShell = (Get-Process -Id $PID).Path
    $process = Start-Process -FilePath $hostPowerShell -ArgumentList @("-NoProfile", "-NonInteractive", "-EncodedCommand", $encoded) -RedirectStandardOutput $LogPath -RedirectStandardError $stderrPath -PassThru -WindowStyle Hidden
    $processHandle = $process.Handle
    $completed = $process.WaitForExit([Math]::Max(1, $TimeoutSeconds) * 1000)
    if (-not $completed) {
        Stop-ProcessTreeBounded $process.Id
        $process.WaitForExit(10000) | Out-Null
    } else {
        $process.WaitForExit()
    }
    $process.Refresh()
    [string]$stdout = if (Test-Path -LiteralPath $LogPath) { Get-Content -Raw -LiteralPath $LogPath } else { "" }
    [string]$stderr = if (Test-Path -LiteralPath $stderrPath) { Get-Content -Raw -LiteralPath $stderrPath } else { "" }
    if ($null -eq $stdout) { $stdout = "" }
    if ($null -eq $stderr) { $stderr = "" }
    if ($stderr) { Add-Content -LiteralPath $LogPath -Value $stderr -Encoding UTF8 }
    return [ordered]@{
        name = $Name
        executable = $FilePath
        arguments = @($Arguments)
        exitCode = if ($completed) { [int]$process.ExitCode } else { $null }
        timedOut = -not $completed
        output = $stdout.Trim()
        errorOutput = $stderr.Trim()
        logPath = $LogPath
    }
}

function Save-Preflight([object]$Record) {
    $script:preflightCommands += $Record
    Write-JsonFile $preflightPath ([ordered]@{ schema = "DORA_REC_I3_V8_PREFLIGHT_V1"; commands = $script:preflightCommands })
}

function Invoke-Preflight([string]$Name, [string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory = $repositoryFull) {
    $record = Invoke-BoundedCommand $Name $FilePath $Arguments $WorkingDirectory (Join-Path $rawPath "$Name.log")
    Save-Preflight $record
    if ($record.timedOut) { throw "PREFLIGHT_TIMEOUT:$Name" }
    if ($record.exitCode -ne 0) { throw "PREFLIGHT_FAILED:${Name}:$($record.exitCode)" }
    return $record
}

function Write-LedgerCreateNew([object]$Value) {
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes((($Value | ConvertTo-Json -Depth 10) + [Environment]::NewLine))
    $stream = [System.IO.File]::Open($ledgerPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
}

try {
    if ($AcceptedCommit -notmatch '^[0-9a-f]{40}$' -or $AcceptedTree -notmatch '^[0-9a-f]{40}$') { throw "ACCEPTED_IDENTITY_INVALID" }
    if ($Serial -notmatch '^emulator-([0-9]{4,5})$') { throw "SERIAL_INVALID" }
    $emulatorPort = [int]$Matches[1]
    if ($emulatorPort -lt 1024 -or $emulatorPort -gt 65534 -or ($emulatorPort % 2) -ne 0) { throw "SERIAL_PORT_INVALID" }
    if ($AvdName -notmatch '^[A-Za-z0-9._-]+$') { throw "AVD_NAME_INVALID" }
    if ($commandTimeoutSeconds -lt 1 -or $commandTimeoutSeconds -gt 7200) { throw "COMMAND_TIMEOUT_INVALID" }
    if (-not (Test-Path -LiteralPath $repositoryFull -PathType Container)) { throw "REPOSITORY_MISSING" }
    foreach ($required in @($adbPath, $emulatorPath, $gradlePath, $governancePath, $dependencyPath, $preservationPath)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "REQUIRED_TOOL_MISSING:$required" }
    }
    [System.IO.Directory]::CreateDirectory($evidenceFull) | Out-Null
    [System.IO.Directory]::CreateDirectory($stagingFull) | Out-Null
    if (Test-Path -LiteralPath $ledgerPath) { throw "ATTEMPT_LEDGER_ALREADY_EXISTS" }
    [System.IO.Directory]::CreateDirectory($rawPath) | Out-Null

    $head = Invoke-Preflight "git-head" $gitPath @("rev-parse", "HEAD")
    if ($head.output.Trim() -ne $AcceptedCommit) { throw "HEAD_IDENTITY_MISMATCH" }
    $tree = Invoke-Preflight "git-tree" $gitPath @("show", "-s", "--format=%T", "HEAD")
    if ($tree.output.Trim() -ne $AcceptedTree) { throw "TREE_IDENTITY_MISMATCH" }
    $status = Invoke-Preflight "git-status" $gitPath @("status", "--porcelain=v1", "--untracked-files=all")
    if (-not [string]::IsNullOrWhiteSpace($status.output)) { throw "CHECKOUT_NOT_CLEAN" }

    $devices = Invoke-Preflight "adb-devices-initial" $adbPath @("-s", $Serial, "devices")
    $online = @($devices.output -split "`r?`n" | Where-Object { $_ -match '^([^\s]+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($online -notcontains $Serial) {
        $emulatorStdout = Join-Path $rawPath "emulator-stdout.log"
        $emulatorStderr = Join-Path $rawPath "emulator-stderr.log"
        $ownedEmulatorProcess = Start-Process -FilePath $emulatorPath -ArgumentList @("-avd", $AvdName, "-port", "$emulatorPort", "-no-window", "-no-audio", "-no-boot-anim") -RedirectStandardOutput $emulatorStdout -RedirectStandardError $emulatorStderr -PassThru -WindowStyle Hidden
        $startedEmulator = $true
        $bootDeadline = [DateTime]::UtcNow.AddSeconds([Math]::Min($commandTimeoutSeconds, 300))
        do {
            Start-Sleep -Milliseconds 250
            $devices = Invoke-Preflight "adb-devices-wait-$($preflightCommands.Count)" $adbPath @("-s", $Serial, "devices")
            $online = @($devices.output -split "`r?`n" | Where-Object { $_ -match '^([^\s]+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
        } while ($online -notcontains $Serial -and [DateTime]::UtcNow -lt $bootDeadline)
    }
    if ($online -notcontains $Serial) { throw "SERIAL_NOT_ONLINE" }
    if (@($online | Where-Object { $_ -ne $Serial }).Count -ne 0) { throw "EXTRA_ONLINE_DEVICE" }
    $state = Invoke-Preflight "adb-state" $adbPath @("-s", $Serial, "get-state")
    if ($state.output.Trim() -ne "device") { throw "TRANSPORT_NOT_HEALTHY" }
    $bootDeadline = [DateTime]::UtcNow.AddSeconds([Math]::Min($commandTimeoutSeconds, 300))
    do {
        $boot = Invoke-Preflight "adb-boot-$($preflightCommands.Count)" $adbPath @("-s", $Serial, "shell", "getprop", "sys.boot_completed")
        if ($boot.output.Trim() -eq "1") { break }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $bootDeadline)
    if ($boot.output.Trim() -ne "1") { throw "BOOT_NOT_COMPLETE" }
    $sdk = Invoke-Preflight "adb-api" $adbPath @("-s", $Serial, "shell", "getprop", "ro.build.version.sdk")
    if ($sdk.output.Trim() -ne "36") { throw "E36_API_MISMATCH" }
    $abi = Invoke-Preflight "adb-abi" $adbPath @("-s", $Serial, "shell", "getprop", "ro.product.cpu.abi")
    if ($abi.output.Trim() -ne "x86_64") { throw "E36_ABI_MISMATCH" }
    $fingerprint = Invoke-Preflight "adb-fingerprint" $adbPath @("-s", $Serial, "shell", "getprop", "ro.build.fingerprint")
    if ($fingerprint.output.Trim() -ne $expectedFingerprint) { throw "E36_FINGERPRINT_MISMATCH" }
    $avd = Invoke-Preflight "adb-avd-name" $adbPath @("-s", $Serial, "emu", "avd", "name")
    if (($avd.output -split "`r?`n")[0].Trim() -ne $AvdName) { throw "AVD_IDENTITY_MISMATCH" }
    $deviceIdentityVerified = $true
    [void](Invoke-Preflight "governance" $pythonPath @($governancePath, "--self-test"))
    [void](Invoke-Preflight "dependencies-online" $pythonPath @($dependencyPath, "--online"))
    [void](Invoke-Preflight "assemble-online" $gradlePath @("--no-daemon", "--no-configuration-cache", ":poc:recovery:assembleDebug", ":poc:recovery:assembleDebugAndroidTest") (Join-Path $repositoryFull "android"))
    [void](Invoke-Preflight "assemble-offline" $gradlePath @("--offline", "--no-daemon", "--no-configuration-cache", ":poc:recovery:assembleDebug", ":poc:recovery:assembleDebugAndroidTest") (Join-Path $repositoryFull "android"))
    $apkRecords = @()
    if (Test-Path -LiteralPath (Join-Path $buildRoot "outputs\apk")) {
        foreach ($apk in Get-ChildItem -LiteralPath (Join-Path $buildRoot "outputs\apk") -Filter *.apk -Recurse) {
            $apkRecords += [ordered]@{ path = $apk.FullName; bytes = $apk.Length; sha256 = Get-Sha256 $apk.FullName }
        }
    }
    if ($apkRecords.Count -lt 2) { throw "EXPECTED_TARGET_AND_TEST_APKS_MISSING" }
    Write-JsonFile (Join-Path $rawPath "apk-digests.json") ([ordered]@{ schema = "DORA_REC_I3_V8_APK_DIGESTS_V1"; apks = $apkRecords })
    foreach ($package in $packageNames.Split(',')) {
        $label = $package.Replace('.', '-')
        $pathRecord = Invoke-BoundedCommand "package-path-$label" $adbPath @("-s", $Serial, "shell", "pm", "path", $package) $repositoryFull (Join-Path $rawPath "package-path-$label.log")
        Save-Preflight $pathRecord
        $listRecord = Invoke-BoundedCommand "package-list-$label" $adbPath @("-s", $Serial, "shell", "pm", "list", "packages") $repositoryFull (Join-Path $rawPath "package-list-$label.log")
        Save-Preflight $listRecord
        $listed = @($listRecord.output -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        $pathAbsent = $pathRecord.exitCode -in @(0, 1) -and [string]::IsNullOrWhiteSpace($pathRecord.output)
        if ($pathRecord.timedOut -or $listRecord.timedOut -or $listRecord.exitCode -ne 0 -or -not $pathAbsent -or $listed -contains "package:$package") {
            throw "PACKAGE_ABSENCE_UNVERIFIED:$package"
        }
    }
    [void](Invoke-Preflight "logcat-clear" $adbPath @("-s", $Serial, "logcat", "-c"))

    $ledger = [ordered]@{
        schema = "DORA_REC_I3_V8_ATTEMPT_LEDGER_V1"
        acceptedCommit = $AcceptedCommit
        acceptedTree = $AcceptedTree
        serial = $Serial
        avdName = $AvdName
        state = "ATTEMPT_CONSUMED_EXECUTION_UNKNOWN"
        launchRecord = [ordered]@{ createdUtc = [DateTime]::UtcNow.ToString("o"); instrumentationAttemptCount = 1 }
        completion = $null
    }
    Write-LedgerCreateNew $ledger
    $ledgerCreated = $true
    $env:ANDROID_SERIAL = $Serial
    $gradleArguments = @(
        "--offline", "--no-daemon", "--no-configuration-cache",
        ":poc:recovery:connectedDebugAndroidTest",
        "--serial", $Serial,
        "-Pandroid.testInstrumentationRunnerArguments.class=$connectedSelector",
        "-Pandroid.testInstrumentationRunnerArguments.pocRecoveryE36GapiPreflight=true",
        "-Pandroid.testInstrumentationRunnerArguments.recoveryHarnessRevision=$AcceptedCommit"
    )
    $gradleResult = Invoke-BoundedCommand "connected-gradle" $gradlePath $gradleArguments (Join-Path $repositoryFull "android") (Join-Path $rawPath "connected-gradle.log")
    [void](Invoke-BoundedCommand "logcat-dump" $adbPath @("-s", $Serial, "logcat", "-d", "-v", "threadtime") $repositoryFull (Join-Path $rawPath "logcat-final.log") 60)
    $ledger.completion = [ordered]@{
        observedUtc = [DateTime]::UtcNow.ToString("o")
        gradleExitCode = $gradleResult.exitCode
        timedOut = $gradleResult.timedOut
        firstCheckpointDiagnostic = @($gradleResult.output -split "`r?`n" | Where-Object { $_ -match 'INSTRUMENTATION_CHECKPOINT_INSERT' } | Select-Object -First 1)
    }
    if (-not $gradleResult.timedOut -and $gradleResult.exitCode -eq 0) { $ledger.state = "ATTEMPT_CONSUMED_COMPLETED" }
    Write-JsonFile $ledgerPath $ledger
    if ($gradleResult.timedOut) { throw "CONNECTED_GRADLE_TIMEOUT" }
    if ($gradleResult.exitCode -ne 0) { throw "CONNECTED_GRADLE_FAILED:$($gradleResult.exitCode)" }
} catch {
    $exitCode = 1
    $primaryFailure = "$($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)"
} finally {
    if ($deviceIdentityVerified -and (Test-Path -LiteralPath $rawPath -PathType Container) -and (Test-Path -LiteralPath $buildRoot -PathType Container)) {
        $metadataDestination = Join-Path $buildRoot "rec-i3-v8-run-metadata-$attemptId"
        [System.IO.Directory]::CreateDirectory($metadataDestination) | Out-Null
        $metadataCopy = Invoke-BoundedCommand "metadata-copy" "robocopy.exe" @($rawPath, $metadataDestination, "/E", "/COPY:DAT", "/DCOPY:DAT", "/R:2", "/W:1", "/XJ", "/NJH", "/NJS", "/NFL", "/NDL") $repositoryFull (Join-Path $evidenceFull "REC-I3-V8-METADATA-COPY-$timestamp.log") 120
        if ($metadataCopy.timedOut -or $null -eq $metadataCopy.exitCode -or $metadataCopy.exitCode -gt 7) {
            if ($exitCode -eq 0) { $exitCode = 1; $primaryFailure = "METADATA_COPY_FAILED" }
        }
        $preserveArguments = @(
            "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $preservationPath,
            "-SourcePath", $buildRoot, "-EvidenceRoot", $preservedPath,
            "-StagingRoot", $stagingFull, "-ObservationPath", $observationPath,
            "-AdbPath", $adbPath, "-PackageNames", $packageNames,
            "-AttemptId", $attemptId, "-Serial", $Serial
        )
        if ($startedEmulator) { $preserveArguments += "-StopEmulator" }
        $cleanupResult = Invoke-BoundedCommand "preservation-cleanup" (Get-Process -Id $PID).Path $preserveArguments $repositoryFull (Join-Path $evidenceFull "REC-I3-V8-PRESERVATION-$timestamp.log")
        if ($cleanupResult.timedOut -or $cleanupResult.exitCode -ne 0) {
            if ($exitCode -eq 0) { $exitCode = 1; $primaryFailure = "PRESERVATION_OR_CLEANUP_FAILED" }
        }
    } elseif ($null -ne $ownedEmulatorProcess) {
        Stop-ProcessTreeBounded $ownedEmulatorProcess.Id
    }
    try {
        if ($env:DORA_REC_I3_INJECT_REPORT_FAILURE -eq "1") { throw "INJECTED_REPORTING_FAILURE" }
        Write-JsonFile $reportPath ([ordered]@{
            schema = "DORA_REC_I3_V8_HOST_RUN_REPORT_V1"
            acceptedCommit = $AcceptedCommit
            acceptedTree = $AcceptedTree
            attemptId = $attemptId
            ledgerCreated = $ledgerCreated
            primaryFailure = $primaryFailure
            cleanupExitCode = if ($null -ne $cleanupResult) { $cleanupResult.exitCode } else { $null }
            exitCode = $exitCode
        })
    } catch {
        $reportFailure = $_.Exception.Message
        [System.IO.File]::WriteAllText((Join-Path $evidenceFull "REPORTING_FAILURE.txt"), $reportFailure + [Environment]::NewLine)
        if ($exitCode -eq 0) { $exitCode = 1; $primaryFailure = $reportFailure }
    }
}

if ($exitCode -ne 0) { Write-Error $primaryFailure; exit $exitCode }
Write-Output "REC_I3_V8_HOST_RUN_COMPLETE $reportPath"
