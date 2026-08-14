# Generate hurl sample music with tags (requires ffmpeg)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$music = Join-Path $repo 'data-sample\music'
Remove-Item -Recurse -Force $music -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $music | Out-Null

$jayDir = Join-Path $music '周杰伦\叶惠美'
$beatlesDir = Join-Path $music 'Beatles\Let It Be'
$soloDir = Join-Path $music 'solo'
New-Item -ItemType Directory -Force -Path $jayDir, $beatlesDir, $soloDir | Out-Null

# MP3 with Chinese tags (pinyin index: 周杰伦 -> Z)
& ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=2" `
  -metadata title="以父之名" -metadata artist="周杰伦" -metadata album_artist="周杰伦" `
  -metadata album="叶惠美" -metadata track="1" -metadata year="2003" `
  (Join-Path $jayDir '01 - 以父之名.mp3')
# FLAC
& ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=330:duration=2" `
  -metadata title="Let It Be" -metadata artist="The Beatles" -metadata album_artist="The Beatles" `
  -metadata album="Let It Be" -metadata track="1" -metadata year="1970" `
  (Join-Path $beatlesDir '01 - Let It Be.flac')
# M4A without tags (unknown artist / unknown album)
& ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=550:duration=2" `
  (Join-Path $soloDir '01 - 无题.m4a')
# folder cover
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap(16, 16)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(51, 102, 153))
$g.Dispose()
$bmp.Save((Join-Path $jayDir 'cover.jpg'), [System.Drawing.Imaging.ImageFormat]::Jpeg)
$bmp.Dispose()

Write-Host "sample music generated: $music"
