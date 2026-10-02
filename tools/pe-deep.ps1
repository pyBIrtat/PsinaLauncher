# pe-deep.ps1 -- deeper PE inspection: export table (JNI surface), embedded PE/MZ scan,
# magic signature scan (zip/gzip/lzma/png/sqlite/elf), sliding entropy map, string dump.
# ASCII-only (Windows PowerShell 5.1 has no BOM handling for .ps1).
# Usage: powershell -File pe-deep.ps1 <file> [-OutDir <dir>]
param([Parameter(Mandatory=$true)][string]$Path, [string]$OutDir = '')

$ErrorActionPreference = 'Stop'
$b = [System.IO.File]::ReadAllBytes($Path)
$len = $b.Length
function U16($o) { [BitConverter]::ToUInt16($b, $o) }
function U32($o) { [BitConverter]::ToUInt32($b, $o) }
function I32($o) { [BitConverter]::ToInt32($b, $o) }
function U64($o) { [BitConverter]::ToUInt64($b, $o) }
function CStr($o) { if ($o -lt 0) { return '' }; $e = $o; while ($e -lt $len -and $b[$e] -ne 0) { $e++ }; [System.Text.Encoding]::ASCII.GetString($b, $o, [Math]::Max(0, $e - $o)) }
function Entropy($start, $size) {
    if ($size -le 0 -or ($start + $size) -gt $len) { return 0.0 }
    $counts = New-Object 'int[]' 256
    for ($k = $start; $k -lt ($start + $size); $k++) { $counts[$b[$k]]++ }
    $e = 0.0
    foreach ($c in $counts) { if ($c -gt 0) { $p = $c / $size; $e -= $p * [Math]::Log($p, 2) } }
    return $e
}

Write-Output ("==== DEEP: {0} ({1} bytes)" -f $Path, $len)
$pe = I32 0x3C
$nSec = U16 ($pe + 6)
$opt = $pe + 24
$magic = U16 $opt
$is64 = ($magic -eq 0x20B)
$optSize = U16 ($pe + 20)
$dd = $opt + $(if ($is64) { 112 } else { 96 })
$secStart = $opt + $optSize
$secs = @()
for ($i = 0; $i -lt $nSec; $i++) {
    $s = $secStart + $i * 40
    $secs += [pscustomobject]@{ Name=(CStr $s); VA=(U32 ($s+12)); VS=(U32 ($s+8)); RawSize=(U32 ($s+16)); RawPtr=(U32 ($s+20)) }
}
function RvaToOff($rva) {
    foreach ($s in $secs) {
        $span = [Math]::Max($s.VS, $s.RawSize)
        if ($rva -ge $s.VA -and $rva -lt ($s.VA + $span)) { return $s.RawPtr + ($rva - $s.VA) }
    }
    return -1
}

# ---------------- exports (JNI surface) ----------------
Write-Output ""
Write-Output "---- EXPORTS (what Java can call / what the DLL presents)"
$expRva = U32 ($dd + 0*8)
if ($expRva -eq 0) { Write-Output "  (no export directory)" }
else {
    $eo = RvaToOff $expRva
    $nameRva = U32 ($eo + 12)
    $ordBase = U32 ($eo + 16)
    $numFunc = U32 ($eo + 20)
    $numName = U32 ($eo + 24)
    $namesRva = U32 ($eo + 32)
    Write-Output ("  dllName   : " + (CStr (RvaToOff $nameRva)))
    Write-Output ("  ordinalBase: {0}  functions: {1}  names: {2}" -f $ordBase, $numFunc, $numName)
    $no = RvaToOff $namesRva
    $from = if ($OutDir -and $numName -gt 200) { 0 } else { 0 }
    for ($i = $from; $i -lt $numName; $i++) {
        if ($i -gt 300) { Write-Output ("  ... and {0} more" -f ($numName - 300)); break }
        $nr = U32 ($no + $i * 4)
        Write-Output ("    " + (CStr (RvaToOff $nr)))
    }
}

# ---------------- embedded PE / MZ scan ----------------
# ISO-8859-1 gives a 1:1 byte->char map, so .NET IndexOf scans 27 MB in milliseconds.
$l1 = [System.Text.Encoding]::GetEncoding(28591).GetString($b)
Write-Output ""
Write-Output "---- embedded MZ/PE scan (dropped payloads?)"
$found = 0
$pos = 0
while ($true) {
    $i = $l1.IndexOf('MZ', $pos)
    if ($i -lt 0) { break }
    $pos = $i + 1
    if (($i + 0x40) -ge $len) { continue }
    $lf = I32 ($i + 0x3C)
    if ($lf -le 0 -or ($i + $lf + 4) -ge $len) { continue }
    $p = $i + $lf
    if ($b[$p] -eq 0x50 -and $b[$p+1] -eq 0x45 -and $b[$p+2] -eq 0 -and $b[$p+3] -eq 0) {
        Write-Output ("  MZ+PE at 0x{0:X} (max size {1} bytes)" -f $i, ($len - $i))
        $found++
        if ($found -gt 12) { Write-Output "  ..."; break }
    }
}
if ($found -eq 0) { Write-Output "  none" }

# ---------------- magic signatures ----------------
Write-Output ""
Write-Output "---- archive/media signatures"
$sigs = [ordered]@{
    'ZIP/PK'      = "PK" + [char]3 + [char]4
    'ZIP-empty'   = "PK" + [char]5 + [char]6
    'GZIP'        = [string][char]0x1F + [char]0x8B
    '7z'          = [string][char]0x37 + [char]0x7A + [char]0xBC + [char]0xAF
    'RAR'         = 'Rar!'
    'CAB/MSCF'    = 'MSCF'
    'PNG'         = [string][char]0x89 + 'PNG'
    'SQLite'      = 'SQLite f'
    'ELF'         = [string][char]0x7F + 'ELF'
    'ZLIB 78 9C'  = [string][char]0x78 + [char]0x9C
    'ZLIB 78 DA'  = [string][char]0x78 + [char]0xDA
}
foreach ($k in $sigs.Keys) {
    $pat = $sigs[$k]
    $hits = New-Object System.Collections.Generic.List[int]
    $p2 = 0
    while ($hits.Count -lt 6) {
        $idx = $l1.IndexOf($pat, $p2)
        if ($idx -lt 0) { break }
        $hits.Add($idx)
        $p2 = $idx + 1
    }
    if ($hits.Count -gt 0) {
        Write-Output ("  {0,-13} x{1} at {2}" -f $k, $hits.Count, (($hits | ForEach-Object { '0x{0:X}' -f $_ }) -join ', '))
    }
}

# ---------------- entropy map (64 KB windows) ----------------
Write-Output ""
Write-Output "---- entropy map (64 KB windows); >7.5 = encrypted/compressed, <1.5 = zeros/padding"
$win = 65536
$line = ""
for ($o = 0; $o -lt $len; $o += $win) {
    $sz = [Math]::Min($win, $len - $o)
    $e = Entropy $o $sz
    $ch = if ($e -gt 7.5) { '#' } elseif ($e -gt 6.0) { '+' } elseif ($e -gt 3.0) { '.' } else { '_' }
    $line += $ch
    if ($line.Length -ge 96) { Write-Output ("  0x{0:X8} " -f ($o - 95*$win)) ; Write-Output ("  " + $line); $line = "" }
}
if ($line.Length -gt 0) { Write-Output ("  0x{0:X8} " -f ($len - $line.Length * $win)) ; Write-Output ("  " + $line) }

# ---------------- strings ----------------
if ($OutDir) {
    if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }
    $ascii = [System.Text.Encoding]::ASCII.GetString($b)
    $utf16 = [System.Text.Encoding]::Unicode.GetString($b)
    $sa = [regex]::Matches($ascii, '[\x20-\x7E]{6,}') | ForEach-Object { $_.Value }
    $su = [regex]::Matches($utf16, '[\x20-\x7E]{6,}') | ForEach-Object { $_.Value }
    $sa | Sort-Object -Unique | Set-Content -Encoding UTF8 (Join-Path $OutDir 'strings-ascii.txt')
    $su | Sort-Object -Unique | Set-Content -Encoding UTF8 (Join-Path $OutDir 'strings-utf16.txt')
    Write-Output ""
    Write-Output ("---- strings written: ascii={0} utf16={1} -> {2}" -f $sa.Count, $su.Count, $OutDir)

    $kw = 'webhook|discord|telegram|t\.me|api\.|http|Bearer|token|password|leveldb|Local Storage|Login Data|cookies|tdata|AppData|accessToken|hwid|MachineGuid|minecraft|launcher_|refresh_token|funpay|pulsevisuals|license|key|auth|verify|\.dll|\.exe|\.dat|\.bin|http://|https://|POST |GET |User-Agent|Content-Type'
    $interesting = New-Object System.Collections.Generic.HashSet[string]
    foreach ($s in (@($sa) + @($su))) { if ($s -match $kw) { [void]$interesting.Add($s.Trim()) } }
    $sorted = $interesting | Sort-Object
    $sorted | Set-Content -Encoding UTF8 (Join-Path $OutDir 'strings-interesting.txt')
    Write-Output ("---- interesting strings: {0}" -f $sorted.Count)
}
