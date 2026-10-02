# pe-report.ps1 -- PE inspection without external tools: headers, sections + entropy,
# import table (DLL + functions), debug/PDB, .NET flag, keyword string scan.
# ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI without a BOM.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File pe-report.ps1 <file> [-Strings]
param([Parameter(Mandatory=$true)][string]$Path, [switch]$Strings)

$ErrorActionPreference = 'Stop'
$b = [System.IO.File]::ReadAllBytes($Path)
$len = $b.Length
function U16($o) { [BitConverter]::ToUInt16($b, $o) }
function U32($o) { [BitConverter]::ToUInt32($b, $o) }
function I32($o) { [BitConverter]::ToInt32($b, $o) }
function U64($o) { [BitConverter]::ToUInt64($b, $o) }
function CStr($o) { if ($o -lt 0) { return '' }; $e = $o; while ($e -lt $len -and $b[$e] -ne 0) { $e++ }; [System.Text.Encoding]::ASCII.GetString($b, $o, [Math]::Max(0, $e - $o)) }

Write-Output "==== $Path"
Write-Output ("size        : {0} bytes" -f $len)
if ($len -lt 64 -or $b[0] -ne 0x4D -or $b[1] -ne 0x5A) { Write-Output "NOT A PE (no MZ)"; exit 1 }
$pe = I32 0x3C
if ($b[$pe] -ne 0x50 -or $b[$pe+1] -ne 0x45) { Write-Output "NOT A PE (no PE sig)"; exit 1 }

$machine   = U16 ($pe + 4)
$nSec      = U16 ($pe + 6)
$stamp     = U32 ($pe + 8)
$optSize   = U16 ($pe + 20)
$chars     = U16 ($pe + 22)
$opt       = $pe + 24
$magic     = U16 $opt
$is64      = ($magic -eq 0x20B)
$entry     = U32 ($opt + 16)
$imageBase = if ($is64) { U64 ($opt + 24) } else { [uint64](U32 ($opt + 28)) }
$subsystem = U16 ($opt + 68)
$nrva      = U32 ($opt + 108)
$dd        = $opt + 112
if (-not $is64) { $dd = $opt + 96 }

$machName = switch ($machine) { 0x8664 {'x86-64'} 0x14C {'x86-32'} 0xAA64 {'ARM64'} default {'unknown'} }
$subName  = switch ($subsystem) { 2 {'GUI'} 3 {'CONSOLE'} default {'other'} }
Write-Output ("machine     : 0x{0:X4} {1}" -f $machine, $machName)
Write-Output ("pe          : {0}" -f $(if ($is64) {'PE32+'} else {'PE32'}))
Write-Output ("sections    : {0}" -f $nSec)
Write-Output ("timestamp   : {0}  ({1} UTC)" -f $stamp, ([DateTimeOffset]::FromUnixTimeSeconds($stamp).ToString('yyyy-MM-dd HH:mm:ss')))
Write-Output ("subsystem   : {0} {1}" -f $subsystem, $subName)
Write-Output ("entryRVA    : 0x{0:X}" -f $entry)
Write-Output ("imageBase   : 0x{0:X}" -f $imageBase)
Write-Output ("flags       : 0x{0:X4} {1}" -f $chars, $(if ($chars -band 0x2) {'EXECUTABLE'} else {'not-executable(?)'}))
$isNet = $false
if ($nrva -gt 14) { $isNet = (U32 ($dd + 14*8)) -ne 0 }
Write-Output ("isDotNet    : {0}" -f $isNet)

$secStart = $opt + $optSize
$secs = @()
Write-Output ""
Write-Output "---- sections (name / virtAddr / virtSize / rawSize / rawPtr / entropy)"
for ($i = 0; $i -lt $nSec; $i++) {
    $s = $secStart + $i * 40
    $name = (CStr $s)
    $vs = U32 ($s + 8); $va = U32 ($s + 12); $rs = U32 ($s + 16); $rp = U32 ($s + 20)
    $ent = 0.0
    if ($rs -gt 0 -and ($rp + $rs) -le $len) {
        $counts = New-Object 'int[]' 256
        $end = $rp + $rs
        for ($k = $rp; $k -lt $end; $k++) { $counts[$b[$k]]++ }
        foreach ($c in $counts) { if ($c -gt 0) { $p = $c / $rs; $ent -= $p * [Math]::Log($p, 2) } }
    }
    $secs += [pscustomobject]@{ Name=$name; VA=$va; VS=$vs; RawSize=$rs; RawPtr=$rp }
    Write-Output ("  {0,-10} 0x{1:X8} 0x{2:X8} {3,10} 0x{4:X8}  {5:N3}" -f $name, $va, $vs, $rs, $rp, $ent)
}

function RvaToOff($rva) {
    foreach ($s in $secs) {
        $span = [Math]::Max($s.VS, $s.RawSize)
        if ($rva -ge $s.VA -and $rva -lt ($s.VA + $span)) { return $s.RawPtr + ($rva - $s.VA) }
    }
    return -1
}

Write-Output ""
Write-Output "---- imports"
$impRva = U32 ($dd + 1*8)
if ($impRva -eq 0) { Write-Output "  (no import table)" }
else {
    $off = RvaToOff $impRva
    $dlls = 0; $fns = 0
    $allNames = New-Object System.Collections.Generic.List[string]
    while ($off -gt 0 -and ($off + 20) -le $len) {
        $oft = U32 $off; $nameRva = U32 ($off + 12); $ft = U32 ($off + 16)
        if ($oft -eq 0 -and $nameRva -eq 0 -and $ft -eq 0) { break }
        $dll = CStr (RvaToOff $nameRva)
        $names = New-Object System.Collections.Generic.List[string]
        $thunkRva = if ($oft -ne 0) { $oft } else { $ft }
        $to = RvaToOff $thunkRva
        $step = if ($is64) { 8 } else { 4 }
        while ($to -gt 0 -and ($to + $step) -le $len) {
            $v = if ($is64) { U64 $to } else { [uint64](U32 $to) }
            if ($v -eq 0) { break }
            # [uint64]1 -shl 63: литерал 0x8000000000000000 не приводится (Int64 overflow)
            $hiBit = if ($is64) { [uint64]1 -shl 63 } else { [uint64]0x80000000 }
            if (($v -band $hiBit) -ne 0) { $names.Add("Ordinal#" + ($v -band 0xFFFF)) }
            else { $no = RvaToOff ([uint32]$v); if ($no -gt 0) { $names.Add((CStr ($no + 2))) } }
            $to += $step
        }
        Write-Output ("  {0}  ({1} funcs)" -f $dll, $names.Count)
        foreach ($n in $names) { $allNames.Add($n); $fns++ }
        $dlls++
        $off += 20
    }
    Write-Output ""
    Write-Output ("  TOTAL: {0} DLL, {1} imported functions" -f $dlls, $fns)
    Write-Output ""
    Write-Output "---- notable imports by category"
    $cats = [ordered]@{
        'HTTP/Internet' = 'WinHttp|Internet(Open|Connect|ReadFile|Query)|URLDownload|HttpQuery|InternetCrackUrl'
        'Sockets'       = '^(socket|connect|send|recv|WSA|getaddrinfo|gethostbyname|inet_addr|htons|bind|listen)'
        'Keyboard/hooks'= 'GetAsyncKeyState|SetWindowsHook|GetKeyboardState|MapVirtualKey|GetKeyState|RegisterHotKey|GetRawInput'
        'Screen/capture'= 'BitBlt|CreateCompatibleDC|GetDC|GetWindowDC|CreateCompatibleBitmap|PrintWindow|GetSystemMetrics|StretchBlt'
        'Process/inject'= 'CreateProcess|ShellExecute|WinExec|CreateRemoteThread|VirtualAllocEx|WriteProcessMemory|OpenProcess|NtUnmapView|QueueUserAPC|SetThreadContext'
        'Files/disk'    = 'CreateFile|ReadFile|WriteFile|FindFirstFile|DeleteFile|MoveFile|GetVolumeInformation|GetDiskFreeSpace|FindNextFile'
        'Registry'      = 'Reg(Open|Query|Set|Create|Delete|Enum)'
        'Crypto'        = 'Crypt(Encrypt|Decrypt|Gen|Hash|Import|Export|Protect)|BCrypt|NCrypt'
        'Hardware/HWID' = 'GetAdaptersInfo|GetAdaptersAddresses|SetupDi|WMI|GetComputerName|GetUserName|GetSystemFirmware|DeviceIoControl|GetLogicalDriveStrings|GetVolumeNameForVolumeMountPoint'
        'Anti-debug'    = 'IsDebuggerPresent|CheckRemoteDebugger|NtQueryInformationProcess|OutputDebugString|QueryPerformanceCounter|NtSetInformationThread'
        'UI/windows'    = 'CreateWindowEx|ShowWindow|MessageBox|SetWindowLong|GetForegroundWindow|SetCursorPos|mouse_event|SendInput|SetWindowPos'
        'Java/JNI'      = 'JNI_|jvm|Java_'
        'DLL loading'   = 'LoadLibrary|GetProcAddress|FreeLibrary|GetModuleHandle'
        'Threads/anti-sandbox' = 'CreateThread|SleepEx|QueryFullProcessImageName|NtQuerySystemInformation|RtlGetVersion'
    }
    foreach ($k in $cats.Keys) {
        $re = $cats[$k]
        $hit = $allNames | Where-Object { $_ -match $re } | Sort-Object -Unique
        if ($hit) { Write-Output ("  [" + $k + "] " + ($hit -join ', ')) }
    }
    Write-Output ""
    Write-Output "---- all imported functions (unique, first 400)"
    $allNames | Sort-Object -Unique | Select-Object -First 400 | ForEach-Object { Write-Output ("    " + $_) }
}

Write-Output ""
Write-Output "---- debug directory (PDB path)"
$dbgRva = U32 ($dd + 6*8)
if ($dbgRva -eq 0) { Write-Output "  (no debug directory)" }
else {
    $o = RvaToOff $dbgRva
    for ($i = 0; $i -lt 8; $i++) {
        $e = $o + $i * 28
        if ($e + 28 -gt $len) { break }
        $type = U32 ($e + 12); $size = U32 ($e + 16); $ptr = U32 ($e + 24)
        if ($type -eq 0 -and $size -eq 0) { break }
        if ($type -eq 2 -and $ptr -gt 0 -and ($ptr + 24) -lt $len) {
            $sig = [System.Text.Encoding]::ASCII.GetString($b, $ptr, 4)
            $age = U32 ($ptr + 20)
            Write-Output ("  CodeView " + $sig + " age=" + $age)
            Write-Output ("  PDB: " + (CStr ($ptr + 24)))
        }
    }
}

if ($Strings) {
    Write-Output ""
    Write-Output "---- keyword strings"
    $ascii = [System.Text.Encoding]::ASCII.GetString($b)
    $utf16 = [System.Text.Encoding]::Unicode.GetString($b)
    $kw = 'webhook|discord|telegram|t\.me/|api\.|http://|https://|Bearer |token|password|passwd|leveldb|Local Storage|Login Data|cookies|Telegram Desktop|tdata|AppData|accessToken|hwid|HWID|MachineGuid|mincraft|minecraft|launcher_accounts|launcher_profiles|refresh_token|funpay|pulsevisuals|license|\.pdb|VMProtect|Themida|UPX|Enigma'
    $hits = New-Object System.Collections.Generic.HashSet[string]
    foreach ($blob in @($ascii, $utf16)) {
        foreach ($m in [regex]::Matches($blob, '[\x20-\x7E]{7,}')) {
            $s = $m.Value
            if ($s -match $kw) { [void]$hits.Add($s.Trim()) }
        }
    }
    $sorted = $hits | Sort-Object
    Write-Output ("  matches: {0}" -f $sorted.Count)
    $sorted | Select-Object -First 120 | ForEach-Object { Write-Output ("    " + $_) }
}
