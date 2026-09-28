param([string]$ToolRoot = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) '..\.tools\aws-cli-v2'))
$ErrorActionPreference = 'Stop'
$ToolRoot = [IO.Path]::GetFullPath($ToolRoot)
$aws = Join-Path $ToolRoot 'Amazon\AWSCLIV2\aws.exe'
if (Test-Path -LiteralPath $aws) { & $aws --version; if ($LASTEXITCODE -ne 0) { throw 'Existing CLI failed' }; exit 0 }
New-Item -ItemType Directory -Force -Path $ToolRoot | Out-Null
$downloadRoot = Join-Path $ToolRoot 'download'
New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null
$msi = Join-Path $downloadRoot 'AWSCLIV2.msi'
Invoke-WebRequest -Uri 'https://awscli.amazonaws.com/AWSCLIV2.msi' -OutFile $msi
$signature = Get-AuthenticodeSignature -LiteralPath $msi
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'Amazon Web Services') {
    throw 'AWS MSI signature is not a valid Amazon Web Services signature; nothing installed'
}
# Administrative extraction only: no product installation, registry/PATH change, or elevation.
$process = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/a', ('"' + $msi + '"'), '/qn', ('TARGETDIR="' + $ToolRoot + '"'), '/l*v', ('"' + (Join-Path $ToolRoot 'extract.log') + '"')) -WindowStyle Hidden -PassThru -Wait
if ($process.ExitCode -ne 0) { throw "CLI extraction failed with exit code $($process.ExitCode)" }
if (-not (Test-Path -LiteralPath $aws)) { throw 'CLI extracted but expected executable is absent' }
& $aws --version
if ($LASTEXITCODE -ne 0) { throw 'Extracted AWS CLI failed verification' }
$proof = @{ version = (& $aws --version); msi_sha256 = (Get-FileHash -LiteralPath $msi -Algorithm SHA256).Hash.ToLowerInvariant(); executable_sha256 = (Get-FileHash -LiteralPath $aws -Algorithm SHA256).Hash.ToLowerInvariant(); signer = $signature.SignerCertificate.Subject; installed_at_utc = [DateTime]::UtcNow.ToString('o') }
$proof | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ToolRoot 'installation-proof.json') -Encoding utf8
