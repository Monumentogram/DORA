$ErrorActionPreference = 'Stop'
$taskPythonCandidates = @(
    (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'),
    (Join-Path $PSScriptRoot '..\..\..\.tools\python\python.exe')
)
$taskPython = $taskPythonCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $taskPython) {
    $taskCommand = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($taskCommand -and $taskCommand.Source -notlike '*WindowsApps*') { $taskPython = $taskCommand.Source }
}
if (-not $taskPython) { throw 'Python 3.12 is required. Reopen the task in Codex to restore its bundled runtime.' }
& $taskPython (Join-Path $PSScriptRoot 'server.py')
exit $LASTEXITCODE
