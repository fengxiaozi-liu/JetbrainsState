$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = 'C:\Program Files\JetBrains\GoLand 2026.3\jbr\bin'
$ideLib = 'C:\Program Files\JetBrains\GoLand 2026.3\lib'
$classesDir = Join-Path $repoRoot 'build\classes'
$patchedDir = Join-Path $repoRoot 'build\verified'
[void](New-Item -ItemType Directory -Force -Path $classesDir)
$asmJar = Join-Path $ideLib 'intellij.libraries.asm.jar'
& (Join-Path $runtimeDir 'javac.exe') -encoding UTF-8 -cp $asmJar -d $classesDir (Join-Path $repoRoot 'src\LocalStateAgent.java') (Join-Path $repoRoot 'src\VerifyTransform.java')
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
& (Join-Path $runtimeDir 'java.exe') -cp "$classesDir;$asmJar" VerifyTransform (Join-Path $ideLib 'intellij.platform.cs.jar') $patchedDir (Join-Path $ideLib 'intellij.platform.ide.impl.jar')
if ($LASTEXITCODE -ne 0) { throw 'Transformation verification failed.' }
# Package only the agent, using ZIP APIs; no dependency on the bundled cracking JAR.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = Join-Path $repoRoot 'local-state-agent.jar'
$stream = [IO.File]::Open($jarPath, [IO.FileMode]::Create)
$archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    $manifest = $archive.CreateEntry('META-INF/MANIFEST.MF')
    $writer = [IO.StreamWriter]::new($manifest.Open(), [Text.UTF8Encoding]::new($false))
    $writer.Write("Manifest-Version: 1.0`r`nPremain-Class: LocalStateAgent`r`n`r`n")
    $writer.Dispose()
    foreach ($file in (Get-ChildItem -LiteralPath $classesDir -Filter 'LocalStateAgent*.class')) {
        $entry = $archive.CreateEntry($file.Name)
        $output = $entry.Open()
        try { $bytes = [IO.File]::ReadAllBytes($file.FullName); $output.Write($bytes, 0, $bytes.Length) } finally { $output.Dispose() }
    }
    $dependency = [IO.Compression.ZipFile]::OpenRead($asmJar)
    try {
        foreach ($sourceEntry in $dependency.Entries) {
            if (!$sourceEntry.FullName.StartsWith('org/jetbrains/org/objectweb/asm/') -or !$sourceEntry.FullName.EndsWith('.class')) { continue }
            $entry = $archive.CreateEntry($sourceEntry.FullName)
            $input = $sourceEntry.Open()
            $output = $entry.Open()
            try { $input.CopyTo($output) } finally { $input.Dispose(); $output.Dispose() }
        }
    } finally { $dependency.Dispose() }
} finally { $archive.Dispose(); $stream.Dispose() }
& (Join-Path $runtimeDir 'java.exe') "-javaagent:$jarPath" -version
if ($LASTEXITCODE -ne 0) { throw 'Agent startup verification failed.' }
Write-Output "Built and verified: $jarPath"
