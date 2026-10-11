param([string]$JavaHome, [string]$Maven)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $JavaHome) {
    foreach ($candidate in @('.tools\jdk25', '..\PixelPrinter Reforged\.tools\jdk25', $env:JAVA_HOME)) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe'))) { $JavaHome = (Resolve-Path -LiteralPath $candidate).Path; break }
    }
}
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
if (-not $Maven) {
    foreach ($candidate in @('.tools\apache-maven-3.9.11\bin\mvn.cmd', '..\PixelPrinter Reforged\.tools\apache-maven-3.9.11\bin\mvn.cmd')) {
        if (Test-Path -LiteralPath $candidate) { $Maven = (Resolve-Path -LiteralPath $candidate).Path; break }
    }
}
if (-not $Maven) { $Maven = 'mvn.cmd' }
& $Maven clean verify
if ($LASTEXITCODE -ne 0) { throw "Maven a echoue: $LASTEXITCODE" }
Write-Host 'Pret: target\Music-Reforged-1.0.0.jar'
