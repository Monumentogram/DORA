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
$copyCommands = @()
$copyEvidenceDirectory = $null
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

Import-Module (Join-Path $PSScriptRoot 'rec_i3_owned_process.psm1') -Force

function Invoke-ExternalObserved([string]$FilePath, [string[]]$Arguments, [string]$LogPath = "") {
    $stdoutPath = $LogPath
    $stderrPath = $null
    $nativeResultPath = $null
    $native = [ordered]@{ exitCode = $null; launchFailure = $null }
    $process = $null
    $completed = $false
    $timedOut = $false
    $wrapperExitCode = $null
    $cleanupResult = $null
    try {
        if (-not $stdoutPath) { $stdoutPath = [System.IO.Path]::GetTempFileName() }
        $stderrPath = "$stdoutPath.stderr"
        $nativeResultPath = "$stdoutPath.native.json"
        if ((Test-Path -LiteralPath $stdoutPath -PathType Container) -or (Test-Path -LiteralPath $stderrPath -PathType Container)) { throw "COMMAND_LOG_PATH_IS_DIRECTORY:$stdoutPath" }
        $argumentText = "@(" + (($Arguments | ForEach-Object { Convert-ToPsLiteral $_ }) -join ",") + ")"
        # Native stderr is not a launch exception in either supported PowerShell host.
        $scriptText = @"
`$ErrorActionPreference='Stop'; `$ProgressPreference='SilentlyContinue'
`$PSNativeCommandUseErrorActionPreference=`$false
`$native=[ordered]@{exitCode=`$null; launchFailure=`$null}
try {
    `$command=Get-Command -Name $(Convert-ToPsLiteral $FilePath) -CommandType Application -ErrorAction Stop
    `$LASTEXITCODE=`$null; `$Error.Clear(); `$ErrorActionPreference='Continue'
    & `$command $argumentText
    `$native.exitCode=`$LASTEXITCODE
    `$invokeErrors=@(`$Error | Where-Object { `$_.FullyQualifiedErrorId -notmatch '^NativeCommandError' })
    `$ErrorActionPreference='Stop'
    if (`$invokeErrors.Count) { throw ((`$invokeErrors | Out-String).Trim()) }
    if (`$null -eq `$native.exitCode) { throw 'NATIVE_EXIT_NOT_OBSERVED' }
} catch {
    `$native.launchFailure=`$_.ToString()
    [Console]::Error.WriteLine(`$native.launchFailure)
}
[IO.File]::WriteAllText($(Convert-ToPsLiteral $nativeResultPath), (`$native | ConvertTo-Json), [Text.UTF8Encoding]::new(`$false))
if (`$native.launchFailure) { exit 125 }
exit `$native.exitCode
"@
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($scriptText))
        $hostPowerShell = (Get-Process -Id $PID).Path
        $process = Start-Process -FilePath $hostPowerShell -ArgumentList @("-NoProfile", "-NonInteractive", "-EncodedCommand", $encoded) -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru -WindowStyle Hidden
        $processBinding = New-RecI3OwnedProcessBinding $process
        $completed = $process.WaitForExit([Math]::Max(1, $externalTimeoutSeconds) * 1000)
        $timedOut = -not $completed
        if ($completed) {
            $process.WaitForExit()
            $process.Refresh()
            $wrapperExitCode = $process.ExitCode
            if (-not (Test-Path -LiteralPath $nativeResultPath -PathType Leaf)) { throw "NATIVE_OBSERVATION_MISSING:wrapperExit=$wrapperExitCode" }
            $native = Get-Content -Raw -Encoding UTF8 -LiteralPath $nativeResultPath | ConvertFrom-Json
        }
    } catch {
        $native.launchFailure = $_.ToString()
    } finally {
        if ($null -ne $process -and -not $completed) {
            $cleanupResult = Stop-RecI3OwnedProcessClosure $processBinding 1000 10000
            if (-not $cleanupResult.cleanupCertain) { $native.launchFailure = 'OWNED_WRAPPER_CLEANUP_UNCERTAIN' }
        }
    }
    [string]$stdout = if ($stdoutPath -and (Test-Path -LiteralPath $stdoutPath -PathType Leaf)) { Get-Content -Raw -LiteralPath $stdoutPath } else { "" }
    [string]$stderr = if ($stderrPath -and (Test-Path -LiteralPath $stderrPath -PathType Leaf)) { Get-Content -Raw -LiteralPath $stderrPath } else { "" }
    if ($null -eq $stdout) { $stdout = "" }
    if ($null -eq $stderr) { $stderr = "" }
    $wrapperProcessId = if ($null -ne $process) { $process.Id } else { $null }
    $wrapperExited = ($null -eq $process -or $process.HasExited)
    if ($null -ne $process) { $process.Dispose() }
    return [ordered]@{
        executable = $FilePath
        arguments = @($Arguments)
        exitCode = $native.exitCode
        wrapperExitCode = $wrapperExitCode
        wrapperProcessId = $wrapperProcessId
        wrapperExited = $wrapperExited
        ownedCleanup = $cleanupResult
        launchFailure = $native.launchFailure
        timedOut = $timedOut
        output = $stdout.Trim()
        errorOutput = $stderr.Trim()
        logPath = $stdoutPath
        stderrPath = $stderrPath
        nativeResultPath = $nativeResultPath
    }
}

function Write-CopyReceipt([string]$Path, [object]$Value) {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes((($Value | ConvertTo-Json -Depth 8) + [Environment]::NewLine))
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
}

function Invoke-Robocopy([string]$Name, [string]$From, [string]$To) {
    $observed = Invoke-ExternalObserved $robocopyPath @($From, $To, "/E", "/COPY:DAT", "/DCOPY:DAT", "/R:2", "/W:1", "/XJ", "/NJH", "/NJS", "/NFL", "/NDL") (Join-Path $copyEvidenceDirectory "$Name.log")
    $observed.name = $Name
    $observed.attemptId = $AttemptId
    $observed.receiptPath = Join-Path $copyEvidenceDirectory "$Name.json"
    $observed.receiptWriteFailure = $null
    $script:copyCommands += $observed
    try {
        Write-CopyReceipt $observed.receiptPath $observed
    } catch {
        $observed.receiptWriteFailure = $_.ToString()
        $script:reportingFailure = "COPY_RECEIPT_WRITE_FAILED:$($observed.receiptWriteFailure)"
        # The main cleanup observation also retains this full result if a receipt destination is blocked.
        $observed.receiptPath = Join-Path $copyEvidenceDirectory "$Name-fallback-$([Guid]::NewGuid().ToString('N')).json"
        try { Write-CopyReceipt $observed.receiptPath $observed }
        catch { $script:reportingFailure += "; COPY_RECEIPT_FALLBACK_FAILED:$($_.ToString())" }
    }
    if ($observed.launchFailure) { throw "ROBOCOPY_LAUNCH_FAILED:$($observed.launchFailure)" }
    if ($observed.timedOut) { throw "ROBOCOPY_TIMEOUT" }
    if ($null -eq $observed.exitCode -or $observed.exitCode -lt 0 -or $observed.exitCode -gt 7) {
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
    # Allocate receipts inside the outer try so allocation failure can never skip finally cleanup.
    $receiptName = "rec-i3-copy-$AttemptId-$([Guid]::NewGuid().ToString('N'))"
    try {
        $copyEvidenceDirectory = Join-Path ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($ObservationPath))) $receiptName
        foreach ($copyRoot in @($sourceFull, $evidenceFull, $stagingFull)) {
            if ($copyEvidenceDirectory.StartsWith($copyRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "COPY_RECEIPTS_INSIDE_COPIED_TREE" }
        }
        New-Item -ItemType Directory -Path $copyEvidenceDirectory -ErrorAction Stop | Out-Null
    } catch {
        $reportingFailure = "COPY_RECEIPT_ALLOCATION_FAILED:$($_.ToString())"
        $copyEvidenceDirectory = Join-Path ([IO.Path]::GetTempPath()) $receiptName
        foreach ($copyRoot in @($sourceFull, $evidenceFull, $stagingFull)) {
            if ($copyEvidenceDirectory.StartsWith($copyRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "COPY_RECEIPT_FALLBACK_INSIDE_COPIED_TREE" }
        }
        New-Item -ItemType Directory -Path $copyEvidenceDirectory -ErrorAction Stop | Out-Null
    }
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
    [void](Invoke-Robocopy "source-to-stage" $sourceFull $resolvedStagingDirectory)
    if ($InjectCopyFailure -or $InjectStageFailure) { throw "INJECTED_STAGE_FAILURE" }
    if ([System.IO.Directory]::Exists((Convert-ToExtendedPath $evidenceFull))) {
        $existingEvidence = @(Get-RelativeFiles $evidenceFull)
        if ($existingEvidence.Count -ne 0) { throw "EVIDENCE_ROOT_NOT_EMPTY" }
    } else {
        [System.IO.Directory]::CreateDirectory((Convert-ToExtendedPath $evidenceFull)) | Out-Null
    }
    [void](Invoke-Robocopy "stage-to-evidence" $resolvedStagingDirectory $evidenceFull)
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
        copyCommands = $copyCommands
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
            $record.forceStopCommand = $forceStop
            $record.forceStopExitCode = $forceStop.exitCode
            $record.forceStopTimedOut = $forceStop.timedOut
            $record.forceStopOutput = $forceStop.output
            $record.forceStopErrorOutput = $forceStop.errorOutput
        } catch { $record.forceStopOutput = $_.Exception.GetType().Name }
        try {
            $uninstall = Invoke-AdbObserved @("uninstall", $package)
            $record.uninstallCommand = $uninstall
            $record.uninstallExitCode = $uninstall.exitCode
            $record.uninstallTimedOut = $uninstall.timedOut
            $record.uninstallOutput = $uninstall.output
            $record.uninstallErrorOutput = $uninstall.errorOutput
        } catch { $record.uninstallOutput = $_.Exception.GetType().Name }
        try {
            $transport = Invoke-AdbObserved @("get-state")
            $record.transportProbeCommand = $transport
            $record.transportProbeExitCode = $transport.exitCode
            $record.transportProbeTimedOut = $transport.timedOut
            $record.transportProbeOutput = $transport.output
            $record.transportProbeErrorOutput = $transport.errorOutput
        } catch { $record.transportProbeOutput = $_.Exception.GetType().Name }
        try {
            $postUninstall = Invoke-AdbObserved @("shell", "pm", "path", $package)
            $record.postUninstallQueryCommand = $postUninstall
            $record.postUninstallQueryExitCode = $postUninstall.exitCode
            $record.postUninstallQueryTimedOut = $postUninstall.timedOut
            $record.postUninstallQueryOutput = $postUninstall.output
            $record.postUninstallQueryErrorOutput = $postUninstall.errorOutput
        } catch { $record.postUninstallQueryOutput = $_.Exception.GetType().Name }
        try {
            $packageList = Invoke-AdbObserved @("shell", "pm", "list", "packages")
            $record.packageListCommand = $packageList
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
            $emulatorCleanup.command = $emulator
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
        copyCommands = $copyCommands
        copyEvidenceDirectory = $copyEvidenceDirectory
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
        $reportingFailure = "$reportingFailure; OBSERVATION_WRITE_FAILED:$($_.Exception.Message)"
        $observation.reportingFailure = $reportingFailure
        if ($exitCode -eq 0) { $exitCode = 1 }
        if ($copyEvidenceDirectory) {
            try {
                $fallbackObservationPath = Join-Path $copyEvidenceDirectory "cleanup-observation-$([Guid]::NewGuid().ToString('N')).json"
                Write-CopyReceipt $fallbackObservationPath $observation
                Write-Warning "CLEANUP_OBSERVATION_RELOCATED:$fallbackObservationPath"
            } catch { Write-Warning "CLEANUP_OBSERVATION_FALLBACK_FAILED:$($_.ToString())" }
        }
    }
    if ($null -ne $cleanupFailure -and $exitCode -eq 0) { $exitCode = 2 }
    if ($null -ne $reportingFailure -and $exitCode -eq 0) { $exitCode = 1 }
}

if ($exitCode -ne 0) {
    $reportedFailure = if ($null -ne $copyFailure) { $copyFailure } elseif ($null -ne $cleanupFailure) { $cleanupFailure } else { $reportingFailure }
    Write-Error $reportedFailure
    exit $exitCode
}
Write-Output "PRESERVATION_COMPLETE $EvidenceRoot"
