param(
    [ValidateSet('GoLand','PyCharm')][string]$Product = 'GoLand',
    [ValidateSet('Install','Restore','Check')][string]$Mode = 'Install'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$profiles = Import-PowerShellDataFile (Join-Path $PSScriptRoot 'products.psd1')
$productProfile = $profiles[$Product]
$build = $productProfile.Build
$ideHome = $productProfile.IdeHome
$environmentName = $productProfile.EnvironmentName
$profileId = $productProfile.Code + '-' + $build
# Keep product-specific user options and rollback records independent.
$legacyHome = Join-Path $env:LOCALAPPDATA ($productProfile.LegacyFolder + '\' + $build)
$managedHome = Join-Path $env:USERPROFILE ($productProfile.StateFolder + '\' + $build)
$managedOptions = Join-Path $managedHome $productProfile.OptionsName
$expectedAgentHash = ''
if ($Mode -ne 'Restore') {
    $expectedAgentHash = (Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'agent.sha256')).Trim()
    if ($expectedAgentHash -cnotmatch '^[A-F0-9]{64}$') { throw 'Invalid agent.sha256. Run scripts/build-agent.ps1.' }
}
$agentHome = Join-Path $ideHome 'local-agent'
$managedAgent = Join-Path $agentHome ("local-state-agent-" + $(if ($expectedAgentHash) { $expectedAgentHash.Substring(0,12).ToLowerInvariant() } else { 'restore' }) + '.jar')
$statePath = Join-Path $managedHome 'install-state.json'
$userOptions = Join-Path $env:APPDATA ('JetBrains\' + $productProfile.ConfigFolder + '\' + $productProfile.Launcher + '.vmoptions')
$backupOptions = Join-Path $managedHome 'original-user.vmoptions'
$socketDir = Join-Path $env:SystemDrive 'jb-lab-sockets'
$utf8 = [Text.UTF8Encoding]::new($false)

function Notify-EnvironmentChange {
    if (-not ('GoLandStateLab.EnvironmentNotification' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace GoLandStateLab {
    public static class EnvironmentNotification {
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr SendMessageTimeout(IntPtr window, uint message,
            UIntPtr parameter, string text, uint flags, uint timeout, out UIntPtr result);
    }
}
'@
    }
    $notificationResult = [UIntPtr]::Zero
    [void][GoLandStateLab.EnvironmentNotification]::SendMessageTimeout(
        [IntPtr]0xffff, 0x1a, [UIntPtr]::Zero, 'Environment', 2, 1000, [ref]$notificationResult)
}

function Write-TextAtomically([string]$path, [string]$text) {
    $temporaryFile = $path + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    $replacementBackup = $temporaryFile + '.previous'
    try {
        [IO.File]::WriteAllText($temporaryFile, $text, $utf8)
        if (Test-Path -LiteralPath $path) { [IO.File]::Replace($temporaryFile, $path, $replacementBackup) }
        else { [IO.File]::Move($temporaryFile, $path) }
    } finally {
        if (Test-Path -LiteralPath $temporaryFile) { Remove-Item -LiteralPath $temporaryFile }
        if (Test-Path -LiteralPath $replacementBackup) { Remove-Item -LiteralPath $replacementBackup }
    }
}

function Save-State($value) {
    Write-TextAtomically $statePath ($value | ConvertTo-Json -Depth 4)
}

function Test-AgentLoad {
    $javaExecutable = Join-Path $ideHome 'jbr\bin\java.exe'
    if (!(Test-Path -LiteralPath $javaExecutable)) { throw 'Bundled Java runtime is missing.' }
    $checkLog = Join-Path $managedHome 'agent-startup-check.log'
    $probe = New-Object System.Diagnostics.Process
    $probe.StartInfo.FileName = $javaExecutable
    $probe.StartInfo.Arguments = '"-javaagent:' + $managedAgent + '=' + $profileId + '" -version'
    $probe.StartInfo.UseShellExecute = $false
    $probe.StartInfo.CreateNoWindow = $true
    $probe.StartInfo.RedirectStandardOutput = $true
    $probe.StartInfo.RedirectStandardError = $true
    try {
        [void]$probe.Start()
        $stdout = $probe.StandardOutput.ReadToEndAsync()
        $stderr = $probe.StandardError.ReadToEndAsync()
        if (!$probe.WaitForExit(20000)) {
            $probe.Kill()
            $probe.WaitForExit()
            throw 'Agent startup check timed out. Configuration was not updated.'
        }
        $checkOutput = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        Write-TextAtomically $checkLog $checkOutput
        if ($probe.ExitCode -ne 0 -or $checkOutput -notmatch '\[local-state-lab\] Armed') {
            throw "Agent startup check failed. Configuration was not updated. See $checkLog"
        }
        Write-Output "PASS: Java Agent startup check. Log: $checkLog"
    } finally { $probe.Dispose() }
}

function Assert-SharedFileLocation([string]$path) {
    if (-not ('GoLandStateLab.FileLocation' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;
namespace GoLandStateLab {
    public static class FileLocation {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern uint GetFinalPathNameByHandle(SafeFileHandle handle,
            StringBuilder path, uint capacity, uint flags);
    }
}
'@
    }
    $stream = [IO.File]::OpenRead($path)
    try {
        $actual = New-Object Text.StringBuilder 32768
        $length = [GoLandStateLab.FileLocation]::GetFinalPathNameByHandle(
            $stream.SafeFileHandle, $actual, $actual.Capacity, 0)
        if ($length -eq 0 -or $length -ge $actual.Capacity) {
            throw "Cannot verify the physical installation path: $path"
        }
        $resolved = $actual.ToString()
        if ($resolved.StartsWith('\\?\')) { $resolved = $resolved.Substring(4) }
        if (![string]::Equals($resolved, [IO.Path]::GetFullPath($path), [StringComparison]::OrdinalIgnoreCase)) {
            throw "Installation path was redirected to '$resolved'. Configuration was not updated."
        }
    } finally { $stream.Dispose() }
}

if ($Mode -ne 'Restore') {
    $installedProductInfo = Get-Content -Raw -LiteralPath (Join-Path $ideHome 'product-info.json') | ConvertFrom-Json
    if ($installedProductInfo.productCode -ne $productProfile.Code -or $installedProductInfo.buildNumber -ne $build) {
        throw "Unsupported $Product build $($installedProductInfo.buildNumber). Expected $profileId. No configuration was changed."
    }
    $sourceAgent = Join-Path $repoRoot 'local-state-agent.jar'
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $sourceAgent).Hash -ne $expectedAgentHash) {
        throw 'Agent fingerprint does not match agent.sha256. No configuration was changed.'
    }
    & (Join-Path $ideHome 'jbr\bin\java.exe') -cp $sourceAgent AgentCompatibility $profileId $ideHome
    if ($LASTEXITCODE -ne 0) { throw 'Original class compatibility check failed. No configuration was changed.' }
    if ($Mode -eq 'Check') {
        Write-Output "PASS: $Product build, source Agent and target classes checked. No settings changed."
        $machineOptions = [Environment]::GetEnvironmentVariable($environmentName,'Machine')
        if ($machineOptions -and !(Test-Path -LiteralPath $machineOptions)) {
            Write-Output "NOTICE: Machine $environmentName points to a missing file: $machineOptions"
            Write-Output 'Install sets a current-user override; the machine value is preserved.'
        }
        return
    }
}

# Preserve the first installation's rollback metadata when moving away from
# an AppData location. Do not replace it with the currently installed options.
if (!(Test-Path -LiteralPath $statePath) -and
    (Test-Path -LiteralPath (Join-Path $legacyHome 'install-state.json'))) {
    [void](New-Item -ItemType Directory -Force -Path $managedHome)
    foreach ($name in @('install-state.json', 'original-user.vmoptions')) {
        $oldFile = Join-Path $legacyHome $name
        if (Test-Path -LiteralPath $oldFile) {
            Copy-Item -LiteralPath $oldFile -Destination (Join-Path $managedHome $name)
        }
    }
}

if ($Mode -eq 'Restore') {
    if (!(Test-Path -LiteralPath $statePath)) { Write-Output 'No installation backup found.'; return }
    $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
    if (!$state.installed) { Write-Output 'The original configuration is already restored.'; return }
    if ($state.userOptionsPath -ne $userOptions) { throw 'Backup belongs to a different user profile.' }
    if (Test-Path -LiteralPath $userOptions) {
        $currentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $userOptions).Hash
        if ($currentHash -ne $state.optionsHash) {
            throw 'Custom VM options were changed after installation; automatic restore stopped to preserve your edits.'
        }
    }
    if ($state.hadUserOptions -and !(Test-Path -LiteralPath $backupOptions)) { throw 'Original VM options backup is missing.' }
    if ($state.hadUserOptions) {
        Copy-Item -LiteralPath $backupOptions -Destination $userOptions -Force
    } elseif (Test-Path -LiteralPath $userOptions) {
        Remove-Item -LiteralPath $userOptions
    }
    $currentUserEnvironment = [Environment]::GetEnvironmentVariable($environmentName,'User')
    if ($currentUserEnvironment -eq $managedOptions -or
        $currentUserEnvironment -eq (Join-Path $legacyHome $productProfile.OptionsName)) {
        [Environment]::SetEnvironmentVariable($environmentName, $state.originalUserEnvironment, 'User')
        [Environment]::SetEnvironmentVariable($environmentName, $state.originalUserEnvironment, 'Process')
    } else {
        Write-Output "$environmentName was changed elsewhere; that newer value was preserved."
    }
    $state.installed = $false
    Save-State $state
    Notify-EnvironmentChange
    Write-Output "Original current-user $Product configuration restored. Restart $Product to apply."
    return
}

[void](New-Item -ItemType Directory -Force -Path $managedHome)
[void](New-Item -ItemType Directory -Force -Path $socketDir)
[void](New-Item -ItemType Directory -Force -Path (Split-Path -Parent $userOptions))
try { [void](New-Item -ItemType Directory -Force -Path $agentHome) }
catch { throw "Cannot create '$agentHome'. The installer needs write access to the IDE installation directory. Startup configuration was not updated. Original error: $($_.Exception.Message)" }

$existingState = $null
if (Test-Path -LiteralPath $statePath) {
    $existingState = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
    if ($existingState.userOptionsPath -ne $userOptions) { throw 'Backup belongs to a different user profile.' }
}
if ($existingState -and $existingState.installed) {
    $state = $existingState
} else {
    $state = [pscustomobject]@{
        schemaVersion = 1
        build = $build
        installed = $false
        originalUserEnvironment = [Environment]::GetEnvironmentVariable($environmentName,'User')
        hadUserOptions = (Test-Path -LiteralPath $userOptions)
        userOptionsPath = $userOptions
        optionsHash = ''
        baselinePath = ''
    }
    if ($state.hadUserOptions) { Copy-Item -LiteralPath $userOptions -Destination $backupOptions -Force }
    Save-State $state
}

$baseline = $null
foreach ($candidate in @(
    [Environment]::GetEnvironmentVariable($environmentName,'User'),
    [Environment]::GetEnvironmentVariable($environmentName,'Machine'),
    $userOptions,
    (Join-Path $ideHome ('bin\' + $productProfile.Launcher + '.vmoptions'))
)) {
    if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) { $baseline = $candidate; break }
}
if (!$baseline) { throw 'No base VM options file found.' }
$options = @(Get-Content -LiteralPath $baseline | Where-Object {
    $_ -notmatch '^\s*-javaagent:.*[\\/](ja-netfilter|local-state-agent(?:-[a-fA-F0-9]{12})?)\.jar(?:[=\s]|$)' -and
    $_ -notmatch '^\s*--add-opens=java.base/jdk\.internal\.org\.objectweb\.asm(?:\.tree)?=ALL-UNNAMED\s*$' -and
    $_ -notmatch '^\s*-D(?:intellij\.startup\.wizard|jdk\.net\.unixdomain\.tmpdir|java\.io\.tmpdir)='
})
$options += "-javaagent:$managedAgent=$profileId"
$options += '-Dintellij.startup.wizard=false'
$options += "-Djdk.net.unixdomain.tmpdir=$socketDir"
$options += "-Djava.io.tmpdir=$socketDir"

if (!(Test-Path -LiteralPath $managedAgent) -or
    (Get-FileHash -Algorithm SHA256 -LiteralPath $managedAgent).Hash -ne $expectedAgentHash) {
    try { Copy-Item -LiteralPath $sourceAgent -Destination $managedAgent -Force }
    catch { throw "Cannot copy Agent to '$managedAgent'. Check installation-directory write access and close the IDE if the file is in use. Startup configuration was not updated. Original error: $($_.Exception.Message)" }
}
Assert-SharedFileLocation $managedAgent
Test-AgentLoad
$optionsText = ($options -join [Environment]::NewLine) + [Environment]::NewLine
Write-TextAtomically $managedOptions $optionsText
Assert-SharedFileLocation $managedOptions
Write-TextAtomically $userOptions $optionsText
$state.baselinePath = $baseline
$state.optionsHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $userOptions).Hash
# Save rollback data before changing the persistent environment.
$state.installed = $true
Save-State $state
[Environment]::SetEnvironmentVariable($environmentName, $managedOptions, 'User')
[Environment]::SetEnvironmentVariable($environmentName, $managedOptions, 'Process')
Notify-EnvironmentChange
Write-Output "Configured $Product $profileId for direct EXE launch."
Write-Output "Agent: $managedAgent"
Write-Output "User configuration and rollback records: $managedHome"
Write-Output "Direct EXE: $(Join-Path $ideHome ('bin\' + $productProfile.Launcher))"
Write-Output 'Close existing instances of the selected IDE first. Then open the original EXE or shortcut.'
Write-Output 'Existing terminal/launcher processes may need restarting to receive the updated environment.'
Write-Output 'This is build-specific local behavior, not a permanent or server-side subscription.'
