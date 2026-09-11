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
    $nativeStartedPath = $null
    $nativeResultPath = $null
    $native = [ordered]@{receiptPhase='UNKNOWN';exitCode=$null;rawExitValue=$null;rawExitType=$null;firstFailure=$null;launchFailure=$null;targetStarted=$null;targetProcessId=$null;targetConfiguredPath=$null;targetConfiguredBinding=$null;targetNativeIdentity=$null;targetNativeBinding=$null;targetBindingProof=[ordered]@{stage='NOT_STARTED';verified=$false;error=$null};targetCleanup=$null;targetExitCaptureError=$null;targetDisposeError=$null;targetStdoutComplete=$null;targetStderrComplete=$null;targetStdoutReadError=$null;targetStderrReadError=$null;invocationArtifactPath=$null;invocationArtifactBinding=$null}
    $outerFailureState=[ordered]@{firstFailure=$null;failures=[Collections.Generic.List[string]]::new()}
    $addOuterFailure={param([string]$Message) if(-not[string]::IsNullOrWhiteSpace($Message)){if($null-eq$outerFailureState.firstFailure){$outerFailureState.firstFailure=$Message};$outerFailureState.failures.Add($Message)}}
    $process = $null
    $completed = $false
    $timedOut = $false
    $wrapperExitCode = $null
    $cleanupResult = $null
    $processBinding = $null
    $wrapperStarted = $false
    $invocationArtifactPath=$null;$invocationArtifactBinding=$null;$targetConfiguredPath=$null;$targetConfiguredBinding=$null
    try {
        if (-not $stdoutPath) { $stdoutPath = [System.IO.Path]::GetTempFileName() }
        $stderrPath = "$stdoutPath.stderr"
        $nativeStartedPath = "$stdoutPath.native.started.json"
        $nativeResultPath = "$stdoutPath.native.completed.json"
        if ((Test-Path -LiteralPath $stdoutPath -PathType Container) -or (Test-Path -LiteralPath $stderrPath -PathType Container)) { throw "COMMAND_LOG_PATH_IS_DIRECTORY:$stdoutPath" }
        $resolvedCommand=Get-Command -Name $FilePath -CommandType Application -ErrorAction Stop
        $invocationArtifactPath=[IO.Path]::GetFullPath([string]$resolvedCommand.Source)
        $invocationArtifactBinding=Get-RecI3ExecutableFileBinding $invocationArtifactPath $null 'CONFIGURED_INVOCATION_ARTIFACT'
        if([IO.Path]::GetExtension($invocationArtifactPath)-in@('.cmd','.bat')){$targetConfiguredPath=[IO.Path]::GetFullPath($env:ComSpec);$cmdArguments=@($Arguments|ForEach-Object{$value=[string]$_;if($value-match'[\s"&|<>^]'){'"'+$value.Replace('"','""')+'"'}else{$value}});$targetArguments=@('/d','/s','/c',('""'+$invocationArtifactPath+'" '+($cmdArguments-join' ')+'"'));$targetArgumentLine='/d /s /c ""'+$invocationArtifactPath+'" '+($cmdArguments-join' ')+'"'}else{$targetConfiguredPath=$invocationArtifactPath;$targetArguments=@($Arguments);$targetArgumentLine=ConvertTo-RecI3WindowsCommandLine $targetArguments}
        $targetConfiguredBinding=Get-RecI3ExecutableFileBinding $targetConfiguredPath $null 'CONFIGURED_TARGET'
        $configuredBindingJson=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($targetConfiguredBinding|ConvertTo-Json -Depth 6 -Compress)))
        $artifactBindingJson=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($invocationArtifactBinding|ConvertTo-Json -Depth 6 -Compress)))
        $targetStdoutPath="$stdoutPath.target.stdout";$targetStderrPath="$stdoutPath.target.stderr"
        # Native stderr is not a launch exception in either supported PowerShell host.
        $scriptText = @"
`$ErrorActionPreference='Stop'; `$ProgressPreference='SilentlyContinue'
`$PSNativeCommandUseErrorActionPreference=`$false
Import-Module $(Convert-ToPsLiteral (Join-Path $PSScriptRoot 'rec_i3_owned_process.psm1')) -Force
function Write-NativeCreateOnce([string]`$Path,[object]`$Value){`$bytes=[Text.UTF8Encoding]::new(`$false).GetBytes(((`$Value|ConvertTo-Json -Depth 20)+[Environment]::NewLine));`$stream=[IO.File]::Open(`$Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read);try{`$stream.Write(`$bytes,0,`$bytes.Length);`$stream.Flush(`$true)}finally{`$stream.Dispose()}}
function Set-FirstFailure([string]`$Message){if([string]::IsNullOrWhiteSpace(`$native.firstFailure)){`$native.firstFailure=`$Message};`$native.launchFailure=@(`$native.launchFailure,`$Message|Where-Object{`$_})-join'; '}
`$native=[ordered]@{receiptPhase='INITIALIZED';exitCode=`$null;rawExitValue=`$null;rawExitType=`$null;firstFailure=`$null;launchFailure=`$null;targetStarted=`$null;targetProcessId=`$null;targetConfiguredPath=$(Convert-ToPsLiteral $targetConfiguredPath);targetConfiguredBinding=`$null;targetNativeIdentity=`$null;targetNativeBinding=`$null;targetBindingProof=[ordered]@{stage='NOT_STARTED';verified=`$false;error=`$null};targetCleanup=`$null;targetExitCaptureError=`$null;targetDisposeError=`$null;targetStdoutComplete=`$null;targetStderrComplete=`$null;targetStdoutReadError=`$null;targetStderrReadError=`$null;invocationArtifactPath=$(Convert-ToPsLiteral $invocationArtifactPath);invocationArtifactBinding=`$null;targetStdoutPath=$(Convert-ToPsLiteral $targetStdoutPath);targetStderrPath=$(Convert-ToPsLiteral $targetStderrPath)}
`$target=`$null;`$targetBinding=`$null
try {
    `$native.targetConfiguredBinding=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($(Convert-ToPsLiteral $configuredBindingJson)))|ConvertFrom-Json
    `$native.invocationArtifactBinding=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($(Convert-ToPsLiteral $artifactBindingJson)))|ConvertFrom-Json
    `$target=Start-Process -FilePath $(Convert-ToPsLiteral $targetConfiguredPath) -ArgumentList $(Convert-ToPsLiteral $targetArgumentLine) -RedirectStandardOutput $(Convert-ToPsLiteral $targetStdoutPath) -RedirectStandardError $(Convert-ToPsLiteral $targetStderrPath) -PassThru -WindowStyle Hidden
    `$native.targetStarted=`$true;`$native.targetProcessId=[int]`$target.Id;`$native.targetBindingProof.stage='TARGET_BINDING'
    `$targetBinding=New-RecI3OwnedProcessBinding `$target `$native.targetConfiguredBinding
    `$native.targetNativeIdentity=`$targetBinding.capturedIdentity;`$native.targetNativeBinding=`$targetBinding.nativeExecutableBinding;`$native.targetBindingProof=`$targetBinding.executableBindingProof
    `$native.receiptPhase='STARTED';Write-NativeCreateOnce $(Convert-ToPsLiteral $nativeStartedPath) `$native
    `$target.WaitForExit();try{`$target.Refresh();`$raw=`$target.ExitCode;if(`$null-eq`$raw){throw 'TARGET_EXIT_MISSING'};if(`$raw-isnot[int]){throw "TARGET_EXIT_NOT_INT32:`$(`$raw.GetType().FullName)"};`$native.rawExitValue=`$raw;`$native.rawExitType=`$raw.GetType().FullName;`$native.exitCode=`$raw}catch{`$native.rawExitValue=`$null;`$native.rawExitType=`$null;`$native.exitCode=`$null;`$native.targetExitCaptureError=`$_.Exception.Message;Set-FirstFailure "TARGET_EXIT_CAPTURE_FAILED:`$(`$_.Exception.Message)"}
} catch {
    Set-FirstFailure `$_.ToString()
    if(`$native.targetStarted-and-not`$native.targetBindingProof.verified){`$native.targetBindingProof.error=`$_.Exception.Message}
    if(`$native.targetStarted-and`$null-eq`$native.targetExitCaptureError){`$native.targetExitCaptureError='TARGET_EXIT_UNOBSERVED_DUE_TO_PRIOR_FAILURE'}
    [Console]::Error.WriteLine(`$native.launchFailure)
} finally {
    if(`$null-ne`$target){
        try{if(`$null-eq`$targetBinding){`$native.targetCleanup=[ordered]@{rootIdentity=`$null;cleanupCertain=`$false;laterAuditRequired=`$true;rootAbsenceObserved=`$false;failures=@('OWNED_TARGET_BINDING_UNAVAILABLE');results=@();state='OWNED_TARGET_BINDING_UNAVAILABLE'}}elseif(-not`$target.HasExited){`$native.targetCleanup=Stop-RecI3OwnedProcessClosure `$targetBinding 1000 10000}else{`$native.targetCleanup=[ordered]@{rootIdentity=`$targetBinding.capturedIdentity;cleanupCertain=`$false;laterAuditRequired=`$true;rootAbsenceObserved=`$true;failures=@('LATER_PROCESS_AUDIT_REQUIRED');results=@();state='TARGET_ROOT_EXITED_CLOSURE_UNAUDITED'}}}catch{`$native.targetCleanup=[ordered]@{rootIdentity=if(`$null-ne`$targetBinding){`$targetBinding.capturedIdentity}else{`$null};cleanupCertain=`$false;laterAuditRequired=`$true;rootAbsenceObserved=`$false;failures=@("OWNED_TARGET_CLEANUP_EXCEPTION:`$(`$_.Exception.Message)");results=@();state='OWNED_TARGET_CLEANUP_UNCERTAIN'};Set-FirstFailure "OWNED_TARGET_CLEANUP_UNCERTAIN:`$(`$_.Exception.Message)"}
    }
    try{if(Test-Path -LiteralPath $(Convert-ToPsLiteral $targetStdoutPath) -PathType Leaf){[Console]::Out.Write([IO.File]::ReadAllText($(Convert-ToPsLiteral $targetStdoutPath)));`$native.targetStdoutComplete=`$true}else{throw 'TARGET_STDOUT_MISSING'}}catch{`$native.targetStdoutComplete=`$false;`$native.targetStdoutReadError=`$_.Exception.Message;Set-FirstFailure "TARGET_STDOUT_READ_FAILED:`$(`$_.Exception.Message)"}
    try{if(Test-Path -LiteralPath $(Convert-ToPsLiteral $targetStderrPath) -PathType Leaf){[Console]::Error.Write([IO.File]::ReadAllText($(Convert-ToPsLiteral $targetStderrPath)));`$native.targetStderrComplete=`$true}else{throw 'TARGET_STDERR_MISSING'}}catch{`$native.targetStderrComplete=`$false;`$native.targetStderrReadError=`$_.Exception.Message;Set-FirstFailure "TARGET_STDERR_READ_FAILED:`$(`$_.Exception.Message)"}
    if(`$null-ne`$target){try{`$target.Dispose()}catch{`$native.targetDisposeError=`$_.Exception.Message;Set-FirstFailure "TARGET_PROCESS_DISPOSE_UNCERTAIN:`$(`$_.Exception.Message)"}}
    `$native.receiptPhase='COMPLETED';try{Write-NativeCreateOnce $(Convert-ToPsLiteral $nativeResultPath) `$native}catch{Set-FirstFailure "NATIVE_RESULT_PUBLICATION_FAILED:`$(`$_.Exception.Message)";[Console]::Error.WriteLine(`$native.launchFailure)}
}
if (`$native.launchFailure) { exit 125 }
if (`$null-eq`$native.exitCode) { exit 125 }
exit `$native.exitCode
"@
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($scriptText))
        $hostPowerShell = (Get-Process -Id $PID).Path
        $process = Start-Process -FilePath $hostPowerShell -ArgumentList @("-NoProfile", "-NonInteractive", "-EncodedCommand", $encoded) -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru -WindowStyle Hidden
        $wrapperStarted=$true
        try{$processBinding = New-RecI3OwnedProcessBinding $process}catch{&$addOuterFailure $_.ToString()}
        $completed = $process.WaitForExit([Math]::Max(1, $externalTimeoutSeconds) * 1000)
        $timedOut = -not $completed
        if ($completed) {
            $process.WaitForExit()
            $process.Refresh()
            $wrapperExitCode = $process.ExitCode
        }
    } catch {
        &$addOuterFailure $_.ToString()
    } finally {
        if ($null -ne $process -and -not $completed) {
            try {
                if ($null -eq $processBinding) {
                    $cleanupResult = [ordered]@{ cleanupCertain=$false;laterAuditRequired=$true;rootAbsenceObserved=$false;failures=@('OWNED_PROCESS_BINDING_UNAVAILABLE');results=@();state='OWNED_WRAPPER_BINDING_UNAVAILABLE' }
                } else {
                    $cleanupResult = Stop-RecI3OwnedProcessClosure $processBinding 1000 10000
                }
                if (-not $cleanupResult.cleanupCertain) { &$addOuterFailure "OWNED_WRAPPER_CLEANUP_UNCERTAIN:$(@($cleanupResult.failures)-join'; ')" }
            } catch {
                $cleanupResult = [ordered]@{ cleanupCertain=$false;laterAuditRequired=$true;rootAbsenceObserved=$false;failures=@("OWNED_PROCESS_CLEANUP_EXCEPTION:$($_.Exception.Message)");results=@();state='OWNED_WRAPPER_CLEANUP_UNCERTAIN' }
                &$addOuterFailure "OWNED_WRAPPER_CLEANUP_UNCERTAIN:$($cleanupResult.failures[0])"
            }
        }
    }
    $nativeReceiptStatus='MISSING'
    if($nativeResultPath-and(Test-Path -LiteralPath $nativeResultPath -PathType Leaf)){
        try{$candidate=Get-Content -Raw -Encoding UTF8 -LiteralPath $nativeResultPath|ConvertFrom-Json;try{$null=Confirm-RecI3ObservedTargetReceipt $candidate $targetConfiguredBinding $invocationArtifactBinding;$nativeReceiptStatus='COMPLETED_VALID'}catch{$null=Confirm-RecI3ObservedTargetFailureReceipt $candidate $targetConfiguredBinding $invocationArtifactBinding;$nativeReceiptStatus='COMPLETED_FAILURE_VALID'};$native=$candidate}catch{&$addOuterFailure "NATIVE_RESULT_READBACK_INVALID:$($_.Exception.Message)";$nativeReceiptStatus='COMPLETED_INVALID'}
    }elseif($nativeStartedPath-and(Test-Path -LiteralPath $nativeStartedPath -PathType Leaf)){
        try{$candidate=Get-Content -Raw -Encoding UTF8 -LiteralPath $nativeStartedPath|ConvertFrom-Json;$null=Confirm-RecI3ObservedTargetStartedReceipt $candidate $targetConfiguredBinding $invocationArtifactBinding;$native=$candidate;&$addOuterFailure 'NATIVE_COMPLETION_RECEIPT_MISSING';$nativeReceiptStatus='STARTED_VALID_COMPLETION_MISSING'}catch{&$addOuterFailure "NATIVE_STARTED_READBACK_INVALID:$($_.Exception.Message)";$nativeReceiptStatus='STARTED_INVALID'}
    }else{&$addOuterFailure "NATIVE_OBSERVATION_MISSING:wrapperExit=$wrapperExitCode"}
    if($null-eq$native.targetCleanup-and$null-ne$cleanupResult){$native.targetCleanup=ConvertFrom-RecI3WrapperClosureTargetCleanup $native.targetNativeIdentity $cleanupResult}
    if($timedOut){$native.exitCode=$null;$native.rawExitValue=$null;$native.rawExitType=$null;if($null-eq$native.targetExitCaptureError){$native.targetExitCaptureError='WRAPPER_TIMEOUT_EXIT_UNOBSERVED'};&$addOuterFailure 'WRAPPER_TIMEOUT'}
    [string]$stdout = if ($stdoutPath -and (Test-Path -LiteralPath $stdoutPath -PathType Leaf)) { Get-Content -Raw -LiteralPath $stdoutPath } else { "" }
    [string]$stderr = if ($stderrPath -and (Test-Path -LiteralPath $stderrPath -PathType Leaf)) { Get-Content -Raw -LiteralPath $stderrPath } else { "" }
    if ($null -eq $stdout) { $stdout = "" }
    if ($null -eq $stderr) { $stderr = "" }
    $wrapperProcessId = if ($null -ne $process) { try{$process.Id}catch{$null} } else { $null }
    $wrapperExited = if($null-eq$process){$true}else{try{[bool]$process.HasExited}catch{$null}}
    $wrapperDisposeFailure=$null;if ($null -ne $process) { try{$process.Dispose()}catch{$wrapperDisposeFailure=$_.Exception.Message;&$addOuterFailure "PROCESS_DISPOSE_UNCERTAIN:$($_.Exception.Message)"} }
    $readTargetStream={param([string]$Path,[string]$Label) $result=[ordered]@{text='';complete=$false;error=$null};try{if(-not(Test-Path -LiteralPath $Path -PathType Leaf)){throw "${Label}_MISSING"};$result.text=[IO.File]::ReadAllText($Path);$result.complete=$true}catch{$result.error=$_.Exception.Message};$result}
    $targetStdoutPath=if($stdoutPath){"$stdoutPath.target.stdout"}else{$null};$targetStderrPath=if($stdoutPath){"$stdoutPath.target.stderr"}else{$null};$targetStdoutSalvage=&$readTargetStream $targetStdoutPath 'TARGET_STDOUT_SALVAGE';$targetStderrSalvage=&$readTargetStream $targetStderrPath 'TARGET_STDERR_SALVAGE'
    if(-not$targetStdoutSalvage.complete){&$addOuterFailure "TARGET_STDOUT_SALVAGE_FAILED:$($targetStdoutSalvage.error)"};if(-not$targetStderrSalvage.complete){&$addOuterFailure "TARGET_STDERR_SALVAGE_FAILED:$($targetStderrSalvage.error)"}
    $innerFirstFailure=$native.firstFailure;$innerLaunchFailure=$native.launchFailure;$outerFailureText=@($outerFailureState.failures)-join'; ';$combinedFailures=@($outerFailureText,$innerLaunchFailure|Where-Object{-not[string]::IsNullOrWhiteSpace([string]$_)});$native.firstFailure=if($null-ne$outerFailureState.firstFailure){$outerFailureState.firstFailure}else{$innerFirstFailure};$native.launchFailure=if($combinedFailures.Count){$combinedFailures-join'; '}else{$null}
    return [ordered]@{
        executable = $FilePath
        arguments = @($Arguments)
        exitCode = $native.exitCode
        wrapperExitCode = $wrapperExitCode
        wrapperStarted = [bool]$wrapperStarted
        wrapperProcessId = $wrapperProcessId
        wrapperExited = $wrapperExited
        wrapperIdentity = if($null-ne$processBinding){$processBinding.capturedIdentity}else{$null}
        wrapperBindingProof = if($null-ne$processBinding){$processBinding.executableBindingProof}else{[ordered]@{stage=if($wrapperStarted){'WRAPPER_BINDING'}else{'NOT_STARTED'};verified=$false;error=$native.launchFailure}}
        wrapperDisposeFailure = $wrapperDisposeFailure
        receiptPhase = $native.receiptPhase
        nativeReceiptStatus = $nativeReceiptStatus
        targetStarted = $native.targetStarted
        targetProcessId = $native.targetProcessId
        targetConfiguredPath = $native.targetConfiguredPath
        targetConfiguredBinding = $native.targetConfiguredBinding
        targetNativeIdentity = $native.targetNativeIdentity
        targetNativeBinding = $native.targetNativeBinding
        targetBindingProof = $native.targetBindingProof
        targetCleanup = $native.targetCleanup
        targetExitCaptureError = $native.targetExitCaptureError
        rawExitValue = $native.rawExitValue
        rawExitType = $native.rawExitType
        targetDisposeError = $native.targetDisposeError
        targetStdoutReadError = $native.targetStdoutReadError
        targetStderrReadError = $native.targetStderrReadError
        targetStdoutComplete = $native.targetStdoutComplete
        targetStderrComplete = $native.targetStderrComplete
        targetOutput = $targetStdoutSalvage.text
        targetErrorOutput = $targetStderrSalvage.text
        targetStdoutSalvageComplete = $targetStdoutSalvage.complete
        targetStdoutSalvageError = $targetStdoutSalvage.error
        targetStderrSalvageComplete = $targetStderrSalvage.complete
        targetStderrSalvageError = $targetStderrSalvage.error
        invocationArtifactPath = $native.invocationArtifactPath
        invocationArtifactBinding = $native.invocationArtifactBinding
        ownedCleanup = $cleanupResult
        firstFailure = $native.firstFailure
        outerFirstFailure = $outerFailureState.firstFailure
        innerFirstFailure = $innerFirstFailure
        launchFailure = $native.launchFailure
        timedOut = $timedOut
        output = $stdout.Trim()
        errorOutput = $stderr.Trim()
        logPath = $stdoutPath
        stderrPath = $stderrPath
        nativeResultPath = $nativeResultPath
        nativeStartedPath = $nativeStartedPath
    }
}

function Write-CopyReceipt([string]$Path, [object]$Value) {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes((($Value | ConvertTo-Json -Depth 20) + [Environment]::NewLine))
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
