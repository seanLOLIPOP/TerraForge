param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Push-Location $PSScriptRoot
try {
    if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome pointing to a Java 21 JDK.' }
    $JdkHome = (Resolve-Path -LiteralPath $JdkHome).Path
    & "$JdkHome/bin/javac.exe" -version
    if ($LASTEXITCODE) { throw 'Cannot run javac.' }
    $classes = Join-Path $PSScriptRoot ('dist/build-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path "$classes/assets" -Force | Out-Null
    Copy-Item -LiteralPath 'assets/tree-library.trees' -Destination "$classes/assets/"
    $sources = @(Get-ChildItem -LiteralPath src -Filter '*.java' | ForEach-Object FullName)
    & "$JdkHome/bin/javac.exe" --release 21 -encoding UTF-8 -cp 'lib/*' -d $classes @sources
    if ($LASTEXITCODE) { throw 'Compilation failed.' }
    & "$JdkHome/bin/jar.exe" --create --file TerraForge.jar.new --manifest tools/manifest.mf -C $classes .
    if ($LASTEXITCODE) { throw 'JAR creation failed.' }
    Move-Item -LiteralPath TerraForge.jar.new -Destination TerraForge.jar -Force
    & "$JdkHome/bin/java.exe" -jar TerraForge.jar --version
    if ($LASTEXITCODE) { throw 'Version check failed.' }
} finally { Pop-Location }
