# Builds the Cutter Ray texture: the vanilla Pulse Rifle texture (96 x 96) on a canvas 24 pixels taller, with a solid
# bright red-orange (#ff2a1a) patch of 24 x 24 pixels in the new strip at the bottom left. The beam node of the
# Cutter Ray models reads its colour from that patch (and is drawn fullbright, so it looks lit from within).
#   powershell -File tools/assets/make_ray_texture.ps1 -Vanilla <folder holding the vanilla Common/>
param([Parameter(Mandatory = $true)][string]$Vanilla)
Add-Type -AssemblyName System.Drawing
$source = Join-Path $Vanilla 'Common/Items/Hypixel/Minigames/Space_Swarm/Weapons/Pulse_Rifle_Texture.png'
$out = Join-Path $PSScriptRoot '../../src/main/resources/Common/Items/Equipment_of_Arrakis'
New-Item -ItemType Directory -Force $out | Out-Null
$src = [Drawing.Bitmap]::FromFile($source)
$bmp = New-Object Drawing.Bitmap $src.Width, ($src.Height + 24), ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [Drawing.Graphics]::FromImage($bmp)
$g.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
$g.DrawImage($src, 0, 0, $src.Width, $src.Height)
$red = [Drawing.Color]::FromArgb(255, 0xff, 0x2a, 0x1a)
for ($y = $src.Height; $y -lt $src.Height + 24; $y++) { for ($x = 0; $x -lt 24; $x++) { $bmp.SetPixel($x, $y, $red) } }
$g.Dispose(); $src.Dispose()
$bmp.Save((Join-Path (Resolve-Path $out) 'Arrakis_Cutter_Ray_Texture.png'), [Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host "wrote Arrakis_Cutter_Ray_Texture.png, red patch at (0, 96)"
