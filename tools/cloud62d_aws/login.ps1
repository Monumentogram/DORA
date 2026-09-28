param([ValidateSet('Browser','SSO')][string]$Method='Browser')
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$aws = [IO.Path]::GetFullPath((Join-Path $repo '..\.tools\aws-cli-v2\Amazon\AWSCLIV2\aws.exe'))
if (-not (Test-Path -LiteralPath $aws)) { & (Join-Path $PSScriptRoot 'install-cli.ps1') }
if (-not (Test-Path -LiteralPath $aws)) { throw 'Prepared AWS CLI unavailable' }
Get-ChildItem Env:AWS_* | ForEach-Object { Remove-Item -LiteralPath ('Env:' + $_.Name) }
$env:AWS_PAGER=''
$env:AWS_EC2_METADATA_DISABLED='true'
$env:AWS_IGNORE_CONFIGURED_ENDPOINT_URLS='true'
Write-Host 'Profile: dora-62d-alpha. Sign in with the intended TEST account and a role, never root. Do not share keys, tokens, or sign-in codes in chat.'
if ($Method -eq 'SSO') {
    & $aws configure sso --profile dora-62d-alpha
    if ($LASTEXITCODE -ne 0) { throw 'SSO configuration was not completed' }
    & $aws sso login --profile dora-62d-alpha
} else {
    & $aws login --profile dora-62d-alpha --region eu-central-1
}
if ($LASTEXITCODE -ne 0) { throw 'Login was not completed' }
& $aws configure set region eu-central-1 --profile dora-62d-alpha
$identityJson = & $aws sts get-caller-identity --profile dora-62d-alpha --region eu-central-1 --output json --no-cli-pager
if ($LASTEXITCODE -ne 0) { throw 'STS verification failed' }
$identity = $identityJson | ConvertFrom-Json
if ($identity.Arn -notmatch ':assumed-role/') { throw 'A short-lived assumed role is required; switch console role and rerun' }
Write-Host 'LOGIN_OK: short-lived assumed role verified. Account identifiers remain local. Return to Codex; no secrets are needed.'
