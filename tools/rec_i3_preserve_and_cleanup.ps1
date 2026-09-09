[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SourcePath,
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [Parameter(Mandatory = $true)][string]$StagingRoot,
    [Parameter(Mandatory = $true)][string]$ObservationPath,
    [Parameter(Mandatory = $true)][string]$AdbPath,
    [Parameter(Mandatory = $true)][string]$PackageNames,
    [Parameter(Mandatory = $true)][string]$AttemptId,
    [Parameter(Mandatory = $true)][string]$Serial,
    [switch]$StopEmulator,
    [switch]$InjectCopyFailure,
    [switch]$InjectSourceFailure,
    [switch]$InjectStageFailure,
    [switch]$InjectEvidenceFailure,
    [switch]$InjectReportFailure
)

$ErrorActionPreference = "Stop"
$copySucceeded = $false
$copyFailure = $null
$reportingFailure = $null
$cleanupFailure = $null
$exitCode = 0
$stagingDirectory = $null
$packageCleanup = @()
$emulatorCleanup = [ordered]@{ attempted = $false; exitCode = $null; output = $null; errorOutput = $null }
$externalTimeoutSeconds = if ($env:DORA_REC_I3_HELPER_COMMAND_TIMEOUT_SECONDS) { [int]$env:DORA_REC_I3_HELPER_COMMAND_TIMEOUT_SECONDS } else { 120 }
$robocopyPath = if ($env:DORA_REC_I3_ROBOCOPY_PATH) { $env:DORA_REC_I3_ROBOCOPY_PATH } else { "robocopy.exe" }

function Convert-ToExtendedPath([string]$Path) {
    $full = [System.IO.Path]::GetFullPath($Path)
    if ($full.StartsWith("\\?\")) { return $full }
    if ($full.StartsWith("\\")) { return "\\?\UNC\" + $full.Substring(2) }
    return "\\?\" + $full
}

function Get-Sha256([string]$Path) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead((Convert-ToExtendedPath $Path))
        try {
            return ([System.BitConverter]::ToString($sha.ComputeHash($stream))).Replace("-", "").ToLowerInvariant()
        } finally {
            $stream.Dispose()
        }
    } finally {
        $sha.Dispose()
    }
}

function Convert-ToPsLiteral([string]$Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}

function Stop-ProcessTreeBounded([int]$ProcessId) {
    try {
        $killer = Start-Process -FilePath "taskkill.exe" -ArgumentList @("/PID", "$ProcessId", "/T", "/F") -PassThru -WindowStyle Hidden
        if (-not $killer.WaitForExit(10000)) { try { $killer.Kill() } catch {} }
    } catch { try { Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue } catch {} }
}

function Invoke-ExternalObserved([string]$FilePath, [string[]]$Arguments) {
    $stdoutPath = [System.IO.Path]::GetTempFileName()
    $stderrPath = [System.IO.Path]::GetTempFileName()
    try {
        $argumentText = "@(" + (($Arguments | ForEach-Object { Convert-ToPsLiteral $_ }) -join ",") + ")"
        $scriptText = "`$ErrorActionPreference='Continue'; `$ProgressPreference='SilentlyContinue'; & $(Convert-ToPsLiteral $FilePath) $argumentText; if (`$null -eq `$LASTEXITCODE) { exit 0 } else { exit `$LASTEXITCODE }"
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($scriptText))
        $hostPowerShell = (Get-Process -Id $PID).Path
        $process = Start-Process -FilePath $hostPowerShell -ArgumentList @("-NoProfile", "-NonInteractive", "-EncodedCommand", $encoded) -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru -WindowStyle Hidden
        $processHandle = $process.Handle
        $completed = $process.WaitForExit([Math]::Max(1, $externalTimeoutSeconds) * 1000)
        if (-not $completed) {
            Stop-ProcessTreeBounded $process.Id
            $process.WaitForExit(10000) | Out-Null
        } else { $process.WaitForExit() }
        $process.Refresh()
        [string]$stdout = Get-Content -Raw -LiteralPath $stdoutPath
        [string]$stderr = Get-Content -Raw -LiteralPath $stderrPath
        if ($null -eq $stdout) { $stdout = "" }
        if ($null -eq $stderr) { $stderr = "" }
        return [ordered]@{
            exitCode = if ($completed) { [int]$process.ExitCode } else { $null }
            timedOut = -not $completed
            output = $stdout.Trim()
            errorOutput = $stderr.Trim()
        }
    } finally {
        try { [System.IO.File]::Delete($stdoutPath) } catch {}
        try { [System.IO.File]::Delete($stderrPath) } catch {}
    }
}

function Invoke-Robocopy([string]$From, [string]$To) {
    $observed = Invoke-ExternalObserved $robocopyPath @($From, $To, "/E", "/COPY:DAT", "/DCOPY:DAT", "/R:2", "/W:1", "/XJ", "/NJH", "/NJS", "/NFL", "/NDL")
    if ($observed.timedOut) { throw "ROBOCOPY_TIMEOUT" }
    if ($observed.exitCode -lt 0 -or $observed.exitCode -gt 7) {
        throw "ROBOCOPY_FAILED:$($observed.exitCode)"
    }
    return $observed.exitCode
}

function Invoke-AdbObserved([string[]]$Arguments) {
    $bound = @("-s", $Serial) + $Arguments
    return Invoke-ExternalObserved $AdbPath $bound
}

function Get-RelativeFiles([string]$RootPath) {
    $rootExtended = Convert-ToExtendedPath $RootPath
    $rootPrefix = $rootExtended.TrimEnd('\') + '\'
    $records = @()
    foreach ($fileExtended in [System.IO.Directory]::EnumerateFiles($rootExtended, "*", [System.IO.SearchOption]::AllDirectories)) {
        if (-not $fileExtended.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "FILE_ESCAPE"
        }
        $relative = $fileExtended.Substring($rootPrefix.Length).Replace('\', '/')
        $records += [ordered]@{
            relativePath = $relative
            fullPath = [System.IO.Path]::Combine($RootPath, $relative.Replace('/', '\'))
        }
    }
    return @($records | Sort-Object { $_.relativePath })
}

try {
    if ($AttemptId -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$') {
        throw "ATTEMPT_ID_UNSAFE"
    }
    if ([string]::IsNullOrWhiteSpace($Serial)) {
        throw "SERIAL_REQUIRED"
    }
    $sourceFull = [System.IO.Path]::GetFullPath($SourcePath)
    $evidenceFull = [System.IO.Path]::GetFullPath($EvidenceRoot)
    $stagingFull = [System.IO.Path]::GetFullPath($StagingRoot)
    if (-not [System.IO.Directory]::Exists((Convert-ToExtendedPath $sourceFull))) {
        throw "SOURCE_DIRECTORY_MISSING"
    }
    if ($stagingFull.Length -gt 160) {
        throw "STAGING_ROOT_NOT_SHORT"
    }
    [System.IO.Directory]::CreateDirectory((Convert-ToExtendedPath $stagingFull)) | Out-Null
    $stagingDirectory = Join-Path $stagingFull "rec-i3-preservation-$AttemptId"
    $resolvedStagingDirectory = [System.IO.Path]::GetFullPath($stagingDirectory)
    $stagingPrefix = $stagingFull.TrimEnd('\') + '\'
    if (-not $resolvedStagingDirectory.StartsWith($stagingPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "STAGING_DIRECTORY_ESCAPE"
    }
    if ([System.IO.Directory]::Exists((Convert-ToExtendedPath $resolvedStagingDirectory))) {
        throw "STAGING_ATTEMPT_ALREADY_EXISTS"
    }
    [System.IO.Directory]::CreateDirectory((Convert-ToExtendedPath $resolvedStagingDirectory)) | Out-Null

    if ($InjectSourceFailure) { throw "INJECTED_SOURCE_FAILURE" }
    [void](Invoke-Robocopy $sourceFull $resolvedStagingDirectory)
    if ($InjectCopyFailure -or $InjectStageFailure) { throw "INJECTED_STAGE_FAILURE" }
    if ([System.IO.Directory]::Exists((Convert-ToExtendedPath $evidenceFull))) {
        $existingEvidence = @(Get-RelativeFiles $evidenceFull)
        if ($existingEvidence.Count -ne 0) { throw "EVIDENCE_ROOT_NOT_EMPTY" }
    } else {
        [System.IO.Directory]::CreateDirectory((Convert-ToExtendedPath $evidenceFull)) | Out-Null
    }
    [void](Invoke-Robocopy $resolvedStagingDirectory $evidenceFull)
    if ($InjectEvidenceFailure) { throw "INJECTED_EVIDENCE_FAILURE" }

    $sourceFiles = @(Get-RelativeFiles $sourceFull)
    $stagedFiles = @(Get-RelativeFiles $resolvedStagingDirectory)
    $evidenceFiles = @(Get-RelativeFiles $evidenceFull)
    $sourcePaths = @($sourceFiles | ForEach-Object { $_.relativePath })
    $stagedPaths = @($stagedFiles | ForEach-Object { $_.relativePath })
    $evidencePaths = @($evidenceFiles | ForEach-Object { $_.relativePath })
    if (($sourcePaths -join "`n") -ne ($stagedPaths -join "`n") -or ($sourcePaths -join "`n") -ne ($evidencePaths -join "`n")) {
        throw "PRESERVATION_RELATIVE_PATH_SET_MISMATCH"
    }

    $files = @()
    foreach ($relative in $sourcePaths) {
        $nativeRelative = $relative.Replace('/', '\')
        $originalPath = [System.IO.Path]::Combine($sourceFull, $nativeRelative)
        $stagedPath = [System.IO.Path]::Combine($resolvedStagingDirectory, $nativeRelative)
        $preservedPath = [System.IO.Path]::Combine($evidenceFull, $nativeRelative)
        $sourceBytes = [System.IO.FileInfo]::new((Convert-ToExtendedPath $originalPath)).Length
        $stagedBytes = [System.IO.FileInfo]::new((Convert-ToExtendedPath $stagedPath)).Length
        $evidenceBytes = [System.IO.FileInfo]::new((Convert-ToExtendedPath $preservedPath)).Length
        $sourceHash = Get-Sha256 $originalPath
        $stagedHash = Get-Sha256 $stagedPath
        $evidenceHash = Get-Sha256 $preservedPath
        if ($sourceBytes -ne $stagedBytes -or $sourceBytes -ne $evidenceBytes -or $sourceHash -ne $stagedHash -or $sourceHash -ne $evidenceHash) {
            throw "PRESERVATION_CONTENT_MISMATCH:$relative"
        }
        $files += [ordered]@{
            relativePath = $relative
            originalPath = $originalPath
            stagedPath = $stagedPath
            preservedPath = $preservedPath
            sourceBytes = $sourceBytes
            stagedBytes = $stagedBytes
            evidenceBytes = $evidenceBytes
            sourceSha256 = $sourceHash
            stagedSha256 = $stagedHash
            evidenceSha256 = $evidenceHash
        }
    }
    $manifest = [ordered]@{
        schema = "DORA_REC_I3_PRESERVATION_V2"
        attemptId = $AttemptId
        sourcePath = $sourceFull
        evidenceRoot = $evidenceFull
        stagingPath = $resolvedStagingDirectory
        copySucceeded = $true
        sourceRelativePaths = $sourcePaths
        stagedRelativePaths = $stagedPaths
        evidenceRelativePaths = $evidencePaths
        files = $files
    }
    $manifestPath = [System.IO.Path]::Combine($evidenceFull, "PRESERVATION_MANIFEST.json")
    [System.IO.File]::WriteAllText(
        (Convert-ToExtendedPath $manifestPath),
        (($manifest | ConvertTo-Json -Depth 8) + [Environment]::NewLine),
        [System.Text.UTF8Encoding]::new($false)
    )
    $copySucceeded = $true
} catch {
    $exitCode = 1
    $copyFailure = $_.Exception.Message
} finally {
    foreach ($package in ($PackageNames -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
        $record = [ordered]@{
            package = $package
            forceStopAttempted = $true
            forceStopExitCode = $null
            forceStopTimedOut = $false
            forceStopOutput = $null
            forceStopErrorOutput = $null
            uninstallAttempted = $true
            uninstallExitCode = $null
            uninstallTimedOut = $false
            uninstallOutput = $null
            uninstallErrorOutput = $null
            transportProbeAttempted = $true
            transportProbeExitCode = $null
            transportProbeTimedOut = $false
            transportProbeOutput = $null
            transportProbeErrorOutput = $null
            postUninstallQueryAttempted = $true
            postUninstallQueryExitCode = $null
            postUninstallQueryTimedOut = $false
            postUninstallQueryOutput = $null
            postUninstallQueryErrorOutput = $null
            packageListAttempted = $true
            packageListExitCode = $null
            packageListTimedOut = $false
            packageListOutput = $null
            packageListErrorOutput = $null
            packageAbsentObserved = $false
        }
        try {
            $forceStop = Invoke-AdbObserved @("shell", "am", "force-stop", $package)
            $record.forceStopExitCode = $forceStop.exitCode
            $record.forceStopTimedOut = $forceStop.timedOut
            $record.forceStopOutput = $forceStop.output
            $record.forceStopErrorOutput = $forceStop.errorOutput
        } catch { $record.forceStopOutput = $_.Exception.GetType().Name }
        try {
            $uninstall = Invoke-AdbObserved @("uninstall", $package)
            $record.uninstallExitCode = $uninstall.exitCode
            $record.uninstallTimedOut = $uninstall.timedOut
            $record.uninstallOutput = $uninstall.output
            $record.uninstallErrorOutput = $uninstall.errorOutput
        } catch { $record.uninstallOutput = $_.Exception.GetType().Name }
        try {
            $transport = Invoke-AdbObserved @("get-state")
            $record.transportProbeExitCode = $transport.exitCode
            $record.transportProbeTimedOut = $transport.timedOut
            $record.transportProbeOutput = $transport.output
            $record.transportProbeErrorOutput = $transport.errorOutput
        } catch { $record.transportProbeOutput = $_.Exception.GetType().Name }
        try {
            $postUninstall = Invoke-AdbObserved @("shell", "pm", "path", $package)
            $record.postUninstallQueryExitCode = $postUninstall.exitCode
            $record.postUninstallQueryTimedOut = $postUninstall.timedOut
            $record.postUninstallQueryOutput = $postUninstall.output
            $record.postUninstallQueryErrorOutput = $postUninstall.errorOutput
        } catch { $record.postUninstallQueryOutput = $_.Exception.GetType().Name }
        try {
            $packageList = Invoke-AdbObserved @("shell", "pm", "list", "packages")
            $record.packageListExitCode = $packageList.exitCode
            $record.packageListTimedOut = $packageList.timedOut
            $record.packageListOutput = $packageList.output
            $record.packageListErrorOutput = $packageList.errorOutput
        } catch { $record.packageListOutput = $_.Exception.GetType().Name }

        $listedPackages = @($record.packageListOutput -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        $exactPackageListed = $listedPackages -contains "package:$package"
        $pathAbsentForm = $record.postUninstallQueryExitCode -in @(0, 1) -and
            [string]::IsNullOrWhiteSpace($record.postUninstallQueryOutput) -and
            [string]::IsNullOrWhiteSpace($record.postUninstallQueryErrorOutput)
        $record.packageAbsentObserved =
            $record.forceStopExitCode -eq 0 -and
            $record.uninstallExitCode -eq 0 -and
            $record.transportProbeExitCode -eq 0 -and
            $record.transportProbeOutput.Trim() -eq "device" -and
            $record.packageListExitCode -eq 0 -and
            -not $exactPackageListed -and
            $pathAbsentForm
        if (-not $record.packageAbsentObserved) { $cleanupFailure = "PACKAGE_CLEANUP_UNVERIFIED" }
        $packageCleanup += $record
    }
    if ($StopEmulator) {
        $emulatorCleanup.attempted = $true
        $emulatorCleanup.timedOut = $false
        try {
            $emulator = Invoke-AdbObserved @("emu", "kill")
            $emulatorCleanup.exitCode = $emulator.exitCode
            $emulatorCleanup.timedOut = $emulator.timedOut
            $emulatorCleanup.output = $emulator.output
            $emulatorCleanup.errorOutput = $emulator.errorOutput
        } catch { $emulatorCleanup.output = $_.Exception.GetType().Name }
        if ($emulatorCleanup.timedOut -or $emulatorCleanup.exitCode -ne 0) { $cleanupFailure = "EMULATOR_CLEANUP_UNVERIFIED" }
    }
    if ($InjectReportFailure) {
        $reportingFailure = "INJECTED_REPORT_FAILURE"
        if ($exitCode -eq 0) { $exitCode = 1 }
    }
    $observation = [ordered]@{
        schema = "DORA_REC_I3_CLEANUP_OBSERVATION_V2"
        attemptId = $AttemptId
        serial = $Serial
        copySucceeded = $copySucceeded
        copyFailure = $copyFailure
        reportingFailure = $reportingFailure
        cleanupFailure = $cleanupFailure
        stagingPath = $stagingDirectory
        stagingRetained = ($null -ne $stagingDirectory -and [System.IO.Directory]::Exists((Convert-ToExtendedPath $stagingDirectory)))
        cleanupAttempted = $true
        packageCleanup = $packageCleanup
        emulatorCleanup = $emulatorCleanup
    }
    try {
        $observationFull = [System.IO.Path]::GetFullPath($ObservationPath)
        [System.IO.Directory]::CreateDirectory((Convert-ToExtendedPath ([System.IO.Path]::GetDirectoryName($observationFull)))) | Out-Null
        [System.IO.File]::WriteAllText(
            (Convert-ToExtendedPath $observationFull),
            (($observation | ConvertTo-Json -Depth 8) + [Environment]::NewLine),
            [System.Text.UTF8Encoding]::new($false)
        )
    } catch {
        $reportingFailure = $_.Exception.Message
        if ($exitCode -eq 0) { $exitCode = 1 }
    }
    if ($null -ne $cleanupFailure -and $exitCode -eq 0) { $exitCode = 2 }
}

if ($exitCode -ne 0) {
    $reportedFailure = if ($null -ne $copyFailure) { $copyFailure } elseif ($null -ne $cleanupFailure) { $cleanupFailure } else { $reportingFailure }
    Write-Error $reportedFailure
    exit $exitCode
}
Write-Output "PRESERVATION_COMPLETE $EvidenceRoot"
