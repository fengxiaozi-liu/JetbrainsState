param([string]$IdeHome = 'C:\Program Files\JetBrains\PyCharm 2026.3.0.2')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $repoRoot 'build\multi-product\classes'
$verified = Join-Path $repoRoot 'build\multi-product\verified'
$asm = Join-Path $IdeHome 'lib\intellij.libraries.asm.jar'
$java = Join-Path $IdeHome 'jbr\bin\java.exe'
$javac = Join-Path $IdeHome 'jbr\bin\javac.exe'
$sources = @('LocalStateAgent','PyCharmStateAgent','AgentCompatibility','VerifyPyCharm') | ForEach-Object { Join-Path $repoRoot "src\$_.java" }
[void](New-Item -ItemType Directory -Force -Path $classes)
& $javac -encoding UTF-8 -cp $asm -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Agent compilation failed.' }
& $java -cp "$classes;$asm" VerifyPyCharm $IdeHome $verified
if ($LASTEXITCODE -ne 0) { throw 'PyCharm transformation verification failed.' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = Join-Path $repoRoot 'local-state-agent.jar'
$temporaryJar = $jarPath + '.new'
$stream = [IO.File]::Open($temporaryJar,[IO.FileMode]::Create)
$archive = [IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
try {
    $entry = $archive.CreateEntry('META-INF/MANIFEST.MF')
    $writer = [IO.StreamWriter]::new($entry.Open(),[Text.UTF8Encoding]::new($false))
    try { $writer.Write("Manifest-Version: 1.0`r`nPremain-Class: LocalStateAgent`r`n`r`n") } finally { $writer.Dispose() }
    foreach ($file in Get-ChildItem -LiteralPath $classes -Filter '*.class') {
        if ($file.Name -notmatch '^(LocalStateAgent|PyCharmStateAgent|AgentCompatibility)(\$|\.)') { continue }
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$file.Name)
    }
    $dependency = [IO.Compression.ZipFile]::OpenRead($asm)
    try {
        foreach ($source in $dependency.Entries) {
            if (!$source.FullName.StartsWith('org/jetbrains/org/objectweb/asm/') -or !$source.FullName.EndsWith('.class')) { continue }
            $entry = $archive.CreateEntry($source.FullName)
            $input = $source.Open(); $output = $entry.Open()
            try { $input.CopyTo($output) } finally { $input.Dispose(); $output.Dispose() }
        }
    } finally { $dependency.Dispose() }
} finally { $archive.Dispose(); $stream.Dispose() }
& $java "-javaagent:$temporaryJar=PY-263.7307.351" -version
if ($LASTEXITCODE -ne 0) { throw 'Agent startup failed; previous binary preserved.' }
& $java -cp $temporaryJar AgentCompatibility 'PY-263.7307.351' $IdeHome
if ($LASTEXITCODE -ne 0) { throw 'Packaged compatibility check failed; previous binary preserved.' }
Copy-Item -LiteralPath $temporaryJar -Destination $jarPath -Force
Remove-Item -LiteralPath $temporaryJar
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
[IO.File]::WriteAllText((Join-Path $repoRoot 'agent.sha256'),$hash + [Environment]::NewLine,[Text.UTF8Encoding]::new($false))
Write-Output "Built verified multi-product agent: $hash"
