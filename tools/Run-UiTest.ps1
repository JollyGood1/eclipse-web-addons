<#
.SYNOPSIS
  Runs one of the throwaway UI tests against the jars in dist\, in a separate Eclipse configuration and
  a fresh workspace. The Eclipse install itself is only read: nothing is written into it.
.DESCRIPTION
  -Test themes : tools\uitest      - the Syntax Colour Theme page (Apply, Restore Defaults, import, Light/Dark switch)
  -Test java   : tools\uitest      - a Java file under the Light window theme, without and with the semantic highlighting option
  -Test icons  : tools\iconstest  - the File Icons page, Project Explorer and tab icons, Git and error marks
  -Test import : tools\importtest  - the two Import buttons; the native file dialogs are answered from here
  Each test plug-in is compiled here, started with Eclipse, drives the UI, prints lines that start with
  UITEST / ICONTEST / IMPORTTEST, saves pictures into the work folder and closes Eclipse.
  Run .\build.ps1 first. Close no Eclipse for this: a second instance with its own configuration is used.
.EXAMPLE
  .\tools\Run-UiTest.ps1 -EclipseDir '<folder with eclipse.exe>' -Test icons
  .\tools\Run-UiTest.ps1 -EclipseDir '<folder with eclipse.exe>' -Test icons -Baseline   # without the plug-in, to compare
#>
param(
    [string]$EclipseDir = $env:ECLIPSE_HOME,
    [Parameter(Mandatory = $true)][ValidateSet('themes', 'java', 'icons', 'import')][string]$Test,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$WorkDir = (Join-Path $env:TEMP 'eclipse-web-addons-test'),
    [switch]$Baseline,
    [switch]$NoGit,
    [string]$FinalTheme = '',
    [int]$TimeoutSeconds = 400
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $root 'dist'
if (-not $EclipseDir -or -not (Test-Path (Join-Path $EclipseDir 'eclipsec.exe'))) { throw 'Pass -EclipseDir <folder with eclipse.exe> (or set ECLIPSE_HOME).' }
$javac = if ($Jdk) { Join-Path $Jdk 'bin\javac.exe' } else { (Get-Command javac -ErrorAction SilentlyContinue).Source }
if (-not $javac -or -not (Test-Path $javac)) { throw 'No JDK found: pass -Jdk <JDK folder>, set JAVA_HOME, or put javac on the PATH.' }
$jarExe = Join-Path (Split-Path $javac) 'jar.exe'
$distJars = @(Get-ChildItem $dist -Filter 'io.github.jollygood1.*.jar' -ErrorAction SilentlyContinue)
if (-not $distJars) { throw "No jars in $dist. Run build.ps1 first." }

$src = Join-Path $PSScriptRoot (@{ themes = 'uitest'; java = 'uitest'; icons = 'iconstest'; import = 'importtest' }[$Test])
$prefix = @{ themes = 'UITEST'; java = 'UITEST'; icons = 'ICONTEST'; import = 'IMPORTTEST' }[$Test]
New-Item -ItemType Directory -Force $WorkDir | Out-Null
$WorkDir = (Resolve-Path $WorkDir).Path
$out = Join-Path $WorkDir "out-$Test"
$ws = Join-Path $WorkDir "ws-$Test"
$cfg = Join-Path $WorkDir 'configuration'
foreach ($d in $out, $ws, $cfg, (Join-Path $WorkDir 'classes')) { if (Test-Path $d) { Remove-Item $d -Recurse -Force } }
New-Item -ItemType Directory -Force $out, (Join-Path $WorkDir 'classes') | Out-Null

function FileUrl([string]$path) { return 'file:/' + $path.Replace('\', '/').Replace(' ', '%20') }

# ---- compile and package the test plug-in ----
$classes = Join-Path $WorkDir 'classes'
$sources = Get-ChildItem (Join-Path $src 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 21 -encoding UTF-8 -nowarn -d $classes -cp ((Join-Path $EclipseDir 'plugins\*') + ';' + (Join-Path $dist '*')) $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed for the test plug-in' }
$testJar = Join-Path $WorkDir 'test-plugin.jar'
& $jarExe --create --file $testJar --manifest (Join-Path $src 'META-INF\MANIFEST.MF') -C $classes . -C $src plugin.xml
if ($LASTEXITCODE -ne 0) { throw 'jar failed for the test plug-in' }
$testManifest = Get-Content (Join-Path $src 'META-INF\MANIFEST.MF') -Raw
$testId = ([regex]'Bundle-SymbolicName: ([^;\s]+)').Match($testManifest).Groups[1].Value
$testVersion = ([regex]'Bundle-Version: (\S+)').Match($testManifest).Groups[1].Value

# ---- a configuration of its own: the install's bundle list plus our jars, minus the dropins scanner ----
New-Item -ItemType Directory -Force (Join-Path $cfg 'org.eclipse.equinox.simpleconfigurator') | Out-Null
$ini = Get-Content (Join-Path $EclipseDir 'configuration\config.ini') | ForEach-Object {
    if ($_ -like 'eclipse.p2.data.area=*') { 'eclipse.p2.data.area=@config.dir/p2/' } else { $_ }
}
[IO.File]::WriteAllLines((Join-Path $cfg 'config.ini'), $ini)
$bundles = Get-Content (Join-Path $EclipseDir 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info') |
    Where-Object { $_ -notlike 'org.eclipse.equinox.p2.reconciler.dropins,*' -and $_ -notlike 'io.github.jollygood1.*' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($jar in $distJars) {
    if ($Baseline -and $jar.Name -like '*fileicons*' -and $Test -eq 'icons') { continue }
    $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
    $reader = New-Object IO.StreamReader ($zip.GetEntry('META-INF/MANIFEST.MF').Open())
    $mf = $reader.ReadToEnd(); $reader.Dispose(); $zip.Dispose()
    $id = ([regex]'Bundle-SymbolicName: ([^;\s]+)').Match($mf).Groups[1].Value
    $version = ([regex]'Bundle-Version: (\S+)').Match($mf).Groups[1].Value
    $bundles += "$id,$version,$(FileUrl $jar.FullName),4,false"
}
$bundles += "$testId,$testVersion,$(FileUrl $testJar),4,false"
[IO.File]::WriteAllLines((Join-Path $cfg 'org.eclipse.equinox.simpleconfigurator\bundles.info'), $bundles)

# ---- fresh workspace: Dark window theme, no "really exit?" question ----
$settings = Join-Path $ws '.metadata\.plugins\org.eclipse.core.runtime\.settings'
New-Item -ItemType Directory -Force $settings | Out-Null
[IO.File]::WriteAllText((Join-Path $settings 'org.eclipse.e4.ui.css.swt.theme.prefs'), "eclipse.preferences.version=1`nthemeid=org.eclipse.e4.ui.css.theme.$(if ($Test -eq 'java') { 'e4_default' } else { 'e4_dark' })`n")
[IO.File]::WriteAllText((Join-Path $settings 'org.eclipse.ui.ide.prefs'), "eclipse.preferences.version=1`nEXIT_PROMPT_ON_CLOSE_LAST_WINDOW=false`n")

# ---- files the tests import: a second colour theme (white background, red keywords) and an image ----
$themeDir = Join-Path $WorkDir 'import'
New-Item -ItemType Directory -Force $themeDir | Out-Null
$themes = Join-Path $root 'io.github.jollygood1.syntaxthemes\themes'
$xml = (Get-Content (Join-Path $themes 'tomorrow-night.xml') -Raw).Replace('name="Tomorrow Night"', 'name="Import Test"')
$xml = $xml -replace '<background color="#[0-9A-Fa-f]{6}"', '<background color="#FFFFFF"' -replace '<foreground color="#[0-9A-Fa-f]{6}"', '<foreground color="#000000"' -replace '<keyword color="#[0-9A-Fa-f]{6}"', '<keyword color="#FF0000"'
$themeFile = Join-Path $themeDir 'Import Test.xml'
[IO.File]::WriteAllText($themeFile, $xml)
Copy-Item (Join-Path $themes 'tomorrow-night.tmtheme.json') (Join-Path $themeDir 'Import Test.tmtheme.json') -Force
$imageFile = Join-Path $root 'io.github.jollygood1.fileicons\icons\web\html5@2x.png'

$vmargs = @("-Duitest.out=$out")
if ($Test -eq 'themes') { $vmargs += "-Duitest.import=$themeFile"; if ($FinalTheme) { $vmargs += "-Duitest.finalTheme=$FinalTheme" } }
if ($Test -eq 'java') { $vmargs += '-Duitest.java=true' }
if ($Test -eq 'icons') { $vmargs += "-Duitest.import=$imageFile"; if ($Baseline) { $vmargs += '-Duitest.baseline=true' }; if ($NoGit) { $vmargs += '-Duitest.nogit=true' } }

# ---- run ----
$log = Join-Path $out 'eclipse-output.txt'
$argList = @('-clean', '-nosplash', '-configuration', "`"$cfg`"", '-data', "`"$ws`"", '-vmargs') + ($vmargs | ForEach-Object { "`"$_`"" })
$p = Start-Process (Join-Path $EclipseDir 'eclipsec.exe') -ArgumentList $argList -PassThru -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden
$end = (Get-Date).AddSeconds($TimeoutSeconds)

if ($Test -eq 'import') {
    # Answer the native "open file" dialogs: put the file name into the name box (WM_SETTEXT) and press Open (WM_COMMAND IDOK).
    Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
    Add-Type 'using System; using System.Runtime.InteropServices; public class UiTestWin32 { [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern IntPtr SendMessage(IntPtr h, uint msg, IntPtr w, string l); [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l); }'
    $A = [Windows.Automation.AutomationElement]; $T = [Windows.Automation.TreeScope]
    $pidCond = New-Object Windows.Automation.PropertyCondition $A::ProcessIdProperty, $p.Id
    $nameBox = New-Object Windows.Automation.PropertyCondition $A::AutomationIdProperty, '1148'
    $editType = New-Object Windows.Automation.PropertyCondition $A::ControlTypeProperty, ([Windows.Automation.ControlType]::Edit)
    $answered = @{}
    while (-not $p.HasExited -and (Get-Date) -lt $end) {
        Start-Sleep -Milliseconds 700
        try {
            foreach ($w in $A::RootElement.FindAll($T::Children, $pidCond)) {
                $combo = $w.FindFirst($T::Subtree, $nameBox)
                if (-not $combo) { continue }
                $dlg = $combo
                while ($dlg -and $dlg.Current.ControlType -ne [Windows.Automation.ControlType]::Window) { $dlg = [Windows.Automation.TreeWalker]::ControlViewWalker.GetParent($dlg) }
                $title = $dlg.Current.Name
                if ($answered[$title]) { continue }
                $file = if ($title -like '*colour theme*') { $themeFile } else { $imageFile }
                Write-Host "file dialog '$title' <- $file"
                $edit = $combo.FindFirst($T::Subtree, $editType); if (-not $edit) { $edit = $combo }
                [void][UiTestWin32]::SendMessage([IntPtr]$edit.Current.NativeWindowHandle, 0x000C, [IntPtr]::Zero, $file)
                Start-Sleep -Milliseconds 600
                [void][UiTestWin32]::PostMessage([IntPtr]$dlg.Current.NativeWindowHandle, 0x0111, [IntPtr]1, [IntPtr]::Zero)
                $answered[$title] = $true
            }
        } catch { Write-Host "dialog watcher: $_" }
    }
    Write-Host "file dialogs answered: $($answered.Count)"
}
while (-not $p.HasExited -and (Get-Date) -lt $end) { Start-Sleep -Seconds 2 }
if (-not $p.HasExited) { Write-Host "timeout after $TimeoutSeconds s: stopping the test Eclipse"; $p.Kill() }

Select-String -Path $log -Pattern "^$prefix" | ForEach-Object { $_.Line }
$wsLog = Join-Path $ws '.metadata\.log'
if (Test-Path $wsLog) {
    $bad = Select-String -Path $wsLog -Pattern 'jollygood1' | Where-Object { $_.Line -notmatch 'arguments:' }
    if ($bad) { Write-Host 'workspace log mentions the plug-ins:'; $bad | Select-Object -First 10 | ForEach-Object { '  ' + $_.Line } }
}
Write-Host "output folder: $out"
Get-ChildItem $out -Filter *.png | ForEach-Object { '  ' + $_.Name }
