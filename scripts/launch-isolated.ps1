param([switch]$Wait, [string]$ProjectPath)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$ideHome = 'C:\Program Files\JetBrains\GoLand 2026.3'
$agentPath = Join-Path $repoRoot 'local-state-agent.jar'
if (!(Test-Path -LiteralPath $agentPath)) { throw 'Run build.ps1 first.' }
$product = Get-Content -Raw -LiteralPath (Join-Path $ideHome 'product-info.json') | ConvertFrom-Json
if ($product.productCode -ne 'GO' -or $product.buildNumber -ne '263.7307.211') { throw 'Unsupported GoLand build.' }
$sandboxDir = Join-Path $repoRoot 'sandbox'
[void](New-Item -ItemType Directory -Force -Path $sandboxDir)
$stateFile = Join-Path $sandboxDir 'launch-state.json'
if (Test-Path -LiteralPath $stateFile) {
    $previousState = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
    $activeProcess = Get-Process -Id $previousState.pid -ErrorAction SilentlyContinue
    $expectedStartUtc = ([datetime]$previousState.startTimeUtc).ToUniversalTime()
    if ($activeProcess -and $activeProcess.Path -eq (Join-Path $ideHome 'bin\goland64.exe') -and
        $activeProcess.StartTime.ToUniversalTime().Ticks -eq $expectedStartUtc.Ticks) {
        throw "The isolated GoLand instance is already running (PID $($activeProcess.Id)). Close it before launching again."
    }
}
# This JBR fails to connect AF_UNIX sockets under the user's longer Temp path.
# Use a short, task-specific directory only in this IDE's process environment.
$socketDir = Join-Path $env:SystemDrive 'jb-lab-sockets'
[void](New-Item -ItemType Directory -Force -Path $socketDir)
$vmFile = Join-Path $sandboxDir 'goland.vmoptions'
$options = @(Get-Content -LiteralPath (Join-Path $ideHome 'bin\goland64.exe.vmoptions'))
$options += "-javaagent:$agentPath"
$options += "-Didea.config.path=$sandboxDir\config"
$options += "-Didea.system.path=$sandboxDir\system"
$options += "-Didea.plugins.path=$sandboxDir\plugins"
$options += "-Didea.log.path=$sandboxDir\log"
$options += '-Didea.initially.ask.config=false'
$options += '-Dintellij.startup.wizard=false'
$options += "-Djdk.net.unixdomain.tmpdir=$socketDir"
[IO.File]::WriteAllLines($vmFile, $options, [Text.UTF8Encoding]::new($false))
$previousOptions = $env:GOLAND_VM_OPTIONS
$previousTemp = $env:TEMP
$previousTmp = $env:TMP
try {
    $env:GOLAND_VM_OPTIONS = $vmFile
    $env:TEMP = $socketDir
    $env:TMP = $socketDir
    $launchArguments = 'dontReopenProjects'
    if ($ProjectPath) {
        $resolvedProject = (Resolve-Path -LiteralPath $ProjectPath).Path
        if ($resolvedProject.Contains('"')) { throw 'Invalid project path.' }
        $launchArguments += ' "' + $resolvedProject + '"'
    }
    $process = Start-Process -FilePath (Join-Path $ideHome 'bin\goland64.exe') -ArgumentList $launchArguments -PassThru -WindowStyle Normal -RedirectStandardOutput (Join-Path $sandboxDir 'stdout.log') -RedirectStandardError (Join-Path $sandboxDir 'stderr.log')
    $launchState = @{ pid = $process.Id; startTimeUtc = $process.StartTime.ToUniversalTime().ToString('o'); build = $product.buildNumber }
    [IO.File]::WriteAllText($stateFile, ($launchState | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    Write-Output "Launched isolated experiment; process $($process.Id). Logs: $sandboxDir\log"
    if ($Wait) {
        $process.WaitForExit()
        $process.Refresh()
        if ($null -ne $process.ExitCode) { Write-Output "Exit code: $($process.ExitCode)" }
        else { Write-Output 'IDE process exited.' }
    }
} finally {
    $env:GOLAND_VM_OPTIONS = $previousOptions
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
}
