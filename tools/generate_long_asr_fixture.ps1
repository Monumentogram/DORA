param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateSet('ru', 'en')][string]$Language = 'en'
)
$ErrorActionPreference = 'Stop'
$target = [System.IO.Path]::GetFullPath($OutputDirectory)
$repository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ($target.StartsWith($repository + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'GENERATED_AUDIO_MUST_STAY_OUTSIDE_REPOSITORY'
}
if (Test-Path -LiteralPath $target) { throw 'NEW_EXTERNAL_DIRECTORY_REQUIRED' }
[System.IO.Directory]::CreateDirectory($target) | Out-Null
Add-Type -AssemblyName System.Speech
$speech = [System.Speech.Synthesis.SpeechSynthesizer]::new()
$format = [System.Speech.AudioFormat.SpeechAudioFormatInfo]::new(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$voice = if ($Language -eq 'ru') { 'Microsoft Irina Desktop' } else { 'Microsoft David Desktop' }
$speech.SelectVoice($voice)
$speech.Rate = 0
$speech.Volume = 100
$markers = if ($Language -eq 'ru') {
    @('красный маяк', 'зелёный остров', 'золотая звезда', 'белая берёза', 'синий корабль', 'тёплый ветер', 'серебряная луна', 'высокая гора', 'тихая река', 'жёлтый фонарь', 'широкая дорога', 'ясное небо', 'каменный мост', 'лесная тропа', 'зимний сад', 'северный город', 'летний дождь', 'последняя страница')
} else {
    @('amber lantern', 'silver meadow', 'golden mountain', 'quiet river', 'purple garden', 'bright island', 'winter forest', 'gentle ocean', 'crimson sunrise', 'copper bridge', 'distant castle', 'velvet morning', 'northern valley', 'orange harbor', 'peaceful village', 'summer rainfall', 'sapphire window', 'final chapter')
}
$fillerText = if ($Language -eq 'ru') { 'Это искусственная тестовая запись. Мы проверяем сохранение речи и правильный порядок фрагментов. В этом примере нет личных разговоров.' } else { 'This is an artificial test recording. We are checking speech storage and the correct order of its parts. This example contains no private conversations.' }
function Get-SyntheticPcm([string]$Text) {
    $memory = [System.IO.MemoryStream]::new()
    try {
        $speech.SetOutputToAudioStream($memory, $format)
        $speech.Speak($Text)
        $speech.SetOutputToNull()
        return ,$memory.ToArray()
    } finally { $memory.Dispose() }
}
$pcmPath = Join-Path $target 'public-speech-1800.pcm'
$output = [System.IO.File]::Open($pcmPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write)
$zero = [byte[]]::new(32000)
$manifestMarkers = [System.Collections.Generic.List[object]]::new()
$filler = Get-SyntheticPcm $fillerText
function Write-Until([long]$EndByte) {
    while ($output.Position -lt $EndByte) {
        [long]$remaining = $EndByte - $output.Position
        if ($remaining -ge $filler.LongLength) { $output.Write($filler, 0, $filler.Length) }
        else {
            [int]$count = [System.Math]::Min($remaining, $zero.LongLength)
            $output.Write($zero, 0, $count)
        }
    }
}
try {
    for ($window = 0; $window -lt 6; $window++) {
        for ($marker = 0; $marker -lt 3; $marker++) {
            $phrase = $markers[$window * 3 + $marker]
            $text = if ($Language -eq 'ru') { "Контрольная фраза: $phrase." } else { "The reference phrase is $phrase." }
            $clip = Get-SyntheticPcm $text
            if ($clip.Length -le 0 -or $clip.Length -gt 20 * 32000 -or $clip.Length % 2 -ne 0) { throw 'SYNTHETIC_MARKER_DURATION_INVALID' }
            [long]$clipFrames = $clip.LongLength / 2
            [long]$windowFirst = [long]$window * 4800000
            [long]$windowEnd = ([long]$window + 1) * 4800000
            # Retain each complete utterance once: exact window start, interior, exact window end.
            # This checks edge placement; it does not claim quality for an utterance cut in half.
            [long]$startFrame = if ($marker -eq 2) { $windowEnd - $clipFrames } elseif ($marker -eq 1) { $windowFirst + 145 * 16000 } else { $windowFirst }
            if ($output.Position -gt $startFrame * 2) { throw 'SYNTHETIC_MARKER_OVERLAP' }
            Write-Until ($startFrame * 2)
            $output.Write($clip, 0, $clip.Length)
            $role = @('WINDOW_START', 'MIDPOINT', 'WINDOW_END')[$marker]
            $manifestMarkers.Add(@{ windowIndex = $window; role = $role; firstFrame = $startFrame; frameCount = $clipFrames; phrase = $phrase })
            [System.Array]::Clear($clip, 0, $clip.Length)
        }
        Write-Until (([long]$window + 1) * 300 * 32000)
    }
    if ($output.Position -ne 57600000L) { throw 'SYNTHETIC_FIXTURE_FRAME_COUNT_INVALID' }
    $output.Flush($true)
} finally {
    $output.Dispose()
    $speech.Dispose()
    [System.Array]::Clear($filler, 0, $filler.Length)
}
$digest = (Get-FileHash -LiteralPath $pcmPath -Algorithm SHA256).Hash.ToLowerInvariant()
$receipt = @{ version = 2; windowPolicy = 'local-fixed-300-v1'; boundaryCoverage = 'COMPLETE_UTTERANCES_ALIGNED_TO_WINDOW_EDGES_NO_CROSSING_QUALITY_CLAIM'; kind = 'OFFLINE_GENERATED_SPEECH_NO_PRIVATE_AUDIO'; voice = $voice; language = $Language; frames = 28800000L; pcmBytes = 57600000L; pcmSha256 = $digest; sampleRateHz = 16000; encoding = 'PCM_S16LE'; channels = 1; markers = $manifestMarkers }
[System.IO.File]::WriteAllText((Join-Path $target 'manifest.json'), ($receipt | ConvertTo-Json -Depth 6), [System.Text.UTF8Encoding]::new($false))
@{ result = 'GENERATED_SYNTHETIC_30M_FIXTURE'; language = $Language; frames = 28800000L; pcmSha256 = $digest; markerCount = 18 } | ConvertTo-Json
