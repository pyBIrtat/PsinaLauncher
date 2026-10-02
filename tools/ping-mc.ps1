# Minecraft Server List Ping (protocol 769 / 1.21.4) — проверка, что сервер реально
# отвечает по сети, а не просто «слушает порт». Usage: ping-mc.ps1 <ip> <port>
param([string]$ip = '192.168.0.173', [int]$port = 25682)

$tcp = New-Object System.Net.Sockets.TcpClient
$tcp.ReceiveTimeout = 5000
$tcp.SendTimeout = 5000
try { $tcp.Connect($ip, $port) } catch { Write-Output "CONNECT FAILED: $($_.Exception.Message)"; exit 2 }
$s = $tcp.GetStream()

$addr = [System.Text.Encoding]::ASCII.GetBytes($ip)
# handshake: len=0x14, id=0x00, proto=769 (0x81 0x06), addrLen, addr, port BE, nextState=1
$hs = [byte[]](0x14, 0x00, 0x81, 0x06, 0x0D) + $addr + [byte[]](0x64, 0x52, 0x01)
$s.Write($hs, 0, $hs.Length)
# status request
$s.Write([byte[]](0x01, 0x00), 0, 2)
$s.Flush()

Start-Sleep -Milliseconds 700
$buf = New-Object byte[] 16384
$n = 0
try { $n = $s.Read($buf, 0, $buf.Length) } catch { Write-Output "READ FAILED: $($_.Exception.Message)"; exit 3 }
$tcp.Close()

if ($n -le 0) { Write-Output 'EMPTY RESPONSE (сервер принял TCP, но не ответил на ping)'; exit 4 }
$txt = [System.Text.Encoding]::UTF8.GetString($buf, 0, $n)
$i = $txt.IndexOf('{')
if ($i -lt 0) { Write-Output "UNEXPECTED RESPONSE: $txt"; exit 5 }
$json = $txt.Substring($i).TrimEnd([char]0)
Write-Output "PING OK from ${ip}:${port}"
Write-Output $json
