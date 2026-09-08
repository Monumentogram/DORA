[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SourcePath,
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [Parameter(Mandatory = $true)][string]$StagingRoot,
    [Parameter(Mandatory = $true)][string]$ObservationPath,
    [Parameter(Mandatory = $true)][string]$AdbPath,
    [Parameter(Mandatory = $true)][string]$PackageNames,
    [switch]$StopEmulator,
    [switch]$InjectCopyFailure
)

$ErrorActionPreference = "Stop"
$copySucceeded = $false
$copyFailure = $null
$cleanupFailure = $null
$exitCode = 0
$stagingDirectory = $null
$packageCleanup = @()
$emulatorCleanup = [ordered]@{ attempted = $false; exitCode = $null; output = $null }

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

function Invoke-Robocopy([string]$From, [string]$To) {
    & robocopy.exe $From $To /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /XJ /NJH /NJS /NFL /NDL | Out-Null
    $result = $LASTEXITCODE
    if ($result -lt 0 -or $result -gt 7) {
        throw "ROBOCOPY_FAILED:$result"
    }
    return $result
}

function Invoke-AdbObserved([string[]]$Arguments) {
    $output = (& $AdbPath @Arguments 2>&1 | Out-String).Trim()
    return [ordered]@{ exitCode = $LASTEXITCODE; output = $output }
}

try {
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
    $stagingDirectory = Join-Path $stagingFull "rec-i3-preservation"
    $resolvedStagingDirectory = [System.IO.Path]::GetFullPath($stagingDirectory)
    $stagingPrefix = $stagingFull.TrimEnd('\') + '\'
    if (-not $resolvedStagingDirectory.StartsWith($stagingPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "STAGING_DIRECTORY_ESCAPE"
    }
    if ([System.IO.Directory]::Exists((Convert-ToExtendedPath $resolvedStagingDirectory))) {
        Remove-Item -LiteralPath (Convert-ToExtendedPath $resolvedStagingDirectory) -Recurse -Force
    }
    [System.IO.Directory]::CreateDirectory((Convert-ToExtendedPath $resolvedStagingDirectory)) | Out-Null

    [void](Invoke-Robocopy $sourceFull $resolvedStagingDirectory)
    if ($InjectCopyFailure) { throw "INJECTED_COPY_FAILURE" }
    [System.IO.Directory]::CreateDirectory((Convert-ToExtendedPath $evidenceFull)) | Out-Null
    [void](Invoke-Robocopy $resolvedStagingDirectory $evidenceFull)

    $files = @()
    $stageExtended = Convert-ToExtendedPath $resolvedStagingDirectory
    $stageExtendedPrefix = $stageExtended.TrimEnd('\') + '\'
    foreach ($stagedExtended in [System.IO.Directory]::EnumerateFiles($stageExtended, "*", [System.IO.SearchOption]::AllDirectories)) {
        if (-not $stagedExtended.StartsWith($stageExtendedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "STAGED_FILE_ESCAPE"
        }
        $relative = $stagedExtended.Substring($stageExtendedPrefix.Length)
        $relativePortable = $relative.Replace('\', '/')
        $stagedPath = [System.IO.Path]::Combine($resolvedStagingDirectory, $relative)
        $originalPath = [System.IO.Path]::Combine($sourceFull, $relative)
        $preservedPath = [System.IO.Path]::Combine($evidenceFull, $relative)
        $stageHash = Get-Sha256 $stagedPath
        $preservedHash = Get-Sha256 $preservedPath
        if ($stageHash -ne $preservedHash) { throw "PRESERVED_HASH_MISMATCH:$relativePortable" }
        $files += [ordered]@{
            relativePath = $relativePortable
            originalPath = $originalPath
            stagedPath = $stagedPath
            preservedPath = $preservedPath
            bytes = [System.IO.FileInfo]::new((Convert-ToExtendedPath $stagedPath)).Length
            sha256 = $stageHash
        }
    }
    $manifest = [ordered]@{
        schema = "DORA_REC_I3_PRESERVATION_V1"
        sourcePath = $sourceFull
        evidenceRoot = $evidenceFull
        stagingPath = $resolvedStagingDirectory
        copySucceeded = $true
        files = $files
    }
    $manifestJson = $manifest | ConvertTo-Json -Depth 8
    $manifestPath = [System.IO.Path]::Combine($evidenceFull, "PRESERVATION_MANIFEST.json")
    [System.IO.File]::WriteAllText(
        (Convert-ToExtendedPath $manifestPath),
        $manifestJson + [Environment]::NewLine,
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
            forceStopOutput = $null
            uninstallAttempted = $true
            uninstallExitCode = $null
            uninstallOutput = $null
            postUninstallQueryAttempted = $true
            postUninstallQueryExitCode = $null
            postUninstallQueryOutput = $null
            packageAbsentObserved = $false
        }
        try {
            $forceStop = Invoke-AdbObserved @("shell", "am", "force-stop", $package)
            $record.forceStopExitCode = $forceStop.exitCode
            $record.forceStopOutput = $forceStop.output
        } catch {
            $record.forceStopOutput = $_.Exception.GetType().Name
        }
        try {
            $uninstall = Invoke-AdbObserved @("uninstall", $package)
            $record.uninstallExitCode = $uninstall.exitCode
            $record.uninstallOutput = $uninstall.output
        } catch {
            $record.uninstallOutput = $_.Exception.GetType().Name
        }
        try {
            $postUninstall = Invoke-AdbObserved @("shell", "pm", "path", $package)
            $record.postUninstallQueryExitCode = $postUninstall.exitCode
            $record.postUninstallQueryOutput = $postUninstall.output
            $record.packageAbsentObserved =
                ($postUninstall.exitCode -eq 0 -and [string]::IsNullOrWhiteSpace($postUninstall.output))
        } catch {
            $record.postUninstallQueryOutput = $_.Exception.GetType().Name
        }
        if (
            $record.forceStopExitCode -ne 0 -or
            $record.uninstallExitCode -ne 0 -or
            -not $record.packageAbsentObserved
        ) {
            $cleanupFailure = "PACKAGE_CLEANUP_UNVERIFIED"
        }
        $packageCleanup += $record
    }
    if ($StopEmulator) {
        $emulatorCleanup.attempted = $true
        try {
            $emulator = Invoke-AdbObserved @("emu", "kill")
            $emulatorCleanup.exitCode = $emulator.exitCode
            $emulatorCleanup.output = $emulator.output
        } catch {
            $emulatorCleanup.output = $_.Exception.GetType().Name
        }
        if ($emulatorCleanup.exitCode -ne 0) {
            $cleanupFailure = "EMULATOR_CLEANUP_UNVERIFIED"
        }
    }
    $observation = [ordered]@{
        schema = "DORA_REC_I3_CLEANUP_OBSERVATION_V1"
        copySucceeded = $copySucceeded
        copyFailure = $copyFailure
        cleanupFailure = $cleanupFailure
        stagingPath = $stagingDirectory
        stagingRetained = ($null -ne $stagingDirectory -and [System.IO.Directory]::Exists((Convert-ToExtendedPath $stagingDirectory)))
        cleanupAttempted = $true
        packageCleanup = $packageCleanup
        emulatorCleanup = $emulatorCleanup
    }
    $observationFull = [System.IO.Path]::GetFullPath($ObservationPath)
    [System.IO.Directory]::CreateDirectory((Convert-ToExtendedPath ([System.IO.Path]::GetDirectoryName($observationFull)))) | Out-Null
    [System.IO.File]::WriteAllText(
        (Convert-ToExtendedPath $observationFull),
        (($observation | ConvertTo-Json -Depth 8) + [Environment]::NewLine),
        [System.Text.UTF8Encoding]::new($false)
    )
    if ($null -ne $cleanupFailure -and $exitCode -eq 0) { $exitCode = 2 }
}

if ($exitCode -ne 0) {
    $reportedFailure = if ($null -ne $copyFailure) { $copyFailure } else { $cleanupFailure }
    Write-Error $reportedFailure
    exit $exitCode
}
Write-Output "PRESERVATION_COMPLETE $EvidenceRoot"
