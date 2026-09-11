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
$completionPath = Join-Path $evidenceFull "REC-I3-V8-COMPLETION-$AcceptedCommit.json"
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
$ownedEmulatorBinding = $null
$ownedEmulatorConfiguredBinding = $null
$ownedEmulatorInvocationArtifactBinding = $null
$ownedEmulatorBindingFailure = $null
$ownedEmulatorProcessId = $null
$ownedEmulatorDisposeFailure = $null
$gradleResult = $null
$secondaryFailures = @()
$ownedStop = $null
$ownedStopFailure = $null

function Write-JsonFile([string]$Path, [object]$Value) {
    $parent = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetFullPath($Path))
    [System.IO.Directory]::CreateDirectory($parent) | Out-Null
    [System.IO.File]::WriteAllText(
        [System.IO.Path]::GetFullPath($Path),
        (($Value | ConvertTo-Json -Depth 20) + [Environment]::NewLine),
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

Import-Module (Join-Path $PSScriptRoot 'rec_i3_owned_process.psm1') -Force

function Invoke-BoundedCommand(
    [string]$Name,
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [string]$LogPath,
    [int]$TimeoutSeconds = $commandTimeoutSeconds
) {
    $stderrPath = "$LogPath.stderr"
    $nativeStartedPath = "$LogPath.native.started.json"
    $nativeResultPath = "$LogPath.native.completed.json"
    $resolvedCommand = Get-Command -Name $FilePath -CommandType Application -ErrorAction Stop
    $invocationArtifactPath = [IO.Path]::GetFullPath([string]$resolvedCommand.Source)
    $invocationArtifactBinding = Get-RecI3ExecutableFileBinding $invocationArtifactPath $null 'CONFIGURED_INVOCATION_ARTIFACT'
    if([IO.Path]::GetExtension($invocationArtifactPath)-in @('.cmd','.bat')){
        $targetConfiguredPath=[IO.Path]::GetFullPath($env:ComSpec)
        $cmdArguments=@($Arguments|ForEach-Object{$value=[string]$_;if($value-match'[\s"&|<>^]'){'"'+$value.Replace('"','""')+'"'}else{$value}})
        $targetArguments=@('/d','/s','/c',('""'+$invocationArtifactPath+'" '+($cmdArguments-join' ')+'"'))
        $targetArgumentLine='/d /s /c ""'+$invocationArtifactPath+'" '+($cmdArguments-join' ')+'"'
    }else{$targetConfiguredPath=$invocationArtifactPath;$targetArguments=@($Arguments);$targetArgumentLine=ConvertTo-RecI3WindowsCommandLine $targetArguments}
    $targetConfiguredBinding = Get-RecI3ExecutableFileBinding $targetConfiguredPath $null 'CONFIGURED_TARGET'
    $configuredBindingJson=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($targetConfiguredBinding|ConvertTo-Json -Depth 6 -Compress)))
    $artifactBindingJson=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($invocationArtifactBinding|ConvertTo-Json -Depth 6 -Compress)))
    $targetStdoutPath="$LogPath.target.stdout";$targetStderrPath="$LogPath.target.stderr"
    # Keep native stderr non-terminating on PS5.1; only setup/invocation errors are launch failures.
    $scriptText = @"
`$ErrorActionPreference='Stop'; `$ProgressPreference='SilentlyContinue'
`$PSNativeCommandUseErrorActionPreference=`$false
    Import-Module $(Convert-ToPsLiteral (Join-Path $PSScriptRoot 'rec_i3_owned_process.psm1')) -Force
function Write-NativeCreateOnce([string]`$Path,[object]`$Value){`$bytes=[Text.UTF8Encoding]::new(`$false).GetBytes(((`$Value|ConvertTo-Json -Depth 20)+[Environment]::NewLine));`$stream=[IO.File]::Open(`$Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read);try{`$stream.Write(`$bytes,0,`$bytes.Length);`$stream.Flush(`$true)}finally{`$stream.Dispose()}}
function Set-FirstFailure([string]`$Message){if([string]::IsNullOrWhiteSpace(`$native.firstFailure)){`$native.firstFailure=`$Message};`$native.launchFailure=@(`$native.launchFailure,`$Message|Where-Object{`$_})-join'; '}
    `$native=[ordered]@{receiptPhase='INITIALIZED';exitCode=`$null;rawExitValue=`$null;rawExitType=`$null;firstFailure=`$null;launchFailure=`$null;targetStarted=`$null;targetProcessId=`$null;targetConfiguredPath=$(Convert-ToPsLiteral $targetConfiguredPath);targetConfiguredBinding=`$null;targetNativeIdentity=`$null;targetNativeBinding=`$null;targetBindingProof=[ordered]@{stage='NOT_STARTED';verified=`$false;error=`$null};targetCleanup=`$null;targetExitCaptureError=`$null;targetDisposeError=`$null;targetStdoutComplete=`$null;targetStderrComplete=`$null;targetStdoutReadError=`$null;targetStderrReadError=`$null;invocationArtifactPath=$(Convert-ToPsLiteral $invocationArtifactPath);invocationArtifactBinding=`$null;targetStdoutPath=$(Convert-ToPsLiteral $targetStdoutPath);targetStderrPath=$(Convert-ToPsLiteral $targetStderrPath)}
    `$target=`$null;`$targetBinding=`$null
try {
    Set-Location -LiteralPath $(Convert-ToPsLiteral $WorkingDirectory)
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
    $process = $null
    $completed = $false
    $timedOut = $false
    $native = [ordered]@{ receiptPhase='UNKNOWN';exitCode=$null;rawExitValue=$null;rawExitType=$null;firstFailure=$null;launchFailure=$null;targetStarted=$null;targetProcessId=$null;targetConfiguredPath=$targetConfiguredPath;targetConfiguredBinding=$targetConfiguredBinding;targetNativeIdentity=$null;targetNativeBinding=$null;targetBindingProof=[ordered]@{stage='NOT_STARTED';verified=$false;error=$null};targetCleanup=$null;targetExitCaptureError=$null;targetDisposeError=$null;targetStdoutComplete=$null;targetStderrComplete=$null;targetStdoutReadError=$null;targetStderrReadError=$null;invocationArtifactPath=$invocationArtifactPath;invocationArtifactBinding=$invocationArtifactBinding;targetStdoutPath=$targetStdoutPath;targetStderrPath=$targetStderrPath }
    $outerFailureState=[ordered]@{firstFailure=$null;failures=[Collections.Generic.List[string]]::new()}
    $addOuterFailure={param([string]$Message) if(-not[string]::IsNullOrWhiteSpace($Message)){if($null-eq$outerFailureState.firstFailure){$outerFailureState.firstFailure=$Message};$outerFailureState.failures.Add($Message)}}
    $wrapperExitCode = $null
    $cleanupResult = $null
    $processBinding = $null
    $wrapperStarted = $false
    $wrapperDisposeFailure=$null
    try {
        if ((Test-Path -LiteralPath $LogPath -PathType Container) -or (Test-Path -LiteralPath $stderrPath -PathType Container)) { throw "COMMAND_LOG_PATH_IS_DIRECTORY:$LogPath" }
        $hostPowerShell = (Get-Process -Id $PID).Path
        $process = Start-Process -FilePath $hostPowerShell -ArgumentList @("-NoProfile", "-NonInteractive", "-EncodedCommand", $encoded) -RedirectStandardOutput $LogPath -RedirectStandardError $stderrPath -PassThru -WindowStyle Hidden
        $wrapperStarted = $true
        try{$processBinding = New-RecI3OwnedProcessBinding $process}catch{&$addOuterFailure $_.ToString()}
        $completed = $process.WaitForExit([Math]::Max(1, $TimeoutSeconds) * 1000)
        $timedOut = -not $completed
        if ($completed) { $process.WaitForExit(); $process.Refresh(); $wrapperExitCode = $process.ExitCode }
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
    if(Test-Path -LiteralPath $nativeResultPath -PathType Leaf){
        try{$candidate=Get-Content -Raw -Encoding UTF8 -LiteralPath $nativeResultPath|ConvertFrom-Json;try{$null=Confirm-RecI3ObservedTargetReceipt $candidate $targetConfiguredBinding $invocationArtifactBinding;$nativeReceiptStatus='COMPLETED_VALID'}catch{$null=Confirm-RecI3ObservedTargetFailureReceipt $candidate $targetConfiguredBinding $invocationArtifactBinding;$nativeReceiptStatus='COMPLETED_FAILURE_VALID'};$native=$candidate}catch{&$addOuterFailure "NATIVE_RESULT_READBACK_INVALID:$($_.Exception.Message)";$nativeReceiptStatus='COMPLETED_INVALID'}
    }elseif(Test-Path -LiteralPath $nativeStartedPath -PathType Leaf){
        try{$candidate=Get-Content -Raw -Encoding UTF8 -LiteralPath $nativeStartedPath|ConvertFrom-Json;$null=Confirm-RecI3ObservedTargetStartedReceipt $candidate $targetConfiguredBinding $invocationArtifactBinding;$native=$candidate;&$addOuterFailure 'NATIVE_COMPLETION_RECEIPT_MISSING';$nativeReceiptStatus='STARTED_VALID_COMPLETION_MISSING'}catch{&$addOuterFailure "NATIVE_STARTED_READBACK_INVALID:$($_.Exception.Message)";$nativeReceiptStatus='STARTED_INVALID'}
    }else{&$addOuterFailure "NATIVE_OBSERVATION_MISSING:wrapperExit=$wrapperExitCode"}
    if($null-eq$native.targetCleanup-and$null-ne$cleanupResult){$native.targetCleanup=ConvertFrom-RecI3WrapperClosureTargetCleanup $native.targetNativeIdentity $cleanupResult}
    if($timedOut){$native.exitCode=$null;$native.rawExitValue=$null;$native.rawExitType=$null;if($null-eq$native.targetExitCaptureError){$native.targetExitCaptureError='WRAPPER_TIMEOUT_EXIT_UNOBSERVED'};&$addOuterFailure 'WRAPPER_TIMEOUT'}
    [string]$stdout = if (Test-Path -LiteralPath $LogPath -PathType Leaf) { Get-Content -Raw -LiteralPath $LogPath } else { "" }
    [string]$stderr = if (Test-Path -LiteralPath $stderrPath -PathType Leaf) { Get-Content -Raw -LiteralPath $stderrPath } else { "" }
    if ($null -eq $stdout) { $stdout = "" }
    if ($null -eq $stderr) { $stderr = "" }
    $wrapperProcessId = if ($null -ne $process) { try{$process.Id}catch{$null} } else { $null }
    $wrapperExited = if($null-eq$process){$true}else{try{[bool]$process.HasExited}catch{$null}}
    if ($null -ne $process) { try{$process.Dispose()}catch{$wrapperDisposeFailure=$_.Exception.Message;&$addOuterFailure "PROCESS_DISPOSE_UNCERTAIN:$($_.Exception.Message)"} }
    $readTargetStream={param([string]$Path,[string]$Label) $result=[ordered]@{text='';complete=$false;error=$null};try{if(-not(Test-Path -LiteralPath $Path -PathType Leaf)){throw "${Label}_MISSING"};$result.text=[IO.File]::ReadAllText($Path);$result.complete=$true}catch{$result.error=$_.Exception.Message};$result}
    $targetStdoutSalvage=&$readTargetStream $targetStdoutPath 'TARGET_STDOUT_SALVAGE';$targetStderrSalvage=&$readTargetStream $targetStderrPath 'TARGET_STDERR_SALVAGE'
    if(-not$targetStdoutSalvage.complete){&$addOuterFailure "TARGET_STDOUT_SALVAGE_FAILED:$($targetStdoutSalvage.error)"};if(-not$targetStderrSalvage.complete){&$addOuterFailure "TARGET_STDERR_SALVAGE_FAILED:$($targetStderrSalvage.error)"}
    $innerFirstFailure=$native.firstFailure;$innerLaunchFailure=$native.launchFailure;$outerFailureText=@($outerFailureState.failures)-join'; ';$combinedFailures=@($outerFailureText,$innerLaunchFailure|Where-Object{-not[string]::IsNullOrWhiteSpace([string]$_)});$native.firstFailure=if($null-ne$outerFailureState.firstFailure){$outerFailureState.firstFailure}else{$innerFirstFailure};$native.launchFailure=if($combinedFailures.Count){$combinedFailures-join'; '}else{$null}
    return [ordered]@{
        name = $Name
        executable = $FilePath
        arguments = @($Arguments)
        workingDirectory = $WorkingDirectory
        exitCode = $native.exitCode
        wrapperExitCode = $wrapperExitCode
        wrapperStarted = $wrapperStarted
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
        logPath = $LogPath
        stderrPath = $stderrPath
        nativeResultPath = $nativeResultPath
        nativeStartedPath = $nativeStartedPath
    }
}

function Save-Preflight([object]$Record) {
    $script:preflightCommands += $Record
    Write-JsonFile $preflightPath ([ordered]@{ schema = "DORA_REC_I3_V8_PREFLIGHT_V1"; commands = $script:preflightCommands })
}

function Invoke-Preflight([string]$Name, [string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory = $repositoryFull) {
    $record = Invoke-BoundedCommand $Name $FilePath $Arguments $WorkingDirectory (Join-Path $rawPath "$Name.log")
    Save-Preflight $record
    if ($record.launchFailure) { throw "PREFLIGHT_LAUNCH_FAILED:${Name}:$($record.launchFailure)" }
    if ($record.timedOut) { throw "PREFLIGHT_TIMEOUT:$Name" }
    if ($record.exitCode -ne 0) { throw "PREFLIGHT_FAILED:${Name}:$($record.exitCode)" }
    return $record
}

function Write-CreateNewJson([string]$Path, [object]$Value) {
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes((($Value | ConvertTo-Json -Depth 20) + [Environment]::NewLine))
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
}

function Invoke-RunnerCleanupFallback {
    $fallbackPath = Join-Path $evidenceFull "REC-I3-V8-CLEANUP-FALLBACK-$attemptId"
    $evidenceFailure = $null
    try {
        if (Test-Path -LiteralPath $fallbackPath) { throw "FALLBACK_ATTEMPT_ALREADY_EXISTS" }
        Write-CreateNewJson "$fallbackPath.lock" ([ordered]@{
            attemptId = $attemptId; acceptedCommit = $AcceptedCommit; acceptedTree = $AcceptedTree
        })
        New-Item -ItemType Directory -Path $fallbackPath -ErrorAction Stop | Out-Null
    } catch {
        # Refuse prior artifacts, but do not let evidence allocation prevent bounded cleanup.
        $evidenceFailure = $_.Exception.Message
        $fallbackPath = Join-Path ([System.IO.Path]::GetTempPath()) "DORA-REC-I3-CLEANUP-$attemptId-$([Guid]::NewGuid().ToString('N'))"
        New-Item -ItemType Directory -Path $fallbackPath -ErrorAction Stop | Out-Null
        Write-Warning "FALLBACK_EVIDENCE_RELOCATED:$fallbackPath; $evidenceFailure"
    }
    $packages = @()
    foreach ($package in $packageNames.Split(',')) {
        $record = [ordered]@{ package = $package; commands = @() }
        foreach ($operation in @(
            [ordered]@{ name = "force-stop"; arguments = @("-s", $Serial, "shell", "am", "force-stop", $package) },
            [ordered]@{ name = "uninstall"; arguments = @("-s", $Serial, "uninstall", $package) },
            [ordered]@{ name = "transport"; arguments = @("-s", $Serial, "get-state") },
            [ordered]@{ name = "pm-path"; arguments = @("-s", $Serial, "shell", "pm", "path", $package) },
            [ordered]@{ name = "pm-list"; arguments = @("-s", $Serial, "shell", "pm", "list", "packages") }
        )) {
            try {
                $record.commands += Invoke-BoundedCommand "fallback-$($operation.name)-$($package.Replace('.', '-'))" $adbPath $operation.arguments $repositoryFull (Join-Path $fallbackPath "fallback-$($operation.name)-$($package.Replace('.', '-')).log") 60
            } catch {
                $record.commands += [ordered]@{ name = $operation.name; exitCode = $null; timedOut = $false; launchFailure = $_.Exception.Message }
            }
        }
        $packages += $record
    }
    try {
        $emulator = Invoke-BoundedCommand "fallback-emulator-kill" $adbPath @("-s", $Serial, "emu", "kill") $repositoryFull (Join-Path $fallbackPath "fallback-emulator-kill.log") 60
    } catch {
        $emulator = [ordered]@{ exitCode = $null; timedOut = $false; launchFailure = $_.Exception.Message }
    }
    $observation = [ordered]@{
        schema = "DORA_REC_I3_RUNNER_CLEANUP_FALLBACK_V1"
        attemptId = $attemptId
        acceptedCommit = $AcceptedCommit
        acceptedTree = $AcceptedTree
        evidenceDirectory = $fallbackPath
        evidenceFailure = $evidenceFailure
        cleanupAttempted = $true
        serial = $Serial
        packageCleanup = $packages
        emulatorCleanup = $emulator
    }
    Write-CreateNewJson (Join-Path $fallbackPath "observation.json") $observation
    return $observation
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
        $ownedEmulatorInvocationArtifactBinding=Get-RecI3ExecutableFileBinding $emulatorPath $null 'CONFIGURED_EMULATOR_INVOCATION_ARTIFACT'
        $emulatorArguments=@("-avd",$AvdName,"-port","$emulatorPort","-no-window","-no-audio","-no-boot-anim")
        if([IO.Path]::GetExtension($emulatorPath)-in@('.cmd','.bat')){$emulatorLaunchPath=[IO.Path]::GetFullPath($env:ComSpec);$quotedEmulatorArguments=@($emulatorArguments|ForEach-Object{$value=[string]$_;if($value-match'[\s"&|<>^]'){'"'+$value.Replace('"','""')+'"'}else{$value}});$emulatorLaunchArguments=@('/d','/s','/c',('""'+[IO.Path]::GetFullPath($emulatorPath)+'" '+($quotedEmulatorArguments-join' ')+'"'));$emulatorArgumentLine='/d /s /c ""'+[IO.Path]::GetFullPath($emulatorPath)+'" '+($quotedEmulatorArguments-join' ')+'"'}else{$emulatorLaunchPath=[IO.Path]::GetFullPath($emulatorPath);$emulatorLaunchArguments=$emulatorArguments;$emulatorArgumentLine=ConvertTo-RecI3WindowsCommandLine $emulatorLaunchArguments}
        $ownedEmulatorConfiguredBinding=Get-RecI3ExecutableFileBinding $emulatorLaunchPath $null 'CONFIGURED_EMULATOR_TARGET'
        $ownedEmulatorProcess = Start-Process -FilePath $emulatorLaunchPath -ArgumentList $emulatorArgumentLine -RedirectStandardOutput $emulatorStdout -RedirectStandardError $emulatorStderr -PassThru -WindowStyle Hidden
        $startedEmulator = $true
        $ownedEmulatorProcessId=[int]$ownedEmulatorProcess.Id
        try{$ownedEmulatorBinding = New-RecI3OwnedProcessBinding $ownedEmulatorProcess $ownedEmulatorConfiguredBinding}catch{$ownedEmulatorBindingFailure=$_.ToString();throw}
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
        $pathAbsent = $pathRecord.exitCode -in @(0, 1) -and
            [string]::IsNullOrWhiteSpace($pathRecord.output) -and
            [string]::IsNullOrWhiteSpace($pathRecord.errorOutput)
        if ($pathRecord.launchFailure -or $listRecord.launchFailure -or $pathRecord.timedOut -or $listRecord.timedOut -or $listRecord.exitCode -ne 0 -or -not $pathAbsent -or $listed -contains "package:$package") {
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
    }
    Write-CreateNewJson $ledgerPath $ledger
    $ledgerCreated = $true
    $env:ANDROID_SERIAL = $Serial
    $gradleArguments = @(
        "--offline", "--no-daemon", "--no-configuration-cache",
        ":poc:recovery:connectedDebugAndroidTest",
        # The finally helper owns observed APK removal after evidence preservation.
        "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true",
        "-Pandroid.testInstrumentationRunnerArguments.class=$connectedSelector",
        "-Pandroid.testInstrumentationRunnerArguments.pocRecoveryE36GapiPreflight=true",
        "-Pandroid.testInstrumentationRunnerArguments.recoveryHarnessRevision=$AcceptedCommit"
    )
    $gradleResult = Invoke-BoundedCommand "connected-gradle" $gradlePath $gradleArguments (Join-Path $repositoryFull "android") (Join-Path $rawPath "connected-gradle.log")
    if ($gradleResult.launchFailure) { $primaryFailure = "CONNECTED_GRADLE_LAUNCH_FAILED:$($gradleResult.launchFailure)" }
    elseif ($gradleResult.timedOut) { $primaryFailure = "CONNECTED_GRADLE_TIMEOUT" }
    elseif ($gradleResult.exitCode -ne 0) { $primaryFailure = "CONNECTED_GRADLE_FAILED:$($gradleResult.exitCode)" }
    if ($primaryFailure) { $exitCode = 1 }
    $completion = [ordered]@{
        schema = "DORA_REC_I3_V8_ATTEMPT_COMPLETION_V1"
        state = if (-not $gradleResult.launchFailure -and -not $gradleResult.timedOut -and $gradleResult.exitCode -eq 0) { "ATTEMPT_CONSUMED_COMPLETED" } else { "ATTEMPT_CONSUMED_EXECUTION_UNKNOWN" }
        acceptedCommit = $AcceptedCommit
        acceptedTree = $AcceptedTree
        observedUtc = [DateTime]::UtcNow.ToString("o")
        gradleExitCode = $gradleResult.exitCode
        timedOut = $gradleResult.timedOut
        firstCheckpointDiagnostic = @($gradleResult.output -split "`r?`n" | Where-Object { $_ -match 'INSTRUMENTATION_CHECKPOINT_INSERT' } | Select-Object -First 1)
        connectedResult = $gradleResult
        primaryFailure = $primaryFailure
    }
    # Persist observed execution before any secondary capture; independent receipts survive one blocked path.
    foreach ($destination in @((Join-Path $rawPath "connected-result.json"), $completionPath)) {
        try {
            if ($destination -eq $completionPath -and $env:DORA_REC_I3_INJECT_COMPLETION_FAILURE -eq "1") { throw "INJECTED_COMPLETION_PERSISTENCE_FAILURE" }
            Write-CreateNewJson $destination $completion
        } catch {
            $exitCode = 1
            $secondaryFailures += [ordered]@{ stage = "connected-result-persistence"; path = $destination; message = $_.ToString() }
        }
    }
    $logcatResult = $null
    try {
        $logcatResult = Invoke-BoundedCommand "logcat-dump" $adbPath @("-s", $Serial, "logcat", "-d", "-v", "threadtime", "System.out:I", "AndroidJUnitRunner:I", "TestRunner:I", "*:S") $repositoryFull (Join-Path $rawPath "logcat-final.log") 60
        Write-JsonFile (Join-Path $rawPath "final-logcat-result.json") $logcatResult
        if ($logcatResult.launchFailure) { throw "FINAL_LOGCAT_LAUNCH_FAILED:$($logcatResult.launchFailure)" }
        if ($logcatResult.timedOut) { throw "FINAL_LOGCAT_TIMEOUT" }
        if ($logcatResult.exitCode -ne 0) { throw "FINAL_LOGCAT_FAILED:$($logcatResult.exitCode)" }
    } catch {
        $exitCode = 1
        $secondaryFailures += [ordered]@{ stage = "final-logcat"; message = $_.ToString(); command = $logcatResult }
    }
    if ($secondaryFailures.Count) {
        Write-CreateNewJson (Join-Path $evidenceFull "REC-I3-V8-SECONDARY-$timestamp.json") ([ordered]@{ connectedResult = $gradleResult; primaryFailure = $primaryFailure; failures = $secondaryFailures })
    }
} catch {
    $exitCode = 1
    if ($null -eq $gradleResult) { $primaryFailure = "$($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)" }
    else { $secondaryFailures += [ordered]@{ stage = "reporting"; message = $_.ToString() } }
} finally {
    if ($deviceIdentityVerified) {
        try {
            if ((Test-Path -LiteralPath $rawPath -PathType Container) -and (Test-Path -LiteralPath $buildRoot -PathType Container)) {
                $metadataDestination = if ($env:DORA_REC_I3_METADATA_DESTINATION) { $env:DORA_REC_I3_METADATA_DESTINATION } else { Join-Path $buildRoot "rec-i3-v8-run-metadata-$attemptId" }
                [System.IO.Directory]::CreateDirectory($metadataDestination) | Out-Null
                $metadataCopy = Invoke-BoundedCommand "metadata-copy" "robocopy.exe" @($rawPath, $metadataDestination, "/E", "/COPY:DAT", "/DCOPY:DAT", "/R:2", "/W:1", "/XJ", "/NJH", "/NJS", "/NFL", "/NDL") $repositoryFull (Join-Path $evidenceFull "REC-I3-V8-METADATA-COPY-$timestamp.log") 120
                if ($metadataCopy.launchFailure -or $metadataCopy.timedOut -or $null -eq $metadataCopy.exitCode -or $metadataCopy.exitCode -gt 7) { throw "METADATA_COPY_FAILED" }
            }
        } catch {
            if ($exitCode -eq 0) { $exitCode = 1; $primaryFailure = "METADATA_PREPARATION_FAILED:$($_.Exception.Message)" }
            try {
                Write-JsonFile (Join-Path $evidenceFull "REC-I3-V8-METADATA-FAILURE-$timestamp.json") ([ordered]@{ schema = "DORA_REC_I3_V8_METADATA_FAILURE_V1"; message = $_.Exception.Message })
            } catch {
                Write-Warning "METADATA_FAILURE_REPORT_UNAVAILABLE:$($_.Exception.Message)"
            }
        } finally {
            $preserveArguments = @(
                "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $preservationPath,
                "-SourcePath", $buildRoot, "-EvidenceRoot", $preservedPath,
                "-StagingRoot", $stagingFull, "-ObservationPath", $observationPath,
                "-AdbPath", $adbPath, "-PackageNames", $packageNames,
                "-AttemptId", $attemptId, "-Serial", $Serial
            )
            $preserveArguments += "-StopEmulator"
            try {
                $helperWatchdogSeconds = if ($env:DORA_REC_I3_HELPER_WATCHDOG_SECONDS) { [int]$env:DORA_REC_I3_HELPER_WATCHDOG_SECONDS } else { 1800 }
                $cleanupResult = Invoke-BoundedCommand "preservation-cleanup" (Get-Process -Id $PID).Path $preserveArguments $repositoryFull (Join-Path $evidenceFull "REC-I3-V8-PRESERVATION-$timestamp.log") $helperWatchdogSeconds
            } catch {
                $cleanupResult = [ordered]@{ exitCode = $null; timedOut = $false; launchFailure = $_.Exception.Message }
            } finally {
                $observationPresent = Test-Path -LiteralPath $observationPath -PathType Leaf
                if ($cleanupResult.timedOut -or -not $observationPresent) {
                    [void](Invoke-RunnerCleanupFallback)
                    if ($exitCode -eq 0) { $exitCode = 1; $primaryFailure = "HELPER_DID_NOT_COMPLETE_CLEANUP" }
                }
            }
            if ($cleanupResult.launchFailure -or $cleanupResult.timedOut -or $cleanupResult.exitCode -ne 0) {
                if ($exitCode -eq 0) { $exitCode = 1; $primaryFailure = "PRESERVATION_OR_CLEANUP_FAILED" }
            }
        }
    } else {
        $ownedProcessStopAttempted = $false
        if ($null -ne $ownedEmulatorProcess) {
            $ownedProcessStopAttempted = $true
            try {
                if ($null -eq $ownedEmulatorBinding) { throw 'OWNED_PROCESS_BINDING_UNAVAILABLE:EMULATOR' }
                $ownedStop = Stop-RecI3OwnedProcessClosure $ownedEmulatorBinding 1000 10000
                if (-not $ownedStop.cleanupCertain) { throw "OWNED_EMULATOR_CLEANUP_UNCERTAIN:$(@($ownedStop.failures)-join'; ')" }
            } catch {
                $ownedStopFailure = "OWNED_PROCESS_CLEANUP_EXCEPTION:$($_.Exception.Message)"
                $exitCode = 1
                if($null-eq$primaryFailure){$primaryFailure=$ownedStopFailure}else{$secondaryFailures += [ordered]@{stage='owned-emulator-cleanup';message=$ownedStopFailure}}
            }
        }
        Write-JsonFile $observationPath ([ordered]@{
            schema = "DORA_REC_I3_CLEANUP_OBSERVATION_V2"
            attemptId = $attemptId
            serial = $Serial
            copySucceeded = $false
            copyFailure = "DEVICE_IDENTITY_NOT_VERIFIED"
            cleanupFailure = "CLEANUP_INTENTIONALLY_NOT_RUN_ON_UNVERIFIED_TARGET"
            cleanupAttempted = $false
            packageCleanup = @()
            emulatorCleanup = [ordered]@{ attempted = $false; ownedProcessStopAttempted = $ownedProcessStopAttempted; started=$startedEmulator;processId=$ownedEmulatorProcessId;invocationArtifactBinding=$ownedEmulatorInvocationArtifactBinding;configuredBinding=$ownedEmulatorConfiguredBinding;retainedBinding=if($null-ne$ownedEmulatorBinding){$ownedEmulatorBinding.capturedIdentity}else{$null};bindingFailure=$ownedEmulatorBindingFailure;stdoutPath=if($startedEmulator){$emulatorStdout}else{$null};stderrPath=if($startedEmulator){$emulatorStderr}else{$null}; ownedStop = $ownedStop; ownedStopFailure = $ownedStopFailure }
        })
    }
    if($null-ne$ownedEmulatorProcess){try{$ownedEmulatorProcess.Dispose()}catch{$ownedEmulatorDisposeFailure=$_.Exception.Message;$exitCode=1;if($null-eq$primaryFailure){$primaryFailure="OWNED_EMULATOR_DISPOSE_UNCERTAIN:$ownedEmulatorDisposeFailure"}else{$secondaryFailures += [ordered]@{stage='owned-emulator-dispose';message=$ownedEmulatorDisposeFailure}}}}
    try {
        if ($env:DORA_REC_I3_INJECT_REPORT_FAILURE -eq "1") { throw "INJECTED_REPORTING_FAILURE" }
        Write-JsonFile $reportPath ([ordered]@{
            schema = "DORA_REC_I3_V8_HOST_RUN_REPORT_V1"
            acceptedCommit = $AcceptedCommit
            acceptedTree = $AcceptedTree
            attemptId = $attemptId
            ledgerCreated = $ledgerCreated
            primaryFailure = $primaryFailure
            connectedResult = $gradleResult
            secondaryFailures = $secondaryFailures
            cleanupExitCode = if ($null -ne $cleanupResult) { $cleanupResult.exitCode } else { $null }
            ownedStop = $ownedStop
            ownedStopFailure = $ownedStopFailure
            startedEmulator = $startedEmulator
            ownedEmulatorProcessId = $ownedEmulatorProcessId
            ownedEmulatorConfiguredBinding = $ownedEmulatorConfiguredBinding
            ownedEmulatorInvocationArtifactBinding = $ownedEmulatorInvocationArtifactBinding
            ownedEmulatorRetainedIdentity = if($null-ne$ownedEmulatorBinding){$ownedEmulatorBinding.capturedIdentity}else{$null}
            ownedEmulatorBindingFailure = $ownedEmulatorBindingFailure
            ownedEmulatorDisposeFailure = $ownedEmulatorDisposeFailure
            exitCode = $exitCode
        })
    } catch {
        $reportFailure = $_.Exception.Message
        $exitCode = 1
        $secondaryFailures += [ordered]@{ stage = "final-report"; message = $reportFailure }
        Write-JsonFile (Join-Path $evidenceFull "REPORTING_FAILURE.txt") ([ordered]@{ connectedResult = $gradleResult; primaryFailure = $primaryFailure; secondaryFailures = $secondaryFailures; exitCode = $exitCode })
    }
}

if ($exitCode -ne 0) {
    $failureMessage = if ($primaryFailure) { $primaryFailure } else { "EVIDENCE_OR_REPORTING_FAILED:$(($secondaryFailures | ConvertTo-Json -Depth 12 -Compress))" }
    Write-Error $failureMessage -ErrorAction Continue
    exit $exitCode
}
Write-Output "REC_I3_V8_HOST_RUN_COMPLETE $reportPath"
