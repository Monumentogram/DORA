Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'rec_i3_owned_process.psm1') -Force
$children=@()
try {
  $parentIdentity=[ordered]@{processId=100;parentProcessId=1;executablePath='C:\Windows\System32\cmd.exe';creationTimeUtc='2026-09-11T08:00:00.0000000Z';creationFileTimeUtc=1000L}
  $childIdentity=[ordered]@{processId=200;parentProcessId=100;executablePath='C:\Windows\System32\cmd.exe';creationTimeUtc='2026-09-11T08:00:00.0000100Z';creationFileTimeUtc=1100L}
  $first=[ordered]@{capturedAtFileTimeUtc=1200L;parents=@{200=100}}
  $confirm=[ordered]@{capturedAtFileTimeUtc=1300L;parents=@{200=100}}
  if(-not(Test-RecI3ObservedChildBinding $first $confirm $parentIdentity $childIdentity)){throw 'VALID_SNAPSHOT_CHILD_BINDING_REJECTED'}
  $replacement=[ordered]@{};foreach($k in $childIdentity.Keys){$replacement[$k]=$childIdentity[$k]};$replacement.creationFileTimeUtc=1201L
  try{$null=Test-RecI3ObservedChildBinding $first $confirm $parentIdentity $replacement;throw 'SNAPSHOT_CHILD_REPLACEMENT_ACCEPTED'}catch{if($_.Exception.Message-ceq'SNAPSHOT_CHILD_REPLACEMENT_ACCEPTED'){throw}}
  $missing=[ordered]@{capturedAtFileTimeUtc=1300L;parents=@{}}
  try{$null=Test-RecI3ObservedChildBinding $first $missing $parentIdentity $childIdentity;throw 'MISSING_CONFIRMATION_ACCEPTED'}catch{if($_.Exception.Message-ceq'MISSING_CONFIRMATION_ACCEPTED'){throw}}
  $postTerminateWaitFailure=New-RecI3TerminationExitUncertainResult $parentIdentity $parentIdentity $true $null 'WAIT_FAILED:synthetic'
  if(-not$postTerminateWaitFailure.terminationAttempted-or-not$postTerminateWaitFailure.terminationSucceeded-or-not$postTerminateWaitFailure.unknown-or$postTerminateWaitFailure.absenceObserved-or$postTerminateWaitFailure.state-cne'TERMINATION_EXIT_UNCERTAIN'-or$postTerminateWaitFailure.terminationError-cnotmatch'WAIT_FAILED'){throw 'POST_TERMINATION_WAIT_FAILURE_NOT_PRESERVED'}
  $bindingUnavailable=Stop-RecI3OwnedProcessClosure $null 0 1
  if($bindingUnavailable.cleanupCertain-or$bindingUnavailable.failures-cnotcontains'OWNED_PROCESS_BINDING_UNAVAILABLE'){throw 'BINDING_ACQUISITION_FAILURE_FALSE_PASS'}
  $p=Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/c','ping 127.0.0.1 -n 30 >nul') -PassThru -WindowStyle Hidden;$children+=,$p
  $b=New-RecI3OwnedProcessBinding $p
  $wrong=[ordered]@{};foreach($k in $b.capturedIdentity.Keys){$wrong[$k]=$b.capturedIdentity[$k]};$wrong.executablePath='C:\Windows\System32\notepad.exe'
  try{$null=Test-RecI3Identity $wrong $b.capturedIdentity;throw 'MISMATCH_ACCEPTED'}catch{if($_.Exception.Message-ceq'MISMATCH_ACCEPTED'){throw}}
  if($p.HasExited){throw 'MISMATCH_KILLED_CHILD'}
  $r=Stop-RecI3OwnedProcess $b 0 10000
  if($r.state-cne'TERMINATED'-or-not$r.terminationSucceeded-or-not$r.absenceObserved){throw 'EXACT_HANDLE_TERMINATION_FAILED'}
  $q=Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/c','exit 7') -PassThru -WindowStyle Hidden;$children+=,$q
  $qb=New-RecI3OwnedProcessBinding $q;$q.WaitForExit()
  $qr=Stop-RecI3OwnedProcess $qb 10 1000
  if($qr.state-cne'GRACEFUL_EXIT'-or$qr.exitCode-ne7-or$qr.terminationAttempted){throw 'ALREADY_EXITED_RESULT_INVALID'}
  $childPidPath=Join-Path ([IO.Path]::GetTempPath()) ('dora-owned-child-'+[Guid]::NewGuid().ToString('N')+'.txt')
  $nestedScript='$c=Start-Process -FilePath $env:ComSpec -ArgumentList @(''/d'',''/c'',''ping 127.0.0.1 -n 30 >nul'') -PassThru -WindowStyle Hidden;[IO.File]::WriteAllText('''+$childPidPath.Replace("'","''")+''',[string]$c.Id);Start-Sleep -Seconds 30'
  $encoded=[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($nestedScript));$root=Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -ArgumentList @('-NoProfile','-NonInteractive','-EncodedCommand',$encoded) -PassThru -WindowStyle Hidden;$children+=,$root
  $deadline=[DateTime]::UtcNow.AddSeconds(5);while(-not(Test-Path -LiteralPath $childPidPath)-and[DateTime]::UtcNow-lt$deadline){Start-Sleep -Milliseconds 25};if(-not(Test-Path -LiteralPath $childPidPath)){throw 'NESTED_CHILD_NOT_STARTED'}
  $rootBinding=New-RecI3OwnedProcessBinding $root;$closure=Stop-RecI3OwnedProcessClosure $rootBinding 0 10000
  if(-not$closure.cleanupCertain-or$closure.capturedCount-lt2-or@($closure.capturedAncestry|Where-Object{$null-ne$_}).Count-lt1-or$closure.snapshotPasses-lt2-or$closure.stablePasses-lt2){throw 'OWNED_DESCENDANT_CLOSURE_FAILED'}
  $nestedPid=[int](Get-Content -Raw -LiteralPath $childPidPath);if(Get-Process -Id $nestedPid -ErrorAction SilentlyContinue){throw 'OWNED_DESCENDANT_REMAINS'};Remove-Item -LiteralPath $childPidPath -Force
  'PASS: exact retained handle identity, snapshot-bound descendants, mismatch rejection, termination, graceful race, and reconciled descendant closure'
} finally { foreach($c in $children){try{if(-not$c.HasExited){$c.Kill();$c.WaitForExit(5000)|Out-Null}}catch{};try{$c.Dispose()}catch{}} }
