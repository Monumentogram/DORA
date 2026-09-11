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
public static class DoraRecI3ParentSnapshot {
 [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] struct E {public uint size,usage,pid;public IntPtr heap;public uint module,threads,parent;public int pri;public uint flags;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=260)]public string exe;}
 [DllImport("kernel32.dll",SetLastError=true)]static extern IntPtr CreateToolhelp32Snapshot(uint f,uint p);[DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]static extern bool Process32FirstW(IntPtr h,ref E e);[DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]static extern bool Process32NextW(IntPtr h,ref E e);[DllImport("kernel32.dll")]static extern bool CloseHandle(IntPtr h);
 public static Dictionary<int,int> Parents(){IntPtr h=CreateToolhelp32Snapshot(2,0);if(h==new IntPtr(-1))throw new Win32Exception(Marshal.GetLastWin32Error());try{var d=new Dictionary<int,int>();var e=new E();e.size=(uint)Marshal.SizeOf(typeof(E));if(!Process32FirstW(h,ref e))throw new Win32Exception(Marshal.GetLastWin32Error());do{d[(int)e.pid]=(int)e.parent;e.size=(uint)Marshal.SizeOf(typeof(E));}while(Process32NextW(h,ref e));return d;}finally{CloseHandle(h);}}
}
'@
}

function ConvertTo-RecI3Identity([object]$Native,[int]$ParentProcessId) {
    [ordered]@{ processId=[int]$Native.ProcessId; parentProcessId=$ParentProcessId; executablePath=[IO.Path]::GetFullPath([string]$Native.ExecutablePath); creationTimeUtc=[DateTime]::FromFileTimeUtc([int64]$Native.CreationFileTimeUtc).ToString('o') }
}

function Test-RecI3Identity([object]$Expected,[object]$Actual) {
    if ($Expected.processId -ne $Actual.processId -or [IO.Path]::GetFullPath([string]$Expected.executablePath) -cne [IO.Path]::GetFullPath([string]$Actual.executablePath)) { throw 'OWNED_PROCESS_IDENTITY_MISMATCH' }
    $a=[datetime]::ParseExact([string]$Expected.creationTimeUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind).Ticks
    $b=[datetime]::ParseExact([string]$Actual.creationTimeUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind).Ticks
    if (($a-($a%10)) -ne ($b-($b%10))) { throw 'OWNED_PROCESS_IDENTITY_MISMATCH' }
    $true
}

function New-RecI3OwnedProcessBinding([Diagnostics.Process]$Process) {
    $handle=$Process.SafeHandle
    if($null-eq$handle-or$handle.IsInvalid-or$handle.IsClosed){throw 'OWNED_PROCESS_HANDLE_INVALID'}
    $identity=ConvertTo-RecI3Identity ([DoraRecI3OwnedProcessNative]::Identity($handle)) $PID
    [ordered]@{ process=$Process; safeHandle=$handle; capturedIdentity=$identity; ancestry=$null; identitySource='RETAINED_SAFE_PROCESS_HANDLE' }
}

function Stop-RecI3OwnedProcess([object]$Binding,[int]$GraceMilliseconds=1000,[int]$ForceWaitMilliseconds=10000) {
    $h=$Binding.safeHandle;$captured=$Binding.capturedIdentity
    if([DoraRecI3OwnedProcessNative]::Wait($h,[uint32][Math]::Max(0,$GraceMilliseconds))){return [ordered]@{capturedIdentity=$captured;freshIdentity=$null;terminationAttempted=$false;terminationSucceeded=$false;terminationError=$null;absenceObserved=$true;replacementObserved=$false;unknown=$false;state='GRACEFUL_EXIT';exitCode=[DoraRecI3OwnedProcessNative]::ExitCode($h)}}
    $fresh=ConvertTo-RecI3Identity ([DoraRecI3OwnedProcessNative]::Identity($h)) ([int]$captured.parentProcessId)
    $null=Test-RecI3Identity $captured $fresh
    $error=$null;$succeeded=$false
    try{[DoraRecI3OwnedProcessNative]::Terminate($h,125);$succeeded=$true}catch{$error=$_.Exception.Message}
    $exited=[DoraRecI3OwnedProcessNative]::Wait($h,[uint32][Math]::Max(1,$ForceWaitMilliseconds))
    if(-not$exited){throw 'OWNED_PROCESS_EXIT_UNCERTAIN'}
    if($null-ne$error){return [ordered]@{capturedIdentity=$captured;freshIdentity=$fresh;terminationAttempted=$true;terminationSucceeded=$false;terminationError=$error;absenceObserved=$true;replacementObserved=$false;unknown=$false;state='EXITED_DURING_TERMINATION';exitCode=[DoraRecI3OwnedProcessNative]::ExitCode($h)}}
    [ordered]@{capturedIdentity=$captured;freshIdentity=$fresh;terminationAttempted=$true;terminationSucceeded=$succeeded;terminationError=$null;absenceObserved=$true;replacementObserved=$false;unknown=$false;state='TERMINATED';exitCode=[DoraRecI3OwnedProcessNative]::ExitCode($h)}
}

function Stop-RecI3OwnedProcessClosure([object]$RootBinding,[int]$GraceMilliseconds=1000,[int]$ForceWaitMilliseconds=10000) {
    $bindings=[Collections.Generic.List[object]]::new();$bindings.Add($RootBinding)
    $failures=[Collections.Generic.List[string]]::new()
    try{$parents=[DoraRecI3ParentSnapshot]::Parents()}catch{throw "OWNED_CLOSURE_SNAPSHOT_UNCERTAIN:$($_.Exception.Message)"}
    for($index=0;$index-lt$bindings.Count;$index++){
        $parent=$bindings[$index]
        foreach($entry in @($parents.GetEnumerator()|Where-Object{$_.Value-eq$parent.capturedIdentity.processId})){
            $p=$null
            try{
                $p=[Diagnostics.Process]::GetProcessById([int]$entry.Key);$child=New-RecI3OwnedProcessBinding $p
                $childTime=[datetime]::ParseExact($child.capturedIdentity.creationTimeUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind)
                $parentTime=[datetime]::ParseExact($parent.capturedIdentity.creationTimeUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind)
                if($childTime-lt$parentTime){throw 'OWNED_CLOSURE_TIME_CONTRADICTION'}
                $child.capturedIdentity.parentProcessId=[int]$parent.capturedIdentity.processId
                $child.ancestry=[ordered]@{parentIdentity=$parent.capturedIdentity;childIdentity=$child.capturedIdentity;capturedFrom='TOOLHELP_PARENT_SNAPSHOT'}
                $bindings.Add($child)
            }catch{if($null-ne$p){$p.Dispose()};$failures.Add("OWNED_DESCENDANT_CAPTURE_UNCERTAIN:$($entry.Key):$($_.Exception.Message)")}
        }
    }
    $results=[Collections.Generic.List[object]]::new()
    try{
        for($index=$bindings.Count-1;$index-ge0;$index--){
            try{$grace=if($index-eq0){$GraceMilliseconds}else{0};$results.Add((Stop-RecI3OwnedProcess $bindings[$index] $grace $ForceWaitMilliseconds))}
            catch{$failures.Add("OWNED_INSTANCE_CLEANUP_UNCERTAIN:$($bindings[$index].capturedIdentity.processId):$($_.Exception.Message)")}
        }
    }finally{foreach($binding in $bindings){if($binding-ne$RootBinding){try{$binding.process.Dispose()}catch{$failures.Add("OWNED_INSTANCE_DISPOSE_UNCERTAIN:$($binding.capturedIdentity.processId)")}}}}
    [ordered]@{rootIdentity=$RootBinding.capturedIdentity;capturedCount=[int]$bindings.Count;capturedAncestry=@($bindings|ForEach-Object{$_.ancestry});results=@($results);failures=@($failures);cleanupCertain=($failures.Count-eq0-and@($results|Where-Object{-not$_.absenceObserved-or$_.unknown}).Count-eq0)}
}

Export-ModuleMember -Function New-RecI3OwnedProcessBinding,Stop-RecI3OwnedProcess,Stop-RecI3OwnedProcessClosure,Test-RecI3Identity
