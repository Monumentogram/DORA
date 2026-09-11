Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not ('DoraRecI3OwnedProcessNative' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;
public sealed class DoraRecI3ProcessIdentity { public int ProcessId; public long CreationFileTimeUtc; public string ExecutablePath; }
public static class DoraRecI3OwnedProcessNative {
  const uint WAIT_OBJECT_0=0, WAIT_TIMEOUT=258, WAIT_FAILED=0xFFFFFFFF;
  [DllImport("kernel32.dll",SetLastError=true)] static extern uint GetProcessId(SafeProcessHandle h);
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetProcessTimes(SafeProcessHandle h,out System.Runtime.InteropServices.ComTypes.FILETIME c,out System.Runtime.InteropServices.ComTypes.FILETIME e,out System.Runtime.InteropServices.ComTypes.FILETIME k,out System.Runtime.InteropServices.ComTypes.FILETIME u);
  [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool QueryFullProcessImageNameW(SafeProcessHandle h,uint f,StringBuilder p,ref int n);
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool TerminateProcess(SafeProcessHandle h,uint c);
  [DllImport("kernel32.dll",SetLastError=true)] static extern uint WaitForSingleObject(SafeProcessHandle h,uint ms);
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetExitCodeProcess(SafeProcessHandle h,out uint c);
  static void Check(SafeProcessHandle h){if(h==null||h.IsInvalid||h.IsClosed)throw new InvalidOperationException("RETAINED_HANDLE_INVALID");}
  public static DoraRecI3ProcessIdentity Identity(SafeProcessHandle h){Check(h);uint pid=GetProcessId(h);if(pid==0)throw new Win32Exception(Marshal.GetLastWin32Error(),"GET_PROCESS_ID_FAILED");System.Runtime.InteropServices.ComTypes.FILETIME c,e,k,u;if(!GetProcessTimes(h,out c,out e,out k,out u))throw new Win32Exception(Marshal.GetLastWin32Error(),"GET_PROCESS_TIMES_FAILED");var p=new StringBuilder(32768);int n=p.Capacity;if(!QueryFullProcessImageNameW(h,0,p,ref n))throw new Win32Exception(Marshal.GetLastWin32Error(),"QUERY_PROCESS_IMAGE_FAILED");return new DoraRecI3ProcessIdentity{ProcessId=unchecked((int)pid),CreationFileTimeUtc=((long)(uint)c.dwHighDateTime<<32)|(uint)c.dwLowDateTime,ExecutablePath=p.ToString()};}
  public static bool Wait(SafeProcessHandle h,uint ms){Check(h);uint r=WaitForSingleObject(h,ms);if(r==WAIT_OBJECT_0)return true;if(r==WAIT_TIMEOUT)return false;if(r==WAIT_FAILED)throw new Win32Exception(Marshal.GetLastWin32Error(),"WAIT_FAILED");throw new InvalidOperationException("WAIT_UNEXPECTED:"+r);}
  public static void Terminate(SafeProcessHandle h,uint c){Check(h);if(!TerminateProcess(h,c))throw new Win32Exception(Marshal.GetLastWin32Error(),"TERMINATE_FAILED");}
  public static int ExitCode(SafeProcessHandle h){Check(h);uint c;if(!GetExitCodeProcess(h,out c))throw new Win32Exception(Marshal.GetLastWin32Error(),"EXIT_CODE_FAILED");return unchecked((int)c);}
}

'@
}

if (-not ('DoraRecI3ParentSnapshot' -as [type])) {
    Add-Type -TypeDefinition @'
using System; using System.Collections.Generic; using System.ComponentModel; using System.Runtime.InteropServices;
public sealed class DoraRecI3ParentSnapshotResult { public long CapturedAtFileTimeUtc; public Dictionary<int,int> Parents; }
public static class DoraRecI3ParentSnapshot {
 [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] struct E {public uint size,usage,pid;public IntPtr heap;public uint module,threads,parent;public int pri;public uint flags;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=260)]public string exe;}
 [DllImport("kernel32.dll",SetLastError=true)]static extern IntPtr CreateToolhelp32Snapshot(uint f,uint p);[DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]static extern bool Process32FirstW(IntPtr h,ref E e);[DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]static extern bool Process32NextW(IntPtr h,ref E e);[DllImport("kernel32.dll")]static extern bool CloseHandle(IntPtr h);
 [DllImport("kernel32.dll")]static extern void GetSystemTimePreciseAsFileTime(out System.Runtime.InteropServices.ComTypes.FILETIME t);
 static long FileTime(){System.Runtime.InteropServices.ComTypes.FILETIME t;GetSystemTimePreciseAsFileTime(out t);return ((long)(uint)t.dwHighDateTime<<32)|(uint)t.dwLowDateTime;}
 public static DoraRecI3ParentSnapshotResult Snapshot(){IntPtr h=CreateToolhelp32Snapshot(2,0);if(h==new IntPtr(-1))throw new Win32Exception(Marshal.GetLastWin32Error(),"PROCESS_SNAPSHOT_FAILED");long captured=FileTime();try{var d=new Dictionary<int,int>();var e=new E();e.size=(uint)Marshal.SizeOf(typeof(E));if(!Process32FirstW(h,ref e)){int firstError=Marshal.GetLastWin32Error();if(firstError!=18)throw new Win32Exception(firstError,"PROCESS32_FIRST_FAILED");return new DoraRecI3ParentSnapshotResult{CapturedAtFileTimeUtc=captured,Parents=d};}while(true){d[(int)e.pid]=(int)e.parent;e.size=(uint)Marshal.SizeOf(typeof(E));if(Process32NextW(h,ref e))continue;int nextError=Marshal.GetLastWin32Error();if(nextError!=18)throw new Win32Exception(nextError,"PROCESS32_NEXT_FAILED");break;}return new DoraRecI3ParentSnapshotResult{CapturedAtFileTimeUtc=captured,Parents=d};}finally{if(!CloseHandle(h))throw new Win32Exception(Marshal.GetLastWin32Error(),"PROCESS_SNAPSHOT_CLOSE_FAILED");}}
}
'@
}

function ConvertTo-RecI3Identity([object]$Native,[int]$ParentProcessId) {
    [ordered]@{ processId=[int]$Native.ProcessId; parentProcessId=$ParentProcessId; executablePath=[IO.Path]::GetFullPath([string]$Native.ExecutablePath); creationTimeUtc=[DateTime]::FromFileTimeUtc([int64]$Native.CreationFileTimeUtc).ToString('o'); creationFileTimeUtc=[int64]$Native.CreationFileTimeUtc }
}

function Test-RecI3Identity([object]$Expected,[object]$Actual) {
    if ($Expected.processId -ne $Actual.processId -or [IO.Path]::GetFullPath([string]$Expected.executablePath) -cne [IO.Path]::GetFullPath([string]$Actual.executablePath)) { throw 'OWNED_PROCESS_IDENTITY_MISMATCH' }
    $a=[datetime]::ParseExact([string]$Expected.creationTimeUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind).Ticks
    $b=[datetime]::ParseExact([string]$Actual.creationTimeUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind).Ticks
    if (($a-($a%10)) -ne ($b-($b%10))) { throw 'OWNED_PROCESS_IDENTITY_MISMATCH' }
    $true
}

function Test-RecI3ObservedChildBinding([object]$FirstSnapshot,[object]$ConfirmationSnapshot,[object]$ParentIdentity,[object]$ChildIdentity) {
    $childId=[int]$ChildIdentity.processId;$parentId=[int]$ParentIdentity.processId
    if($null-eq$FirstSnapshot.parents-or-not$FirstSnapshot.parents.ContainsKey($childId)-or[int]$FirstSnapshot.parents[$childId]-ne$parentId){throw 'OWNED_DESCENDANT_FIRST_PARENT_UNPROVEN'}
    if($ParentIdentity.creationFileTimeUtc-isnot[long]-or$ChildIdentity.creationFileTimeUtc-isnot[long]-or[int64]$ChildIdentity.creationFileTimeUtc-lt[int64]$ParentIdentity.creationFileTimeUtc){throw 'OWNED_DESCENDANT_PARENT_TIME_CONTRADICTION'}
    if($FirstSnapshot.capturedAtFileTimeUtc-isnot[long]-or[int64]$ChildIdentity.creationFileTimeUtc-gt[int64]$FirstSnapshot.capturedAtFileTimeUtc){throw 'OWNED_DESCENDANT_SNAPSHOT_REPLACEMENT'}
    if($null-eq$ConfirmationSnapshot.parents-or-not$ConfirmationSnapshot.parents.ContainsKey($childId)-or[int]$ConfirmationSnapshot.parents[$childId]-ne$parentId){throw 'OWNED_DESCENDANT_PARENT_CONFIRMATION_UNCERTAIN'}
    $true
}

function Get-RecI3ParentSnapshot {
    $snapshot=[DoraRecI3ParentSnapshot]::Snapshot()
    [ordered]@{capturedAtFileTimeUtc=[int64]$snapshot.CapturedAtFileTimeUtc;parents=$snapshot.Parents}
}

function New-RecI3OwnedProcessBinding([Diagnostics.Process]$Process) {
    $handle=$Process.SafeHandle
    if($null-eq$handle-or$handle.IsInvalid-or$handle.IsClosed){throw 'OWNED_PROCESS_HANDLE_INVALID'}
    $identity=ConvertTo-RecI3Identity ([DoraRecI3OwnedProcessNative]::Identity($handle)) $PID
    [ordered]@{ process=$Process; safeHandle=$handle; capturedIdentity=$identity; ancestry=$null; identitySource='RETAINED_SAFE_PROCESS_HANDLE' }
}

function New-RecI3TerminationExitUncertainResult([object]$Captured,[object]$Fresh,[bool]$TerminationSucceeded,[string]$TerminationError,[string]$WaitError) {
    [ordered]@{capturedIdentity=$Captured;freshIdentity=$Fresh;terminationAttempted=$true;terminationSucceeded=$TerminationSucceeded;terminationError=@($TerminationError,$WaitError|Where-Object{$null-ne$_})-join'; ';absenceObserved=$false;replacementObserved=$false;unknown=$true;state='TERMINATION_EXIT_UNCERTAIN';exitCode=$null}
}

function Stop-RecI3OwnedProcess([object]$Binding,[int]$GraceMilliseconds=1000,[int]$ForceWaitMilliseconds=10000,[scriptblock]$ExitCodeReader=$null) {
    $h=$Binding.safeHandle;$captured=$Binding.capturedIdentity
    $result=[ordered]@{capturedIdentity=$captured;freshIdentity=$null;terminationAttempted=$false;terminationSucceeded=$false;terminationError=$null;absenceObserved=$false;replacementObserved=$false;unknown=$true;state='CLEANUP_UNCERTAIN';exitCode=$null;exitCodeCaptureError=$null}
    $readExitCode=if($null-ne$ExitCodeReader){$ExitCodeReader}else{{param($handle)[DoraRecI3OwnedProcessNative]::ExitCode($handle)}}
    if([DoraRecI3OwnedProcessNative]::Wait($h,[uint32][Math]::Max(0,$GraceMilliseconds))){$result.absenceObserved=$true;$result.unknown=$false;$result.state='GRACEFUL_EXIT';try{$result.exitCode=&$readExitCode $h}catch{$result.state='GRACEFUL_EXIT_CODE_UNCERTAIN';$result.exitCodeCaptureError=$_.Exception.Message};return $result}
    $fresh=ConvertTo-RecI3Identity ([DoraRecI3OwnedProcessNative]::Identity($h)) ([int]$captured.parentProcessId)
    $null=Test-RecI3Identity $captured $fresh;$result.freshIdentity=$fresh;$result.terminationAttempted=$true
    $error=$null;$succeeded=$false
    try{[DoraRecI3OwnedProcessNative]::Terminate($h,125);$succeeded=$true}catch{$error=$_.Exception.Message}
    $result.terminationSucceeded=$succeeded;$result.terminationError=$error
    $waitError=$null;$exited=$false
    try{$exited=[DoraRecI3OwnedProcessNative]::Wait($h,[uint32][Math]::Max(1,$ForceWaitMilliseconds))}catch{$waitError=$_.Exception.Message}
    if($null-ne$waitError-or-not$exited){return New-RecI3TerminationExitUncertainResult $captured $fresh $succeeded $error $(if($null-ne$waitError){$waitError}else{'OWNED_PROCESS_EXIT_UNOBSERVED'})}
    $result.absenceObserved=$true;$result.unknown=$false;$result.state=if($null-ne$error){'EXITED_DURING_TERMINATION'}else{'TERMINATED'}
    try{$result.exitCode=&$readExitCode $h}catch{$result.state="$($result.state)_EXIT_CODE_UNCERTAIN";$result.exitCodeCaptureError=$_.Exception.Message}
    $result
}

function Add-RecI3ObservedDescendants([Collections.Generic.List[object]]$Bindings,[Collections.Generic.HashSet[string]]$Known,[Collections.Generic.List[string]]$Failures) {
    $first=Get-RecI3ParentSnapshot;$added=0
    for($index=0;$index-lt$Bindings.Count;$index++){
        $parent=$Bindings[$index]
        foreach($entry in @($first.parents.GetEnumerator()|Where-Object{$_.Value-eq$parent.capturedIdentity.processId})){
            $p=$null;$child=$null;$key=$null
            try{
                $p=[Diagnostics.Process]::GetProcessById([int]$entry.Key);$child=New-RecI3OwnedProcessBinding $p;$key="$($child.capturedIdentity.processId)|$($child.capturedIdentity.creationFileTimeUtc)"
                if($Known.Contains($key)){$p.Dispose();continue}
                $parentFresh=ConvertTo-RecI3Identity ([DoraRecI3OwnedProcessNative]::Identity($parent.safeHandle)) ([int]$parent.capturedIdentity.parentProcessId);$null=Test-RecI3Identity $parent.capturedIdentity $parentFresh
                if([DoraRecI3OwnedProcessNative]::Wait($parent.safeHandle,0)-or[DoraRecI3OwnedProcessNative]::Wait($child.safeHandle,0)){throw 'OWNED_DESCENDANT_EXITED_DURING_BINDING'}
                $confirmation=Get-RecI3ParentSnapshot
                $childFresh=ConvertTo-RecI3Identity ([DoraRecI3OwnedProcessNative]::Identity($child.safeHandle)) ([int]$parent.capturedIdentity.processId);$null=Test-RecI3Identity $child.capturedIdentity $childFresh
                $null=Test-RecI3ObservedChildBinding $first $confirmation $parent.capturedIdentity $child.capturedIdentity
                if([DoraRecI3OwnedProcessNative]::Wait($parent.safeHandle,0)-or[DoraRecI3OwnedProcessNative]::Wait($child.safeHandle,0)){throw 'OWNED_DESCENDANT_EXITED_DURING_CONFIRMATION'}
                $child.capturedIdentity.parentProcessId=[int]$parent.capturedIdentity.processId
                $child.ancestry=[ordered]@{parentIdentity=$parent.capturedIdentity;childIdentity=$child.capturedIdentity;capturedFrom='CONFIRMED_TOOLHELP_PARENT_SNAPSHOT';firstSnapshotFileTimeUtc=$first.capturedAtFileTimeUtc;confirmationSnapshotFileTimeUtc=$confirmation.capturedAtFileTimeUtc}
                $null=$Known.Add($key);$Bindings.Add($child);$added++
            }catch{if($null-ne$p-and($null-eq$key-or-not$Known.Contains($key))){$p.Dispose()};$Failures.Add("OWNED_DESCENDANT_CAPTURE_UNCERTAIN:$($entry.Key):$($_.Exception.Message)")}
        }
    }
    [int]$added
}

function Stop-RecI3OwnedProcessClosure([object]$RootBinding,[int]$GraceMilliseconds=1000,[int]$ForceWaitMilliseconds=10000) {
    if($null-eq$RootBinding){return [ordered]@{rootIdentity=$null;capturedCount=0;capturedAncestry=@();results=@();failures=@('OWNED_PROCESS_BINDING_UNAVAILABLE');snapshotPasses=0;stablePasses=0;cleanupCertain=$false}}
    $bindings=[Collections.Generic.List[object]]::new();$bindings.Add($RootBinding)
    $failures=[Collections.Generic.List[string]]::new()
    $known=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$null=$known.Add("$($RootBinding.capturedIdentity.processId)|$($RootBinding.capturedIdentity.creationFileTimeUtc)")
    $snapshotPasses=0;$stablePasses=0;$deadline=[DateTime]::UtcNow.AddMilliseconds([Math]::Max(250,[Math]::Min(2000,$GraceMilliseconds)))
    do{
        $added=0;$snapshotPasses++
        try{$added=Add-RecI3ObservedDescendants $bindings $known $failures}catch{$failures.Add("OWNED_CLOSURE_SNAPSHOT_UNCERTAIN:$($_.Exception.Message)");break}
        if($added-eq0){$stablePasses++}else{$stablePasses=0}
        if([DateTime]::UtcNow-lt$deadline){Start-Sleep -Milliseconds 25}
    }while([DateTime]::UtcNow-lt$deadline)
    if($stablePasses-lt2){$failures.Add('OWNED_CLOSURE_PRE_SHUTDOWN_RECONCILIATION_UNCERTAIN')}
    $results=[Collections.Generic.List[object]]::new()
    try{
        $index=$bindings.Count-1
        while($index-ge0){
            try{$snapshotPasses++;$added=Add-RecI3ObservedDescendants $bindings $known $failures;if($added-gt0){$index=$bindings.Count-1;continue}}catch{$failures.Add("OWNED_CLOSURE_SHUTDOWN_SNAPSHOT_UNCERTAIN:$($_.Exception.Message)")}
            try{$result=Stop-RecI3OwnedProcess $bindings[$index] 0 $ForceWaitMilliseconds;$results.Add($result);if($result.unknown-or-not$result.absenceObserved){$failures.Add("OWNED_INSTANCE_EXIT_UNCERTAIN:$($bindings[$index].capturedIdentity.processId):$($result.terminationError)")}}
            catch{$failures.Add("OWNED_INSTANCE_CLEANUP_UNCERTAIN:$($bindings[$index].capturedIdentity.processId):$($_.Exception.Message)")}
            $index--
        }
        $postStable=0;for($post=0;$post-lt2;$post++){$snapshotPasses++;try{$added=Add-RecI3ObservedDescendants $bindings $known $failures;if($added-eq0){$postStable++}else{$failures.Add('OWNED_DESCENDANT_OBSERVED_AFTER_PARENT_SHUTDOWN')}}catch{$failures.Add("OWNED_CLOSURE_POST_SHUTDOWN_SNAPSHOT_UNCERTAIN:$($_.Exception.Message)")};Start-Sleep -Milliseconds 25};if($postStable-ne2){$failures.Add('OWNED_CLOSURE_POST_SHUTDOWN_RECONCILIATION_UNCERTAIN')}
    }finally{foreach($binding in $bindings){if($binding-ne$RootBinding){try{$binding.process.Dispose()}catch{$failures.Add("OWNED_INSTANCE_DISPOSE_UNCERTAIN:$($binding.capturedIdentity.processId)")}}}}
    [ordered]@{rootIdentity=$RootBinding.capturedIdentity;capturedCount=[int]$bindings.Count;capturedAncestry=@($bindings|ForEach-Object{$_.ancestry});results=@($results);failures=@($failures);snapshotPasses=$snapshotPasses;stablePasses=$stablePasses;cleanupCertain=($failures.Count-eq0-and@($results|Where-Object{-not$_.absenceObserved-or$_.unknown}).Count-eq0)}
}

Export-ModuleMember -Function New-RecI3OwnedProcessBinding,Stop-RecI3OwnedProcess,Stop-RecI3OwnedProcessClosure,Test-RecI3Identity,Test-RecI3ObservedChildBinding,New-RecI3TerminationExitUncertainResult
