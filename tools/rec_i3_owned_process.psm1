Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not ('DoraRecI3OwnedProcessNative' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using System.IO;
using System.Security.Cryptography;
using Microsoft.Win32.SafeHandles;
public sealed class DoraRecI3ProcessIdentity { public int ProcessId; public long CreationFileTimeUtc; public string ExecutablePath; }
public sealed class DoraRecI3ExecutableFileIdentity { public string RawPath; public string CanonicalPath; public string ObservedNativePath; public string PhysicalId; public string Sha256; public long Length; public long LastWriteFileTimeUtc; }
public static class DoraRecI3OwnedProcessNative {
  const uint WAIT_OBJECT_0=0, WAIT_TIMEOUT=258, WAIT_FAILED=0xFFFFFFFF;
  [DllImport("kernel32.dll",SetLastError=true)] static extern uint GetProcessId(SafeProcessHandle h);
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetProcessTimes(SafeProcessHandle h,out System.Runtime.InteropServices.ComTypes.FILETIME c,out System.Runtime.InteropServices.ComTypes.FILETIME e,out System.Runtime.InteropServices.ComTypes.FILETIME k,out System.Runtime.InteropServices.ComTypes.FILETIME u);
  [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool QueryFullProcessImageNameW(SafeProcessHandle h,uint f,StringBuilder p,ref int n);
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool TerminateProcess(SafeProcessHandle h,uint c);
  [DllImport("kernel32.dll",SetLastError=true)] static extern uint WaitForSingleObject(SafeProcessHandle h,uint ms);
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetExitCodeProcess(SafeProcessHandle h,out uint c);
  [StructLayout(LayoutKind.Sequential)] struct BY_HANDLE_FILE_INFORMATION { public uint FileAttributes; public System.Runtime.InteropServices.ComTypes.FILETIME CreationTime,LastAccessTime,LastWriteTime; public uint VolumeSerialNumber,FileSizeHigh,FileSizeLow,NumberOfLinks,FileIndexHigh,FileIndexLow; }
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetFileInformationByHandle(SafeFileHandle h,out BY_HANDLE_FILE_INFORMATION i);
  [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern uint GetFinalPathNameByHandleW(SafeFileHandle h,StringBuilder p,uint n,uint f);
  static void Check(SafeProcessHandle h){if(h==null||h.IsInvalid||h.IsClosed)throw new InvalidOperationException("RETAINED_HANDLE_INVALID");}
  static long FileTime(System.Runtime.InteropServices.ComTypes.FILETIME t){return ((long)(uint)t.dwHighDateTime<<32)|(uint)t.dwLowDateTime;}
  static void CheckNoReparse(string full){string root=Path.GetPathRoot(full);string rest=full.Substring(root.Length);string current=root;foreach(string part in rest.Split(new[]{Path.DirectorySeparatorChar,Path.AltDirectorySeparatorChar},StringSplitOptions.RemoveEmptyEntries)){current=Path.Combine(current,part);FileAttributes a=File.GetAttributes(current);if((a&FileAttributes.ReparsePoint)!=0)throw new InvalidOperationException("OWNED_PROCESS_EXECUTABLE_REPARSE_PATH:"+current);}}
  static BY_HANDLE_FILE_INFORMATION FileInfo(SafeFileHandle h){BY_HANDLE_FILE_INFORMATION i;if(!GetFileInformationByHandle(h,out i))throw new Win32Exception(Marshal.GetLastWin32Error(),"OWNED_PROCESS_EXECUTABLE_FILE_INFO_FAILED");return i;}
  static string FinalPath(SafeFileHandle h){var b=new StringBuilder(32768);uint n=GetFinalPathNameByHandleW(h,b,(uint)b.Capacity,0);if(n==0||n>=(uint)b.Capacity)throw new Win32Exception(Marshal.GetLastWin32Error(),"OWNED_PROCESS_EXECUTABLE_FINAL_PATH_FAILED");string p=b.ToString();if(p.StartsWith(@"\\?\UNC\",StringComparison.OrdinalIgnoreCase))return @"\\"+p.Substring(8);if(p.StartsWith(@"\\?\",StringComparison.OrdinalIgnoreCase))return p.Substring(4);return p;}
  public static DoraRecI3ExecutableFileIdentity ExecutableFile(string raw){if(raw==null)throw new ArgumentNullException("raw");string full=Path.GetFullPath(raw);if(Directory.Exists(full))throw new InvalidOperationException("OWNED_PROCESS_EXECUTABLE_NOT_REGULAR_FILE:"+full);if(!File.Exists(full))throw new FileNotFoundException("OWNED_PROCESS_EXECUTABLE_MISSING",full);CheckNoReparse(full);try{using(var stream=new FileStream(full,FileMode.Open,FileAccess.Read,FileShare.Read,65536,FileOptions.SequentialScan)){var before=FileInfo(stream.SafeFileHandle);string observed=FinalPath(stream.SafeFileHandle);using(var sha=SHA256.Create()){byte[] digest=sha.ComputeHash(stream);var after=FileInfo(stream.SafeFileHandle);CheckNoReparse(full);if(before.VolumeSerialNumber!=after.VolumeSerialNumber||before.FileIndexHigh!=after.FileIndexHigh||before.FileIndexLow!=after.FileIndexLow||before.FileSizeHigh!=after.FileSizeHigh||before.FileSizeLow!=after.FileSizeLow||FileTime(before.LastWriteTime)!=FileTime(after.LastWriteTime))throw new InvalidOperationException("OWNED_PROCESS_EXECUTABLE_RACE_UNCERTAIN");return new DoraRecI3ExecutableFileIdentity{RawPath=raw,CanonicalPath=full,ObservedNativePath=observed,PhysicalId=before.VolumeSerialNumber.ToString("X8")+":"+(((ulong)before.FileIndexHigh<<32)|before.FileIndexLow).ToString("X16"),Sha256=BitConverter.ToString(digest).Replace("-","").ToUpperInvariant(),Length=((long)before.FileSizeHigh<<32)|before.FileSizeLow,LastWriteFileTimeUtc=FileTime(before.LastWriteTime)};}}}catch(FileNotFoundException){throw;}catch(InvalidOperationException){throw;}catch(Exception e){throw new IOException("OWNED_PROCESS_EXECUTABLE_OPEN_FAILED:"+full,e);}}
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
    $executableBinding=Get-RecI3ExecutableFileBinding ([string]$Native.ExecutablePath) $null 'RETAINED_NATIVE_PROCESS_IMAGE'
    [ordered]@{ processId=[int]$Native.ProcessId; parentProcessId=$ParentProcessId; executablePath=[string]$Native.ExecutablePath; executableBinding=$executableBinding; executablePhysicalId=$executableBinding.physicalId; executableSha256=$executableBinding.sha256; creationTimeUtc=[DateTime]::FromFileTimeUtc([int64]$Native.CreationFileTimeUtc).ToString('o'); creationFileTimeUtc=[int64]$Native.CreationFileTimeUtc }
}

function Get-RecI3RequiredScalarString([object]$Record,[string]$Name) {
    $present=$false;$value=$null
    if($Record-is[Collections.IDictionary]){$present=$Record.Contains($Name);if($present){$value=$Record[$Name]}}else{$property=$Record.PSObject.Properties[$Name];$present=$null-ne$property;if($present){$value=$property.Value}}
    if(-not$present-or$value-isnot[string]-or[string]::IsNullOrWhiteSpace([string]$value)){throw "OWNED_PROCESS_EXECUTABLE_DECLARATION_INVALID:$Name"}
    [string]$value
}

function Get-RecI3ExecutableFileBinding([object]$Path,[object]$ExpectedSha256=$null,[object]$Domain='UNSPECIFIED') {
    if($Path-isnot[string]-or[string]::IsNullOrWhiteSpace([string]$Path)){throw 'OWNED_PROCESS_EXECUTABLE_PATH_INVALID'}
    if($Domain-isnot[string]-or[string]::IsNullOrWhiteSpace([string]$Domain)){throw 'OWNED_PROCESS_EXECUTABLE_DOMAIN_INVALID'}
    if($null-ne$ExpectedSha256-and($ExpectedSha256-isnot[string]-or[string]$ExpectedSha256-cnotmatch'^[0-9A-Fa-f]{64}$')){throw 'OWNED_PROCESS_EXECUTABLE_PIN_INVALID'}
    try{$native=[DoraRecI3OwnedProcessNative]::ExecutableFile([string]$Path)}catch{$cause=$_.Exception;while($null-ne$cause.InnerException){$cause=$cause.InnerException};if($cause.Message-cmatch'^OWNED_PROCESS_EXECUTABLE_'){throw $cause.Message};if($cause-is[IO.FileNotFoundException]){throw "OWNED_PROCESS_EXECUTABLE_MISSING:$Path"};throw "OWNED_PROCESS_EXECUTABLE_OPEN_FAILED:${Path}:$($cause.Message)"}
    $hash=([string]$native.Sha256).ToUpperInvariant()
    if($null-ne$ExpectedSha256-and$hash-cne([string]$ExpectedSha256).ToUpperInvariant()){throw "OWNED_PROCESS_EXECUTABLE_PIN_MISMATCH:$Path"}
    [ordered]@{rawPath=[string]$Path;canonicalPath=[string]$native.CanonicalPath;observedNativePath=[string]$native.ObservedNativePath;physicalId=[string]$native.PhysicalId;sha256=$hash;length=[int64]$native.Length;lastWriteFileTimeUtc=[int64]$native.LastWriteFileTimeUtc;domain=[string]$Domain;pinValidated=($null-ne$ExpectedSha256)}
}

function Confirm-RecI3ExecutableFileBinding([object]$Binding,[object]$ExpectedSha256=$null) {
    if($null-eq$Binding){throw 'OWNED_PROCESS_EXECUTABLE_DECLARATION_INVALID:binding'}
    $rawPath=Get-RecI3RequiredScalarString $Binding 'rawPath';$canonicalPath=Get-RecI3RequiredScalarString $Binding 'canonicalPath';$observedNativePath=Get-RecI3RequiredScalarString $Binding 'observedNativePath';$physicalId=Get-RecI3RequiredScalarString $Binding 'physicalId';$sha256=Get-RecI3RequiredScalarString $Binding 'sha256';$domain=Get-RecI3RequiredScalarString $Binding 'domain'
    if($physicalId-cnotmatch'^[0-9A-F]{8}:[0-9A-F]{16}$'-or$sha256-cnotmatch'^[0-9A-F]{64}$'){throw 'OWNED_PROCESS_EXECUTABLE_DECLARATION_INVALID:identity'}
    if($Binding-is[Collections.IDictionary]){$pinPresent=$Binding.Contains('pinValidated');$pinValue=if($pinPresent){$Binding['pinValidated']}else{$null}}else{$pinProperty=$Binding.PSObject.Properties['pinValidated'];$pinPresent=$null-ne$pinProperty;$pinValue=if($pinPresent){$pinProperty.Value}else{$null}}
    if(-not$pinPresent-or$pinValue-isnot[bool]){throw 'OWNED_PROCESS_EXECUTABLE_DECLARATION_INVALID:pinValidated'}
    $fresh=Get-RecI3ExecutableFileBinding $rawPath $ExpectedSha256 $domain
    if($canonicalPath-cne$fresh.canonicalPath-or$observedNativePath-cne$fresh.observedNativePath-or$physicalId-cne$fresh.physicalId-or$sha256-cne$fresh.sha256){throw 'OWNED_PROCESS_EXECUTABLE_DECLARATION_MISMATCH'}
    $fresh
}

function Test-RecI3ExecutableFileBinding([object]$Expected,[object]$Actual,[object]$ExpectedSha256=$null) {
    $left=Confirm-RecI3ExecutableFileBinding $Expected $ExpectedSha256;$right=Confirm-RecI3ExecutableFileBinding $Actual $ExpectedSha256
    if($left.physicalId-cne$right.physicalId-or$left.sha256-cne$right.sha256){throw 'OWNED_PROCESS_EXECUTABLE_IDENTITY_MISMATCH'}
    $true
}

function ConvertTo-RecI3WindowsCommandLine([object[]]$Arguments) {
    $encoded=[Collections.Generic.List[string]]::new()
    foreach($argument in $Arguments){
        if($null-eq$argument-or$argument-isnot[string]){throw 'OWNED_PROCESS_ARGUMENT_INVALID'}
        $value=[string]$argument
        if($value.Length-gt0-and$value-cnotmatch'[\s"]'){$encoded.Add($value);continue}
        $builder=[Text.StringBuilder]::new();$null=$builder.Append('"');$slashes=0
        foreach($character in $value.ToCharArray()){
            if($character-eq'\'){$slashes++;continue}
            if($character-eq'"'){$null=$builder.Append(('\'*(2*$slashes+1)));$null=$builder.Append('"');$slashes=0;continue}
            if($slashes){$null=$builder.Append(('\'*$slashes));$slashes=0};$null=$builder.Append($character)
        }
        if($slashes){$null=$builder.Append(('\'*(2*$slashes)))};$null=$builder.Append('"');$encoded.Add($builder.ToString())
    }
    $encoded -join ' '
}

function Get-RecI3PropertyValue([object]$Record,[string]$Name,[bool]$AllowArray=$false) {
    if($null-eq$Record){throw "OWNED_PROCESS_DECLARATION_MISSING:$Name"}
    if($Record-is[Collections.IDictionary]){if(-not$Record.Contains($Name)){throw "OWNED_PROCESS_DECLARATION_MISSING:$Name"};$value=$Record[$Name]}
    else{$property=$Record.PSObject.Properties[$Name];if($null-eq$property){throw "OWNED_PROCESS_DECLARATION_MISSING:$Name"};$value=$property.Value}
    if(-not$AllowArray-and$value-is[array]){throw "OWNED_PROCESS_DECLARATION_CARDINALITY_INVALID:$Name"};$value
}
function Get-RecI3ArrayProperty([object]$Record,[string]$Name) {
    if($null-eq$Record){throw "OWNED_PROCESS_DECLARATION_MISSING:$Name"}
    if($Record-is[Collections.IDictionary]){if(-not$Record.Contains($Name)){throw "OWNED_PROCESS_DECLARATION_MISSING:$Name"};$value=$Record[$Name]}
    else{$property=$Record.PSObject.Properties[$Name];if($null-eq$property){throw "OWNED_PROCESS_DECLARATION_MISSING:$Name"};$value=$property.Value}
    if($value-isnot[array]){throw "$Name must be an array"};Write-Output -NoEnumerate $value
}

function Test-RecI3NullableString([object]$Value,[string]$Name) {if($null-ne$Value-and($Value-isnot[string]-or[string]::IsNullOrWhiteSpace([string]$Value))){throw "$Name must be null or a nonempty string"}}
function Test-RecI3SerializedInteger([object]$Value,[string]$Name) {if(($Value-isnot[int])-and($Value-isnot[long])){throw "$Name must be an integer"};[int64]$Value}
function Test-RecI3SerializedBoolean([object]$Value,[string]$Name) {if($Value-isnot[bool]){throw "$Name must be Boolean"};[bool]$Value}

function Test-RecI3SerializedProcessIdentity([object]$Identity,[string]$Name) {
    $pidValue=Test-RecI3SerializedInteger (Get-RecI3PropertyValue $Identity 'processId') "$Name.processId";if($pidValue-le0-or$pidValue-gt[int]::MaxValue){throw "$Name.processId out of range"}
    $creationValue=Test-RecI3SerializedInteger (Get-RecI3PropertyValue $Identity 'creationFileTimeUtc') "$Name.creationFileTimeUtc"
    $creationText=Get-RecI3RequiredScalarString $Identity 'creationTimeUtc';$parsed=[datetime]::MinValue
    if(-not[datetime]::TryParseExact($creationText,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind,[ref]$parsed)-or$parsed.Kind-ne[DateTimeKind]::Utc-or$parsed.ToFileTimeUtc()-ne$creationValue-or$parsed.ToString('o',[Globalization.CultureInfo]::InvariantCulture)-cne$creationText){throw "$Name creation identity invalid"}
    [ordered]@{processId=[int]$pidValue;creationFileTimeUtc=[int64]$creationValue;creationTimeUtc=$creationText}
}

function Test-RecI3SerializedBoundProcessIdentity([object]$Identity,[string]$Name) {
    $instance=Test-RecI3SerializedProcessIdentity $Identity $Name
    $binding=Get-RecI3PropertyValue $Identity 'executableBinding';$observed=Confirm-RecI3ExecutableFileBinding $binding $binding.sha256
    if((Get-RecI3RequiredScalarString $Identity 'executablePath')-cne$observed.rawPath-or(Get-RecI3RequiredScalarString $Identity 'executablePhysicalId')-cne$observed.physicalId-or(Get-RecI3RequiredScalarString $Identity 'executableSha256')-cne$observed.sha256){throw "$Name executable identity invalid"}
    $instance.executableBinding=$observed;$instance.executablePath=$observed.rawPath;$instance.executablePhysicalId=$observed.physicalId;$instance.executableSha256=$observed.sha256;$instance
}

function Test-RecI3SerializedCleanup([object]$Cleanup,[object]$ExpectedRootIdentity=$null) {
    $certain=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Cleanup 'cleanupCertain') 'cleanupCertain';$audit=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Cleanup 'laterAuditRequired') 'laterAuditRequired';$rootAbsent=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Cleanup 'rootAbsenceObserved') 'rootAbsenceObserved';$null=Get-RecI3RequiredScalarString $Cleanup 'state'
    $rootValue=Get-RecI3PropertyValue $Cleanup 'rootIdentity';$root=$null;if($null-ne$rootValue){$root=Test-RecI3SerializedBoundProcessIdentity $rootValue 'cleanup.rootIdentity';if($null-ne$ExpectedRootIdentity){$null=Test-RecI3Identity $ExpectedRootIdentity $rootValue}}elseif($null-ne$ExpectedRootIdentity){throw 'cleanup root identity missing'}
    if($rootAbsent-and$null-eq$root){throw 'cleanup root absence identity missing'}
    $failures=Get-RecI3ArrayProperty $Cleanup 'failures';foreach($failure in $failures){if($failure-isnot[string]-or[string]::IsNullOrWhiteSpace($failure)){throw 'cleanup failure invalid'}}
    $results=Get-RecI3ArrayProperty $Cleanup 'results'
    $rootMatches=0;$rootAbsentMatches=0;$allAbsent=$true
    foreach($result in $results){
        $capturedValue=Get-RecI3PropertyValue $result 'capturedIdentity';$captured=Test-RecI3SerializedBoundProcessIdentity $capturedValue 'cleanup.capturedIdentity';$isRootResult=$false;if($null-ne$root){try{$null=Test-RecI3Identity $rootValue $capturedValue;$rootMatches++;$isRootResult=$true}catch{}}
        $fresh=Get-RecI3PropertyValue $result 'freshIdentity';if($null-ne$fresh){$null=Test-RecI3SerializedBoundProcessIdentity $fresh 'cleanup.freshIdentity';$null=Test-RecI3Identity $capturedValue $fresh}
        $attempted=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $result 'terminationAttempted') 'terminationAttempted';$succeeded=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $result 'terminationSucceeded') 'terminationSucceeded';$absent=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $result 'absenceObserved') 'absenceObserved';$unknown=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $result 'unknown') 'unknown';$null=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $result 'replacementObserved') 'replacementObserved';$null=Get-RecI3RequiredScalarString $result 'state'
        $terminationError=Get-RecI3PropertyValue $result 'terminationError';Test-RecI3NullableString $terminationError 'terminationError';$exitError=Get-RecI3PropertyValue $result 'exitCodeCaptureError';Test-RecI3NullableString $exitError 'exitCodeCaptureError';$resultExit=Get-RecI3PropertyValue $result 'exitCode';if($null-ne$resultExit){$null=Test-RecI3SerializedInteger $resultExit 'cleanup.exitCode'}
        $state=Get-RecI3RequiredScalarString $result 'state';$replacement=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $result 'replacementObserved') 'replacementObserved'
        if($succeeded-and-not$attempted-or$unknown-and$absent-or$replacement-or$attempted-and$null-eq$fresh-or-not$attempted-and$null-ne$fresh){throw 'cleanup result relationship contradictory'}
        $observedExitState=($state-cmatch'^(?:GRACEFUL_EXIT|TERMINATED|EXITED_DURING_TERMINATION)$');$uncertainExitState=($state-cmatch'^(?:GRACEFUL_EXIT_CODE_UNCERTAIN|TERMINATED_EXIT_CODE_UNCERTAIN|EXITED_DURING_TERMINATION_EXIT_CODE_UNCERTAIN)$')
        if($observedExitState){if($null-eq$resultExit-or$null-ne$exitError-or$resultExit-lt[int]::MinValue-or$resultExit-gt[int]::MaxValue){throw 'cleanup observed exit outcome contradictory'}}elseif($uncertainExitState){if($null-ne$resultExit-or$null-eq$exitError){throw 'cleanup uncertain exit outcome contradictory'}}elseif($null-ne$resultExit-or$null-ne$exitError){throw 'cleanup unobserved exit outcome contradictory'}
        switch -Regex ($state) {
            '^GRACEFUL_EXIT(?:_CODE_UNCERTAIN)?$' {if($attempted-or$succeeded-or-not$absent-or$unknown-or$null-ne$terminationError){throw 'graceful cleanup result contradictory'};break}
            '^TERMINATED(?:_EXIT_CODE_UNCERTAIN)?$' {if(-not$attempted-or-not$succeeded-or-not$absent-or$unknown-or$null-ne$terminationError){throw 'terminated cleanup result contradictory'};break}
            '^EXITED_DURING_TERMINATION(?:_EXIT_CODE_UNCERTAIN)?$' {if(-not$attempted-or$succeeded-or-not$absent-or$unknown-or$null-eq$terminationError){throw 'termination-race result contradictory'};break}
            '^TERMINATION_EXIT_UNCERTAIN$' {if(-not$attempted-or$absent-or-not$unknown-or$null-eq$terminationError){throw 'uncertain termination result contradictory'};break}
            '^NOT_ATTEMPTED_BUDGET_EXHAUSTED$' {if($attempted-or$succeeded-or$absent-or-not$unknown-or$null-eq$terminationError){throw 'budget result contradictory'};break}
            '^CLEANUP_UNCERTAIN$' {if($attempted-or$succeeded-or$absent-or-not$unknown-or$null-eq$terminationError){throw 'cleanup-uncertain result contradictory'};break}
            default {throw 'cleanup result state invalid'}
        }
        if($isRootResult-and$absent-and-not$unknown-and-not$replacement){$rootAbsentMatches++}
        if(-not$absent-or$unknown){$allAbsent=$false}
    }
    $directUnaudited=($results.Count-eq0-and-not$certain-and$audit-and$rootAbsent-and(Get-RecI3RequiredScalarString $Cleanup 'state')-ceq'TARGET_ROOT_EXITED_CLOSURE_UNAUDITED')
    if($rootMatches-gt1-or-not$directUnaudited-and(($rootAbsent-and($rootMatches-ne1-or$rootAbsentMatches-ne1))-or(-not$rootAbsent-and$rootAbsentMatches-ne0))){throw 'cleanup root absence evidence contradictory'}
    if($certain){if($audit-or-not$rootAbsent-or$failures.Count-ne0-or$results.Count-eq0-or-not$allAbsent-or$rootMatches-ne1){throw 'certain cleanup evidence contradictory'}}else{if(-not$audit-or$failures.Count-eq0){throw 'uncertain cleanup evidence contradictory'}}
    $true
}

function New-RecI3UnknownTargetCleanup([object]$TargetIdentity,[string]$Failure) {
    [ordered]@{rootIdentity=$TargetIdentity;cleanupCertain=$false;laterAuditRequired=$true;rootAbsenceObserved=$false;failures=@($Failure);results=@();state='TARGET_CLEANUP_UNCERTAIN'}
}

function ConvertFrom-RecI3WrapperClosureTargetCleanup([object]$TargetIdentity,[object]$WrapperCleanup) {
    try{$null=Test-RecI3SerializedBoundProcessIdentity $TargetIdentity 'targetIdentity'}catch{return New-RecI3UnknownTargetCleanup $TargetIdentity "TARGET_IDENTITY_INVALID:$($_.Exception.Message)"}
    if($null-eq$WrapperCleanup){return New-RecI3UnknownTargetCleanup $TargetIdentity 'WRAPPER_CLOSURE_UNAVAILABLE'}
    try{$results=Get-RecI3ArrayProperty $WrapperCleanup 'results'}catch{return New-RecI3UnknownTargetCleanup $TargetIdentity "WRAPPER_CLOSURE_RESULTS_INVALID:$($_.Exception.Message)"}
    $targetMatches=[Collections.Generic.List[object]]::new()
    foreach($result in $results){try{$captured=Get-RecI3PropertyValue $result 'capturedIdentity';$null=Test-RecI3SerializedBoundProcessIdentity $captured 'wrapperCleanup.capturedIdentity';$null=Test-RecI3Identity $TargetIdentity $captured;$targetMatches.Add($result)}catch{}}
    if($targetMatches.Count-ne1){return New-RecI3UnknownTargetCleanup $TargetIdentity $(if($targetMatches.Count-eq0){'TARGET_NOT_RECONCILED_IN_WRAPPER_CLOSURE'}else{'TARGET_CLOSURE_RESULT_AMBIGUOUS'})}
    $match=$targetMatches[0]
    try{
        $absent=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $match 'absenceObserved') 'absenceObserved';$unknown=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $match 'unknown') 'unknown';$replacement=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $match 'replacementObserved') 'replacementObserved'
        $targetAbsent=($absent-and-not$unknown-and-not$replacement)
        $targetFailures=$(if($targetAbsent){@()}else{@('TARGET_CLOSURE_RESULT_UNCERTAIN')})
        $targetOnly=[ordered]@{rootIdentity=$TargetIdentity;cleanupCertain=$targetAbsent;laterAuditRequired=(-not$targetAbsent);rootAbsenceObserved=$targetAbsent;failures=@($targetFailures);results=@($match);state=$(if($targetAbsent){'TARGET_CLEANUP_MAPPED_COMPLETE'}else{'TARGET_CLEANUP_MAPPED_UNCERTAIN'})}
        $null=Test-RecI3SerializedCleanup $targetOnly $TargetIdentity
    }catch{return New-RecI3UnknownTargetCleanup $TargetIdentity "TARGET_CLOSURE_RESULT_INVALID:$($_.Exception.Message)"}
    $wrapperCoverageCertain=$false;$coverageFailures=[Collections.Generic.List[string]]::new()
    try{
        $null=Test-RecI3SerializedCleanup $WrapperCleanup
        $wrapperClaimedCertain=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $WrapperCleanup 'cleanupCertain') 'wrapperCleanup.cleanupCertain'
        $capturedCount=Test-RecI3SerializedInteger (Get-RecI3PropertyValue $WrapperCleanup 'capturedCount') 'wrapperCleanup.capturedCount';$unresolved=Get-RecI3ArrayProperty $WrapperCleanup 'unresolvedIdentities';foreach($identity in $unresolved){$null=Test-RecI3SerializedBoundProcessIdentity $identity 'wrapperCleanup.unresolvedIdentity'}
        $ancestry=Get-RecI3ArrayProperty $WrapperCleanup 'capturedAncestry';$budgetExhausted=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $WrapperCleanup 'budgetExhausted') 'wrapperCleanup.budgetExhausted';$snapshotPasses=Test-RecI3SerializedInteger (Get-RecI3PropertyValue $WrapperCleanup 'snapshotPasses') 'wrapperCleanup.snapshotPasses';$stablePasses=Test-RecI3SerializedInteger (Get-RecI3PropertyValue $WrapperCleanup 'stablePasses') 'wrapperCleanup.stablePasses';$shutdownBudget=Test-RecI3SerializedInteger (Get-RecI3PropertyValue $WrapperCleanup 'shutdownBudgetMilliseconds') 'wrapperCleanup.shutdownBudgetMilliseconds'
        if($capturedCount-lt1-or$capturedCount-ne($results.Count+$unresolved.Count)-or$ancestry.Count-ne$capturedCount-or$snapshotPasses-lt0-or$stablePasses-lt0-or$stablePasses-gt$snapshotPasses-or$shutdownBudget-lt1){throw 'wrapper closure accounting contradictory'}
        if($wrapperClaimedCertain-and($unresolved.Count-ne0-or$budgetExhausted-or$stablePasses-lt2)){throw 'wrapper closure certainty accounting contradictory'}
        $wrapperCoverageCertain=($wrapperClaimedCertain-and$unresolved.Count-eq0-and-not$budgetExhausted)
        if(-not$wrapperCoverageCertain){foreach($failure in (Get-RecI3ArrayProperty $WrapperCleanup 'failures')){$coverageFailures.Add([string]$failure)}}
    }catch{$coverageFailures.Add("WRAPPER_CLOSURE_INVALID:$($_.Exception.Message)")}
    $certain=($targetAbsent-and$wrapperCoverageCertain)
    if(-not$certain){if(-not$targetAbsent){$coverageFailures.Add('TARGET_CLOSURE_RESULT_UNCERTAIN')};$coverageFailures.Add('WRAPPER_CLOSURE_COVERAGE_UNCERTAIN')}
    $mapped=[ordered]@{rootIdentity=$TargetIdentity;cleanupCertain=$certain;laterAuditRequired=(-not$certain);rootAbsenceObserved=$targetAbsent;failures=@($coverageFailures);results=@($match);state=if($certain){'TARGET_CLEANUP_MAPPED_COMPLETE'}else{'TARGET_CLEANUP_MAPPED_UNCERTAIN'}}
    try{$null=Test-RecI3SerializedCleanup $mapped $TargetIdentity;$mapped}catch{return New-RecI3UnknownTargetCleanup $TargetIdentity "TARGET_CLOSURE_MAPPING_INVALID:$($_.Exception.Message)"}
}

function Test-RecI3ObservedTargetBindingEvidence([object]$Receipt,[object]$ExpectedConfiguredBinding,[object]$ExpectedInvocationArtifactBinding) {
    $started=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Receipt 'targetStarted') 'targetStarted';if(-not$started){throw 'observed target receipt cannot be not-started'}
    $configured=Confirm-RecI3ExecutableFileBinding $ExpectedConfiguredBinding $ExpectedConfiguredBinding.sha256
    $artifact=Confirm-RecI3ExecutableFileBinding $ExpectedInvocationArtifactBinding $ExpectedInvocationArtifactBinding.sha256
    $declaredConfigured=Get-RecI3PropertyValue $Receipt 'targetConfiguredBinding';$null=Test-RecI3ExecutableFileBinding $configured $declaredConfigured $configured.sha256
    if((Get-RecI3RequiredScalarString $Receipt 'targetConfiguredPath')-cne$configured.rawPath){throw 'targetConfiguredPath mismatch'}
    $declaredArtifact=Get-RecI3PropertyValue $Receipt 'invocationArtifactBinding';$null=Test-RecI3ExecutableFileBinding $artifact $declaredArtifact $artifact.sha256
    if((Get-RecI3RequiredScalarString $Receipt 'invocationArtifactPath')-cne$artifact.rawPath){throw 'invocationArtifactPath mismatch'}
    $targetProcessId=Test-RecI3SerializedInteger (Get-RecI3PropertyValue $Receipt 'targetProcessId') 'targetProcessId';if($targetProcessId-le0-or$targetProcessId-gt[int]::MaxValue){throw 'targetProcessId must be a positive Int32'}
    $proof=Get-RecI3PropertyValue $Receipt 'targetBindingProof';$verified=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $proof 'verified') 'targetBindingProof.verified';if(-not$verified){throw 'target binding proof is not verified'};$proofStage=Get-RecI3RequiredScalarString $proof 'stage';if($proofStage-cnotmatch'^CONFIGURED_TO_RETAINED_NATIVE_VERIFIED(?:;INVENTORY_VERIFIED)?$'){throw 'target binding proof stage invalid'};$proofError=Get-RecI3PropertyValue $proof 'error';if($null-ne$proofError){throw 'verified target binding proof has error'}
    $nativeBinding=Get-RecI3PropertyValue $Receipt 'targetNativeBinding';$null=Test-RecI3ExecutableFileBinding $configured $nativeBinding $configured.sha256
    $nativeIdentity=Get-RecI3PropertyValue $Receipt 'targetNativeIdentity';$nativeInstance=Test-RecI3SerializedProcessIdentity $nativeIdentity 'targetNativeIdentity';if($nativeInstance.processId-ne[int]$targetProcessId){throw 'target native PID mismatch'}
    if((Get-RecI3RequiredScalarString $nativeIdentity 'executablePath')-cne(Get-RecI3RequiredScalarString $nativeBinding 'rawPath')){throw 'target native path mismatch'}
    if((Get-RecI3RequiredScalarString $nativeIdentity 'executablePhysicalId')-cne(Get-RecI3RequiredScalarString $nativeBinding 'physicalId')-or(Get-RecI3RequiredScalarString $nativeIdentity 'executableSha256')-cne(Get-RecI3RequiredScalarString $nativeBinding 'sha256')){throw 'target native identity binding mismatch'}
    $null=Test-RecI3ExecutableFileBinding (Get-RecI3PropertyValue $nativeIdentity 'executableBinding') $nativeBinding $configured.sha256
    $true
}

function Confirm-RecI3ObservedTargetStartedReceipt([object]$Receipt,[object]$ExpectedConfiguredBinding,[object]$ExpectedInvocationArtifactBinding) {
    try {
        if((Get-RecI3RequiredScalarString $Receipt 'receiptPhase')-cne'STARTED'){throw 'receiptPhase must be STARTED'}
        $null=Test-RecI3ObservedTargetBindingEvidence $Receipt $ExpectedConfiguredBinding $ExpectedInvocationArtifactBinding
        foreach($name in @('rawExitValue','rawExitType','exitCode','targetExitCaptureError','targetCleanup','targetStdoutComplete','targetStderrComplete','targetStdoutReadError','targetStderrReadError','targetDisposeError','firstFailure','launchFailure')){if($null-ne(Get-RecI3PropertyValue $Receipt $name)){throw "started receipt contains terminal evidence:$name"}}
        $true
    } catch { throw "OWNED_PROCESS_TARGET_STARTED_RECEIPT_INVALID:$($_.Exception.Message)" }
}

function Confirm-RecI3ObservedTargetFailureReceipt([object]$Receipt,[object]$ExpectedConfiguredBinding,[object]$ExpectedInvocationArtifactBinding) {
    try {
        if((Get-RecI3RequiredScalarString $Receipt 'receiptPhase')-cne'COMPLETED'){throw 'receiptPhase must be COMPLETED'}
        $started=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Receipt 'targetStarted') 'targetStarted';if(-not$started){throw 'failure receipt did not observe a started target'}
        $configured=Confirm-RecI3ExecutableFileBinding $ExpectedConfiguredBinding $ExpectedConfiguredBinding.sha256;$artifact=Confirm-RecI3ExecutableFileBinding $ExpectedInvocationArtifactBinding $ExpectedInvocationArtifactBinding.sha256
        $null=Test-RecI3ExecutableFileBinding $configured (Get-RecI3PropertyValue $Receipt 'targetConfiguredBinding') $configured.sha256;$null=Test-RecI3ExecutableFileBinding $artifact (Get-RecI3PropertyValue $Receipt 'invocationArtifactBinding') $artifact.sha256
        if((Get-RecI3RequiredScalarString $Receipt 'targetConfiguredPath')-cne$configured.rawPath-or(Get-RecI3RequiredScalarString $Receipt 'invocationArtifactPath')-cne$artifact.rawPath){throw 'configured path mismatch'}
        $pidValue=Test-RecI3SerializedInteger (Get-RecI3PropertyValue $Receipt 'targetProcessId') 'targetProcessId';if($pidValue-le0-or$pidValue-gt[int]::MaxValue){throw 'targetProcessId invalid'}
        $proof=Get-RecI3PropertyValue $Receipt 'targetBindingProof';if(Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $proof 'verified') 'targetBindingProof.verified'){throw 'failure receipt cannot claim verified binding'};if((Get-RecI3RequiredScalarString $proof 'stage')-cne'TARGET_BINDING'){throw 'failure proof stage invalid'};Test-RecI3NullableString (Get-RecI3PropertyValue $proof 'error') 'targetBindingProof.error';if($null-eq(Get-RecI3PropertyValue $proof 'error')){throw 'failure proof error missing'}
        foreach($name in @('targetNativeIdentity','targetNativeBinding','rawExitValue','rawExitType','exitCode')){if($null-ne(Get-RecI3PropertyValue $Receipt $name)){throw "failure receipt contains unsupported evidence:$name"}}
        $exitError=Get-RecI3PropertyValue $Receipt 'targetExitCaptureError';Test-RecI3NullableString $exitError 'targetExitCaptureError';if($null-eq$exitError){throw 'failure receipt exit uncertainty missing'}
        $null=Test-RecI3SerializedCleanup (Get-RecI3PropertyValue $Receipt 'targetCleanup') $null
        foreach($streamName in @('targetStdout','targetStderr')){$complete=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Receipt "${streamName}Complete") "${streamName}Complete";$readError=Get-RecI3PropertyValue $Receipt "${streamName}ReadError";Test-RecI3NullableString $readError "${streamName}ReadError";if($complete-and$null-ne$readError-or-not$complete-and$null-eq$readError){throw "$streamName completion relationship contradictory"}}
        Test-RecI3NullableString (Get-RecI3PropertyValue $Receipt 'targetDisposeError') 'targetDisposeError';$null=Get-RecI3RequiredScalarString $Receipt 'firstFailure';$null=Get-RecI3RequiredScalarString $Receipt 'launchFailure'
        $true
    } catch { throw "OWNED_PROCESS_TARGET_FAILURE_RECEIPT_INVALID:$($_.Exception.Message)" }
}

function Confirm-RecI3ObservedTargetReceipt([object]$Receipt,[object]$ExpectedConfiguredBinding,[object]$ExpectedInvocationArtifactBinding) {
    try {
        if((Get-RecI3RequiredScalarString $Receipt 'receiptPhase')-cne'COMPLETED'){throw 'receiptPhase must be COMPLETED'}
        $null=Test-RecI3ObservedTargetBindingEvidence $Receipt $ExpectedConfiguredBinding $ExpectedInvocationArtifactBinding;$nativeIdentity=Get-RecI3PropertyValue $Receipt 'targetNativeIdentity'
        $rawExit=Get-RecI3PropertyValue $Receipt 'rawExitValue';$rawExitType=Get-RecI3PropertyValue $Receipt 'rawExitType';$exitCode=Get-RecI3PropertyValue $Receipt 'exitCode';$exitError=Get-RecI3PropertyValue $Receipt 'targetExitCaptureError';Test-RecI3NullableString $exitError 'targetExitCaptureError'
        if($null-ne$exitCode){$serializedExit=Test-RecI3SerializedInteger $exitCode 'exitCode';if($serializedExit-lt[int]::MinValue-or$serializedExit-gt[int]::MaxValue-or$rawExitType-isnot[string]-or$rawExitType-cne'System.Int32'-or(Test-RecI3SerializedInteger $rawExit 'rawExitValue')-ne$serializedExit-or$null-ne$exitError){throw 'target exit evidence contradictory'}}else{if($null-ne$rawExit-or$null-ne$rawExitType-or$null-eq$exitError){throw 'missing target exit evidence contradictory'}}
        $null=Test-RecI3SerializedCleanup (Get-RecI3PropertyValue $Receipt 'targetCleanup') $nativeIdentity
        foreach($streamName in @('targetStdout','targetStderr')){$complete=Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Receipt "${streamName}Complete") "${streamName}Complete";$readError=Get-RecI3PropertyValue $Receipt "${streamName}ReadError";Test-RecI3NullableString $readError "${streamName}ReadError";if($complete-and$null-ne$readError-or-not$complete-and$null-eq$readError){throw "$streamName completion relationship contradictory"}}
        $disposeError=Get-RecI3PropertyValue $Receipt 'targetDisposeError';Test-RecI3NullableString $disposeError 'targetDisposeError';$firstFailure=Get-RecI3PropertyValue $Receipt 'firstFailure';Test-RecI3NullableString $firstFailure 'firstFailure';$launchFailure=Get-RecI3PropertyValue $Receipt 'launchFailure';Test-RecI3NullableString $launchFailure 'launchFailure'
        if(($null-ne$exitError-or$null-ne$disposeError-or-not(Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Receipt 'targetStdoutComplete') 'targetStdoutComplete')-or-not(Test-RecI3SerializedBoolean (Get-RecI3PropertyValue $Receipt 'targetStderrComplete') 'targetStderrComplete'))-and($null-eq$firstFailure-or$null-eq$launchFailure)){throw 'terminal failure not preserved'}
        $true
    } catch { throw "OWNED_PROCESS_TARGET_RECEIPT_INVALID:$($_.Exception.Message)" }
}

function Test-RecI3Identity([object]$Expected,[object]$Actual) {
    if ($Expected.processId -ne $Actual.processId) { throw 'OWNED_PROCESS_IDENTITY_MISMATCH' }
    $a=[datetime]::ParseExact([string]$Expected.creationTimeUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind).Ticks
    $b=[datetime]::ParseExact([string]$Actual.creationTimeUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind).Ticks
    if (($a-($a%10)) -ne ($b-($b%10))) { throw 'OWNED_PROCESS_IDENTITY_MISMATCH' }
    $expectedBinding=if($null-ne$Expected.executableBinding){if([string]$Expected.executablePath-cne[string]$Expected.executableBinding.rawPath){throw 'OWNED_PROCESS_IDENTITY_MISMATCH'};$Expected.executableBinding}else{Get-RecI3ExecutableFileBinding ([string]$Expected.executablePath) $null 'EXPECTED_PROCESS_IMAGE'}
    $actualBinding=if($null-ne$Actual.executableBinding){if([string]$Actual.executablePath-cne[string]$Actual.executableBinding.rawPath){throw 'OWNED_PROCESS_IDENTITY_MISMATCH'};$Actual.executableBinding}else{Get-RecI3ExecutableFileBinding ([string]$Actual.executablePath) $null 'ACTUAL_PROCESS_IMAGE'}
    try{$null=Test-RecI3ExecutableFileBinding $expectedBinding $actualBinding $expectedBinding.sha256}catch{throw 'OWNED_PROCESS_IDENTITY_MISMATCH'}
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

function New-RecI3OwnedProcessBinding([Diagnostics.Process]$Process,[object]$ConfiguredExecutableBinding=$null,[object]$InventoryExecutableBinding=$null) {
    $handle=$Process.SafeHandle
    if($null-eq$handle-or$handle.IsInvalid-or$handle.IsClosed){throw 'OWNED_PROCESS_HANDLE_INVALID'}
    $identity=ConvertTo-RecI3Identity ([DoraRecI3OwnedProcessNative]::Identity($handle)) $PID
    $configured=$null;$inventory=$null;$proofStage='RETAINED_NATIVE_ONLY'
    if($null-ne$ConfiguredExecutableBinding){$configured=Confirm-RecI3ExecutableFileBinding $ConfiguredExecutableBinding $ConfiguredExecutableBinding.sha256;$null=Test-RecI3ExecutableFileBinding $configured $identity.executableBinding $configured.sha256;$proofStage='CONFIGURED_TO_RETAINED_NATIVE_VERIFIED'}
    if($null-ne$InventoryExecutableBinding){$inventory=Confirm-RecI3ExecutableFileBinding $InventoryExecutableBinding $(if($null-ne$configured){$configured.sha256}else{$InventoryExecutableBinding.sha256});$null=Test-RecI3ExecutableFileBinding $(if($null-ne$configured){$configured}else{$identity.executableBinding}) $inventory $(if($null-ne$configured){$configured.sha256}else{$inventory.sha256});$proofStage="$proofStage;INVENTORY_VERIFIED"}
    [ordered]@{ process=$Process; safeHandle=$handle; capturedIdentity=$identity; configuredExecutableBinding=$configured;inventoryExecutableBinding=$inventory;nativeExecutableBinding=$identity.executableBinding;executableBindingProof=[ordered]@{stage=$proofStage;error=$null;verified=($null-ne$configured)}; ancestry=$null; identitySource='RETAINED_SAFE_PROCESS_HANDLE' }
}

function New-RecI3TerminationExitUncertainResult([object]$Captured,[object]$Fresh,[bool]$TerminationSucceeded,[string]$TerminationError,[string]$WaitError) {
    [ordered]@{capturedIdentity=$Captured;freshIdentity=$Fresh;terminationAttempted=$true;terminationSucceeded=$TerminationSucceeded;terminationError=@($TerminationError,$WaitError|Where-Object{$null-ne$_})-join'; ';absenceObserved=$false;replacementObserved=$false;unknown=$true;state='TERMINATION_EXIT_UNCERTAIN';exitCode=$null;exitCodeCaptureError=$null}
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
                $p=[Diagnostics.Process]::GetProcessById([int]$entry.Key);$child=New-RecI3OwnedProcessBinding $p;$key="$($child.capturedIdentity.processId)|$($child.capturedIdentity.creationFileTimeUtc)|$($child.capturedIdentity.executablePhysicalId)"
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
            }catch{
                $captureError=$_.Exception.Message
                if($null-eq$p){
                    try{$goneConfirmation=Get-RecI3ParentSnapshot;if(-not$goneConfirmation.parents.ContainsKey([int]$entry.Key)-or[int]$goneConfirmation.parents[[int]$entry.Key]-ne[int]$parent.capturedIdentity.processId){continue}}catch{$captureError="$captureError; DISAPPEARANCE_CONFIRMATION_FAILED:$($_.Exception.Message)"}
                }
                if($null-ne$p-and($null-eq$key-or-not$Known.Contains($key))){$p.Dispose()};$Failures.Add("OWNED_DESCENDANT_CAPTURE_UNCERTAIN:$($entry.Key):$captureError")
            }
        }
    }
    [int]$added
}

function Get-RecI3ShutdownBudgetStep([int64]$DeadlineTicks,[int64]$NowTicks,[int]$RequestedWaitMilliseconds,[int]$AddedCount) {
    if($RequestedWaitMilliseconds-lt0-or$AddedCount-lt0){throw 'OWNED_CLOSURE_BUDGET_INPUT_INVALID'}
    $remainingTicks=$DeadlineTicks-$NowTicks
    if($remainingTicks-le0){return [ordered]@{exhausted=$true;remainingMilliseconds=0;clampedWaitMilliseconds=0;continueDiscovery=$false}}
    $remainingMilliseconds=[int][Math]::Max(1,[Math]::Floor($remainingTicks/[TimeSpan]::TicksPerMillisecond))
    [ordered]@{exhausted=$false;remainingMilliseconds=$remainingMilliseconds;clampedWaitMilliseconds=[int][Math]::Min($RequestedWaitMilliseconds,$remainingMilliseconds);continueDiscovery=($AddedCount-gt0)}
}

function New-RecI3BudgetExhaustedResult([object]$Captured) {
    [ordered]@{capturedIdentity=$Captured;freshIdentity=$null;terminationAttempted=$false;terminationSucceeded=$false;terminationError='OWNED_CLOSURE_SHUTDOWN_BUDGET_EXHAUSTED';absenceObserved=$false;replacementObserved=$false;unknown=$true;state='NOT_ATTEMPTED_BUDGET_EXHAUSTED';exitCode=$null;exitCodeCaptureError=$null}
}

function Stop-RecI3OwnedProcessClosure([object]$RootBinding,[int]$GraceMilliseconds=1000,[int]$ForceWaitMilliseconds=10000) {
    if($null-eq$RootBinding){return [ordered]@{rootIdentity=$null;capturedCount=0;capturedAncestry=@();results=@();unresolvedIdentities=@();failures=@('OWNED_PROCESS_BINDING_UNAVAILABLE');snapshotPasses=0;stablePasses=0;shutdownBudgetMilliseconds=[Math]::Max(1,$ForceWaitMilliseconds);budgetExhausted=$false;cleanupCertain=$false;laterAuditRequired=$true;rootAbsenceObserved=$false;state='OWNED_CLOSURE_UNCERTAIN'}}
    $bindings=[Collections.Generic.List[object]]::new();$bindings.Add($RootBinding)
    $failures=[Collections.Generic.List[string]]::new()
    $known=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$null=$known.Add("$($RootBinding.capturedIdentity.processId)|$($RootBinding.capturedIdentity.creationFileTimeUtc)|$($RootBinding.capturedIdentity.executablePhysicalId)")
    $shutdownBudgetMilliseconds=[Math]::Max(1,$ForceWaitMilliseconds);$shutdownDeadline=[DateTime]::UtcNow.AddMilliseconds($shutdownBudgetMilliseconds);$preDeadline=[DateTime]::UtcNow.AddMilliseconds([Math]::Max(250,[Math]::Min(2000,$GraceMilliseconds)));if($preDeadline-gt$shutdownDeadline){$preDeadline=$shutdownDeadline}
    $snapshotPasses=0;$stablePasses=0
    do{
        $added=0;$snapshotPasses++
        try{$added=Add-RecI3ObservedDescendants $bindings $known $failures}catch{$failures.Add("OWNED_CLOSURE_SNAPSHOT_UNCERTAIN:$($_.Exception.Message)");break}
        if($added-gt0){$quietDeadline=[DateTime]::UtcNow.AddMilliseconds(250);if($quietDeadline-gt$preDeadline){$preDeadline=$quietDeadline};if($preDeadline-gt$shutdownDeadline){$preDeadline=$shutdownDeadline}}
        if($added-eq0){$stablePasses++}else{$stablePasses=0}
        if([DateTime]::UtcNow-lt$preDeadline){Start-Sleep -Milliseconds 25}
    }while([DateTime]::UtcNow-lt$preDeadline-and[DateTime]::UtcNow-lt$shutdownDeadline)
    if($stablePasses-lt2){$failures.Add('OWNED_CLOSURE_PRE_SHUTDOWN_RECONCILIATION_UNCERTAIN')}
    $results=[Collections.Generic.List[object]]::new();$resolved=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$budgetExhausted=$false
    try{
        $index=$bindings.Count-1
        while($index-ge0){
            $step=Get-RecI3ShutdownBudgetStep $shutdownDeadline.Ticks ([DateTime]::UtcNow.Ticks) $ForceWaitMilliseconds 0;if($step.exhausted){$budgetExhausted=$true;break}
            try{$snapshotPasses++;$added=Add-RecI3ObservedDescendants $bindings $known $failures}catch{$added=0;$failures.Add("OWNED_CLOSURE_SHUTDOWN_SNAPSHOT_UNCERTAIN:$($_.Exception.Message)")}
            $step=Get-RecI3ShutdownBudgetStep $shutdownDeadline.Ticks ([DateTime]::UtcNow.Ticks) $ForceWaitMilliseconds $added;if($step.exhausted){$budgetExhausted=$true;break};if($step.continueDiscovery){$index=$bindings.Count-1;continue}
            try{$result=Stop-RecI3OwnedProcess $bindings[$index] 0 $step.clampedWaitMilliseconds;$results.Add($result);$null=$resolved.Add("$($bindings[$index].capturedIdentity.processId)|$($bindings[$index].capturedIdentity.creationFileTimeUtc)|$($bindings[$index].capturedIdentity.executablePhysicalId)");if($result.unknown-or-not$result.absenceObserved){$failures.Add("OWNED_INSTANCE_EXIT_UNCERTAIN:$($bindings[$index].capturedIdentity.processId):$($result.terminationError)")}}
            catch{$failures.Add("OWNED_INSTANCE_CLEANUP_UNCERTAIN:$($bindings[$index].capturedIdentity.processId):$($_.Exception.Message)")}
            $index--
        }
        $postStable=0;for($post=0;$post-lt2;$post++){$step=Get-RecI3ShutdownBudgetStep $shutdownDeadline.Ticks ([DateTime]::UtcNow.Ticks) 25 0;if($step.exhausted){$budgetExhausted=$true;break};$snapshotPasses++;try{$added=Add-RecI3ObservedDescendants $bindings $known $failures;if($added-eq0){$postStable++}else{$failures.Add('OWNED_DESCENDANT_OBSERVED_AFTER_PARENT_SHUTDOWN')}}catch{$failures.Add("OWNED_CLOSURE_POST_SHUTDOWN_SNAPSHOT_UNCERTAIN:$($_.Exception.Message)")};Start-Sleep -Milliseconds $step.clampedWaitMilliseconds};if($postStable-ne2){$failures.Add('OWNED_CLOSURE_POST_SHUTDOWN_RECONCILIATION_UNCERTAIN')}
    }finally{foreach($binding in $bindings){if($binding-ne$RootBinding){try{$binding.process.Dispose()}catch{$failures.Add("OWNED_INSTANCE_DISPOSE_UNCERTAIN:$($binding.capturedIdentity.processId)")}}}}
    $unresolved=@($bindings|Where-Object{-not$resolved.Contains("$($_.capturedIdentity.processId)|$($_.capturedIdentity.creationFileTimeUtc)|$($_.capturedIdentity.executablePhysicalId)")});if($budgetExhausted){$failures.Add('OWNED_CLOSURE_SHUTDOWN_BUDGET_EXHAUSTED');foreach($binding in $unresolved){$results.Add((New-RecI3BudgetExhaustedResult $binding.capturedIdentity))}}
    $cleanupCertain=($failures.Count-eq0-and-not$budgetExhausted-and$unresolved.Count-eq0-and@($results|Where-Object{-not$_.absenceObserved-or$_.unknown}).Count-eq0)
    [ordered]@{rootIdentity=$RootBinding.capturedIdentity;capturedCount=[int]$bindings.Count;capturedAncestry=@($bindings|ForEach-Object{$_.ancestry});results=@($results);unresolvedIdentities=@($unresolved|ForEach-Object{$_.capturedIdentity});failures=@($failures);snapshotPasses=$snapshotPasses;stablePasses=$stablePasses;shutdownBudgetMilliseconds=$shutdownBudgetMilliseconds;budgetExhausted=[bool]$budgetExhausted;cleanupCertain=$cleanupCertain;laterAuditRequired=(-not$cleanupCertain);rootAbsenceObserved=[bool](@($results|Where-Object{$_.capturedIdentity.processId-eq$RootBinding.capturedIdentity.processId-and$_.absenceObserved-and-not$_.unknown}).Count-eq1);state=if($cleanupCertain){'OWNED_CLOSURE_COMPLETE'}else{'OWNED_CLOSURE_UNCERTAIN'}}
}

Export-ModuleMember -Function Get-RecI3ExecutableFileBinding,Confirm-RecI3ExecutableFileBinding,Test-RecI3ExecutableFileBinding,ConvertTo-RecI3WindowsCommandLine,Confirm-RecI3ObservedTargetStartedReceipt,Confirm-RecI3ObservedTargetFailureReceipt,Confirm-RecI3ObservedTargetReceipt,ConvertFrom-RecI3WrapperClosureTargetCleanup,New-RecI3OwnedProcessBinding,Stop-RecI3OwnedProcess,Stop-RecI3OwnedProcessClosure,Test-RecI3Identity,Test-RecI3ObservedChildBinding,New-RecI3TerminationExitUncertainResult,Get-RecI3ShutdownBudgetStep
