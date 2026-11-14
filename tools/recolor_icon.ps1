<#
.SYNOPSIS
  Icons drawn in black vanish on a dark background. This lists how dark each icon of a set is, or
  recolours the dark pixels of one icon (keeping transparency) so it reads on dark and light backgrounds.
.EXAMPLE
  .\recolor_icon.ps1                                   # report: share of dark pixels per icon
  .\recolor_icon.ps1 -Name markdown -Color '#81A2BE'   # recolour markdown.png and markdown@2x.png
#>
param(
    [string]$Dir = (Join-Path $PSScriptRoot '..\io.github.jollygood1.fileicons\icons\web'),
    [string]$Name = '',
    [string]$Color = '#D8DCDF'
)
Add-Type -AssemblyName System.Drawing
function IsDark($c) { return (0.2126 * $c.R + 0.7152 * $c.G + 0.0722 * $c.B) / 255 -lt 0.28 }

if (-not $Name) {
    foreach ($f in Get-ChildItem $Dir -Filter *.png | Where-Object { $_.Name -notlike '*@2x*' }) {
        $b = New-Object Drawing.Bitmap $f.FullName
        $solid = 0; $dark = 0
        for ($y = 0; $y -lt $b.Height; $y++) { for ($x = 0; $x -lt $b.Width; $x++) {
            $c = $b.GetPixel($x, $y)
            if ($c.A -gt 40) { $solid++; if (IsDark $c) { $dark++ } }
        } }
        $b.Dispose()
        if ($solid -and ($dark / $solid) -gt 0.25) { '{0,-22} {1,3}% dark' -f $f.Name, [int](100 * $dark / $solid) }
    }
    return
}

$to = [Drawing.ColorTranslator]::FromHtml($Color)
foreach ($file in "$Name.png", "$Name@2x.png") {
    $path = Join-Path $Dir $file
    $src = New-Object Drawing.Bitmap $path
    $out = New-Object Drawing.Bitmap $src.Width, $src.Height, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) { for ($x = 0; $x -lt $src.Width; $x++) {
        $c = $src.GetPixel($x, $y)
        if ($c.A -gt 0 -and (IsDark $c)) { $c = [Drawing.Color]::FromArgb($c.A, $to.R, $to.G, $to.B) }
        $out.SetPixel($x, $y, $c)
    } }
    $src.Dispose()
    $out.Save($path, [Drawing.Imaging.ImageFormat]::Png); $out.Dispose()
    "recoloured $file -> $Color"
}
