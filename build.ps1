<#
.SYNOPSIS
  Builds the plugin jars without Maven: javac against the bundles of an Eclipse install, then jar.
  Output: dist\<plugin id>_<version>.jar for every plugin project folder (io.github.jollygood1.*).
.EXAMPLE
  .\build.ps1 -EclipseDir '<folder with eclipse.exe>'
  .\build.ps1 -EclipseDir '<folder with eclipse.exe>' -Only io.github.jollygood1.fileicons
  .\build.ps1 -EclipseDir '<folder with eclipse.exe>' -Jdk '<JDK 21 folder>'
.NOTES
  -EclipseDir may be left out when the environment variable ECLIPSE_HOME is set.
  -Jdk may be left out when JAVA_HOME points to a JDK 21 or newer, or when javac is on the PATH.
#>
param(
    [string]$EclipseDir = $env:ECLIPSE_HOME,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Only = ''
)
$ErrorActionPreference = 'Stop'
if (-not $EclipseDir -or -not (Test-Path (Join-Path $EclipseDir 'plugins'))) {
    throw 'Pass -EclipseDir <folder with eclipse.exe> (or set ECLIPSE_HOME).'
}
$javac = if ($Jdk) { Join-Path $Jdk 'bin\javac.exe' } else { (Get-Command javac -ErrorAction SilentlyContinue).Source }
if (-not $javac -or -not (Test-Path $javac)) {
    throw 'No JDK found: pass -Jdk <JDK folder>, set JAVA_HOME, or put javac on the PATH.'
}
$jarExe = Join-Path (Split-Path $javac) 'jar.exe'
$build = Join-Path $PSScriptRoot 'build'
$dist = Join-Path $PSScriptRoot 'dist'

# newest version of every bundle the code compiles against
$needed = 'org.eclipse.ui.workbench', 'org.eclipse.jface', 'org.eclipse.swt', 'org.eclipse.swt.win32.win32.x86_64',
          'org.eclipse.core.runtime', 'org.eclipse.core.commands', 'org.eclipse.equinox.common', 'org.eclipse.equinox.preferences',
          'org.eclipse.equinox.registry', 'org.eclipse.equinox.app', 'org.eclipse.osgi', 'org.osgi.service.prefs', 'org.osgi.service.event',
          'org.eclipse.core.resources', 'org.eclipse.core.jobs', 'org.eclipse.e4.ui.model.workbench', 'org.eclipse.e4.ui.workbench',
          'org.eclipse.emf.ecore', 'org.eclipse.emf.common'
$cp = foreach ($n in $needed) {
    # the SWT fragment is named after the platform; any org.eclipse.swt.<platform> jar will do for compiling
    $filter = if ($n -like 'org.eclipse.swt.*') { 'org.eclipse.swt.*_*.jar' } else { "${n}_*.jar" }
    $jar = Get-ChildItem (Join-Path $EclipseDir 'plugins') -Filter $filter | Where-Object { $_.Name -notlike '*.svg_*' } | Sort-Object Name | Select-Object -Last 1
    if (-not $jar) { throw "Bundle $n not found in $EclipseDir\plugins" }
    $jar.FullName
}

if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force $dist | Out-Null
$stamp = Get-Date -Format 'yyyyMMddHHmm'

$projects = Get-ChildItem $PSScriptRoot -Directory -Filter 'io.github.jollygood1.*' | Where-Object { -not $Only -or $_.Name -eq $Only }
foreach ($p in $projects) {
    $id = $p.Name
    $proj = $p.FullName
    $classes = "$build\$id\classes"
    New-Item -ItemType Directory -Force $classes | Out-Null

    $sources = Get-ChildItem "$proj\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & $javac --release 21 -encoding UTF-8 -Xlint:all -d $classes -cp ($cp -join ';') $sources
    if ($LASTEXITCODE -ne 0) { throw "javac failed for $id" }

    # non-Java files in src (messages.properties) go next to the classes
    Get-ChildItem "$proj\src" -Recurse -File | Where-Object { $_.Extension -ne '.java' } | ForEach-Object {
        $target = Join-Path $classes $_.FullName.Substring("$proj\src".Length + 1)
        New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
        Copy-Item $_.FullName $target
    }

    # 1.0.0.qualifier -> 1.0.0.<build time>, as PDE and Tycho do
    $manifest = (Get-Content "$proj\META-INF\MANIFEST.MF" -Raw).Replace('.qualifier', ".$stamp")
    [IO.File]::WriteAllText("$build\$id\MANIFEST.MF", $manifest, (New-Object Text.UTF8Encoding $false))
    $version = ([regex]'Bundle-Version: (\S+)').Match($manifest).Groups[1].Value

    # everything else in the project folder goes into the jar (plugin.xml, OSGI-INF, schema, themes, icons ...)
    $skip = 'src', 'bin', 'META-INF', 'build.properties', '.project', '.classpath', '.settings'
    $jarArgs = @('--create', '--file', "$dist\${id}_$version.jar", '--manifest', "$build\$id\MANIFEST.MF", '-C', $classes, '.')
    Get-ChildItem $proj -Force | Where-Object { $skip -notcontains $_.Name } | ForEach-Object { $jarArgs += @('-C', $proj, $_.Name) }

    Get-ChildItem $dist -Filter "${id}_*.jar" | Remove-Item -Force
    & $jarExe @jarArgs
    if ($LASTEXITCODE -ne 0) { throw "jar failed for $id" }
    Write-Host "Built $dist\${id}_$version.jar"
}
