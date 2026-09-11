Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'rec_i3_owned_process.psm1') -Force
$children=@()
try {
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
  if(-not$closure.cleanupCertain-or$closure.capturedCount-lt2-or@($closure.capturedAncestry|Where-Object{$null-ne$_}).Count-lt1){throw 'OWNED_DESCENDANT_CLOSURE_FAILED'}
  $nestedPid=[int](Get-Content -Raw -LiteralPath $childPidPath);if(Get-Process -Id $nestedPid -ErrorAction SilentlyContinue){throw 'OWNED_DESCENDANT_REMAINS'};Remove-Item -LiteralPath $childPidPath -Force
  'PASS: exact retained handle identity, mismatch rejection, termination, graceful race, and validated descendant closure'
} finally { foreach($c in $children){try{if(-not$c.HasExited){$c.Kill();$c.WaitForExit(5000)|Out-Null}}catch{};try{$c.Dispose()}catch{}} }
