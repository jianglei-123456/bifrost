# Generate hurl sample ebook (EPUB + PDF + cover)
# 纯 PowerShell + .NET 写 EPUB/PDF/封面，不依赖 ffmpeg 或 epublib
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$ebookDir = Join-Path $repo 'data-sample\ebook'
Remove-Item -Recurse -Force $ebookDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $ebookDir | Out-Null

# ============== Cover JPEG (16x16 solid color) ==============
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap(16, 16)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(64, 128, 192))
$g.Dispose()
$coverJpg = Join-Path $ebookDir 'cover-test.jpg'
$bmp.Save($coverJpg, [System.Drawing.Imaging.ImageFormat]::Jpeg)
$bmp.Dispose()
$coverBytes = [System.IO.File]::ReadAllBytes($coverJpg)

# ============== EPUB ==============
function New-Epub {
    param(
        [string]$Path,
        [string]$Title,
        [string]$Creator,
        [string]$Language,
        [string]$Identifier,
        [byte[]]$CoverJpg
    )
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tmp = [System.IO.Path]::GetTempFileName()
    Remove-Item $tmp -Force
    $tmpZip = "$tmp.zip"
    $zip = [System.IO.Compression.ZipFile]::Open($tmpZip, 'Create')

    # mimetype (STORED, must be first)
    $e = $zip.CreateEntry('mimetype', [System.IO.Compression.CompressionLevel]::NoCompression)
    $w = New-Object System.IO.StreamWriter($e.Open())
    $w.Write('application/epub+zip')
    $w.Dispose()

    # META-INF/container.xml
    $e = $zip.CreateEntry('META-INF/container.xml')
    $w = New-Object System.IO.StreamWriter($e.Open())
    $w.Write(@'
<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
'@)
    $w.Dispose()

    # OEBPS/content.opf
    $e = $zip.CreateEntry('OEBPS/content.opf')
    $w = New-Object System.IO.StreamWriter($e.Open())
    $coverBlock = if ($CoverJpg) {
@"
    <meta name="cover" content="cover-img"/>
"@
    } else { '' }
    $coverItem = if ($CoverJpg) {
@'
    <item id="cover-img" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
'@
    } else { '' }
    $w.Write(@"
<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="$Language">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">$Identifier</dc:identifier>
    <dc:title>$Title</dc:title>
    <dc:language>$Language</dc:language>
    <dc:creator>$Creator</dc:creator>
$coverBlock
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
$coverItem
  </manifest>
  <spine>
    <itemref idref="nav"/>
  </spine>
</package>
"@)
    $w.Dispose()

    # OEBPS/nav.xhtml
    $e = $zip.CreateEntry('OEBPS/nav.xhtml')
    $w = New-Object System.IO.StreamWriter($e.Open())
    $w.Write(@"
<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <body><nav epub:type="toc"><h1>$Title</h1></nav></body></html>
"@)
    $w.Dispose()

    # cover.jpg
    if ($CoverJpg) {
        $e = $zip.CreateEntry('OEBPS/cover.jpg')
        $s = $e.Open()
        $s.Write($CoverJpg, 0, $CoverJpg.Length)
        $s.Dispose()
    }

    $zip.Dispose()
    Move-Item $tmpZip $Path -Force
}

# ============== PDF (minimal) ==============
function New-Pdf {
    param(
        [string]$Path,
        [string]$Title,
        [string]$Author
    )
    # 极简 PDF 1.4（单页空内容）。PDFBox 3.x 可解析。
    $content = @"
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Count 1 /Kids [3 0 R] >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>
endobj
xref
0 4
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000110 00000 n
trailer
<< /Size 4 /Root 1 0 R >>
startxref
163
%%EOF
"@
    [System.IO.File]::WriteAllText($Path, $content, [System.Text.Encoding]::ASCII)
}

# ============== Generate ==============
New-Epub -Path (Join-Path $ebookDir 'sample.epub') `
    -Title 'Test Book' -Creator 'Test Author' `
    -Language 'en' -Identifier 'urn:uuid:hurl-ebook-001' `
    -CoverJpg $coverBytes
New-Pdf -Path (Join-Path $ebookDir 'sample.pdf') `
    -Title 'Test PDF' -Author 'Test PDF Author'

Write-Host "sample ebook generated: $ebookDir"
