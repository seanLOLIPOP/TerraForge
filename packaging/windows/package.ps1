param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path "$PSScriptRoot/../..").Path
if (!$JdkHome) { throw 'Pass -JdkHome pointing to a Windows x64 Java 21 JDK.' }
$JdkHome = (Resolve-Path -LiteralPath $JdkHome).Path
& "$root/build.ps1" -JdkHome $JdkHome
$version = (Get-Content -LiteralPath "$root/VERSION" -Raw).Trim()
$stage = Join-Path $root ('dist/windows-' + [guid]::NewGuid().ToString('N'))
$inputDir = Join-Path $stage 'input'
New-Item -ItemType Directory -Path "$inputDir/lib/licenses" -Force | Out-Null
Copy-Item -LiteralPath "$root/TerraForge.jar" -Destination $inputDir
foreach ($module in @('lwjgl','lwjgl-glfw','lwjgl-opengl')) {
    foreach ($suffix in @('', '-natives-windows')) {
        $name = "$module-3.3.6$suffix.jar"
        Copy-Item -LiteralPath "$root/lib/$name" -Destination "$inputDir/lib/"
    }
}
Copy-Item -Path "$root/lib/licenses/*" -Destination "$inputDir/lib/licenses/" -Recurse
$runtime = Join-Path $stage 'runtime'
& "$JdkHome/bin/jlink.exe" --add-modules java.desktop,java.logging,java.management,jdk.management,java.naming,jdk.unsupported --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
if ($LASTEXITCODE) { throw 'Runtime creation failed.' }
$output = Join-Path $stage 'image'
& "$JdkHome/bin/jpackage.exe" --type app-image --name TerraForge --app-version $version --vendor TerraForge --description 'Minecraft Java terrain editor' --input $inputDir --main-jar TerraForge.jar --main-class TerraForge --runtime-image $runtime --dest $output --java-options '-XX:MaxRAMPercentage=70' --java-options '-Dterraforge.home=$APPDIR/..'
if ($LASTEXITCODE) { throw 'Windows launcher creation failed.' }
$app = Join-Path $output 'TerraForge'
foreach ($file in @('VERSION','FEATURE_GUIDE.md','RELEASE_NOTES.md','THIRD_PARTY_NOTICES.md')) {
    Copy-Item -LiteralPath "$root/$file" -Destination $app
}
Copy-Item -LiteralPath "$root/examples" -Destination $app -Recurse
Copy-Item -LiteralPath "$PSScriptRoot/README-Windows.txt","$PSScriptRoot/TerraForge-Console.cmd" -Destination $app
$zip = Join-Path $root "dist/TerraForge-v$version-Windows-x64.zip"
if (Test-Path -LiteralPath $zip) { throw "Package already exists: $zip. Move it aside before rebuilding." }
Compress-Archive -LiteralPath $app -DestinationPath $zip -CompressionLevel Optimal
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$zip.sha256" -Value "$hash  $([IO.Path]::GetFileName($zip))" -Encoding ascii
Write-Output "Windows package: $zip"
Write-Output "SHA256: $hash"
