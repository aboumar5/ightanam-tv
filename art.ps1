# Draws the app's banner (TV home row) and icon: gold lettering and a crescent on a deep night sky.
# Uses WPF so OpenType (CFF) fonts like Thmanyah render. Called by build.ps1; writes res\drawable-xhdpi\*.png
Add-Type -AssemblyName PresentationCore, WindowsBase
$ErrorActionPreference = 'Stop'
$M = 'System.Windows.Media'

# Thmanyah Sans Black from .\fonts if present (see build.ps1), else Sakkal Majalla (ships with Windows).
$thm = Get-ChildItem (Join-Path $PSScriptRoot 'fonts') -Recurse -Include '*Black.ttf', '*Black.otf' -ErrorAction SilentlyContinue |
       Where-Object Name -match '^thmanyah' | Where-Object Name -notmatch 'serif' | Select-Object -First 1
if ($thm) {
    $glyphs = New-Object "$M.GlyphTypeface" (New-Object System.Uri $thm.FullName)
    $family = New-Object "$M.FontFamily" ((New-Object System.Uri ($thm.DirectoryName + '\')), ('./#' + $glyphs.FamilyNames[[Globalization.CultureInfo]::GetCultureInfo('en-US')]))
    $face = New-Object "$M.Typeface" $family, ([System.Windows.FontStyles]::Normal), ([System.Windows.FontWeights]::Black), ([System.Windows.FontStretches]::Normal)
} else {
    $face = New-Object "$M.Typeface" (New-Object "$M.FontFamily" 'Sakkal Majalla'), ([System.Windows.FontStyles]::Normal), ([System.Windows.FontWeights]::Bold), ([System.Windows.FontStretches]::Normal)
}

function C($a, $r, $g, $b) { [System.Windows.Media.Color]::FromArgb($a, $r, $g, $b) }
function P($x, $y) { New-Object System.Windows.Point ([double]$x), ([double]$y) }
function Frozen($o) { $o.Freeze(); $o }

function Draw-Glow($dc, $cx, $cy, $rad, $color) {
    $b = New-Object "$M.RadialGradientBrush" $color, (C 0 $color.R $color.G $color.B)
    $dc.DrawEllipse((Frozen $b), $null, (P $cx $cy), $rad, $rad)
}

# Faint eight-pointed stars (two overlapping squares), the classic Islamic lattice.
function Draw-Lattice($dc, $w, $h, $step, $alpha) {
    $pen = Frozen (New-Object "$M.Pen" (Frozen (New-Object "$M.SolidColorBrush" (C $alpha 233 192 105))), 1.2)
    $r = $step * 0.34
    for ($y = 0; $y -le $h + $step; $y += $step) {
        for ($x = 0; $x -le $w + $step; $x += $step) {
            foreach ($rot in 0, 45) {
                $geo = New-Object "$M.StreamGeometry"
                $ctx = $geo.Open()
                for ($k = 0; $k -lt 4; $k++) {
                    $t = ($rot + 45 + 90 * $k) * [Math]::PI / 180
                    $pt = P ($x + $r * [Math]::Cos($t)) ($y + $r * [Math]::Sin($t))
                    if ($k -eq 0) { $ctx.BeginFigure($pt, $false, $true) } else { $ctx.LineTo($pt, $true, $false) }
                }
                $ctx.Close()
                $dc.DrawGeometry($null, $pen, (Frozen $geo))
            }
        }
    }
}

function Draw-Crescent($dc, $cx, $cy, $r) {
    $o = $r * 0.36
    $outer = New-Object "$M.EllipseGeometry" (P $cx $cy), $r, $r
    $inner = New-Object "$M.EllipseGeometry" (P ($cx + $o * 0.92) ($cy - $o * 0.35)), ($r * 0.92), ($r * 0.92)
    $moon = New-Object "$M.CombinedGeometry" ([System.Windows.Media.GeometryCombineMode]::Exclude), $outer, $inner
    $gold = New-Object "$M.LinearGradientBrush" (C 255 250 228 170), (C 255 201 151 47), 60
    $dc.DrawGeometry((Frozen $gold), $null, $moon)
}

function New-Art($path, $w, $h, $textSize, $textY, $moon) {
    $visual = New-Object "$M.DrawingVisual"
    $dc = $visual.RenderOpen()

    $sky = New-Object "$M.LinearGradientBrush" (C 255 18 24 56), (C 255 6 8 20), 90
    $dc.DrawRectangle((Frozen $sky), $null, (New-Object System.Windows.Rect 0, 0, $w, $h))
    Draw-Glow $dc ($w * 0.5) ($h * 0.55) ([Math]::Max($w, $h) * 0.7) (C 110 40 56 130)
    Draw-Glow $dc ($w * 0.5) ($h * 0.55) ([Math]::Max($w, $h) * 0.38) (C 34 233 192 105)
    Draw-Lattice $dc $w $h ([int]($h / 2.6)) 16
    Draw-Crescent $dc $moon[0] $moon[1] $moon[2]

    # The word: soft shadow, then a gold gradient. "اغتنم" as code points (PS 5.1 reads scripts as ANSI).
    $label = -join ([char[]](0x0627, 0x063A, 0x062A, 0x0646, 0x0645))
    $make = {
        param($brush)
        $ft = New-Object "$M.FormattedText" $label, ([Globalization.CultureInfo]::GetCultureInfo('ar-SA')),
            ([System.Windows.FlowDirection]::RightToLeft), $face, ([double]$textSize), $brush, 1.0
        $ft.TextAlignment = [System.Windows.TextAlignment]::Center
        $ft.MaxTextWidth = $w
        $ft
    }
    $shadow = & $make (Frozen (New-Object "$M.SolidColorBrush" (C 140 0 0 0)))
    $top = $textY + ($h - $textY - $shadow.Height) / 2
    $dc.DrawText($shadow, (P 0 ($top + $h * 0.015)))
    $gold = New-Object "$M.LinearGradientBrush" (C 255 252 232 178), (C 255 196 146 44), 90
    $dc.DrawText((& $make (Frozen $gold)), (P 0 $top))

    # Hairline gold frame.
    $inset = $h * 0.045
    $pen = Frozen (New-Object "$M.Pen" (Frozen (New-Object "$M.SolidColorBrush" (C 90 233 192 105))), ([Math]::Max(1, $h / 120)))
    $dc.DrawRectangle($null, $pen, (New-Object System.Windows.Rect $inset, $inset, ($w - 2 * $inset), ($h - 2 * $inset)))
    $dc.Close()

    $bmp = New-Object System.Windows.Media.Imaging.RenderTargetBitmap $w, $h, 96, 96, ([System.Windows.Media.PixelFormats]::Pbgra32)
    $bmp.Render($visual)
    $enc = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $enc.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bmp))
    $out = Join-Path $PSScriptRoot $path
    New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
    $fs = [IO.File]::Create($out)
    try { $enc.Save($fs) } finally { $fs.Dispose() }
}

# 640x360 banner (xhdpi) and 384x384 icon.
New-Art 'res\drawable-xhdpi\banner.png' 640 360 150 24 @(112, 92, 30)
New-Art 'res\drawable-xhdpi\icon.png'   384 384 120 124 @(192, 108, 46)
