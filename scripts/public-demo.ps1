<#
.SYNOPSIS
  Share the locally running ResumeMatch on a temporary public https address (Windows PowerShell 5.1).

.DESCRIPTION
  Starts a free Cloudflare quick tunnel to Tomcat, prints the public address and the demo login,
  and keeps running until you press Ctrl+C. The address changes every run and stops working when
  the tunnel ends.

  While it runs, the script also creates the flag file <tomcat>\resumematch-demo-mode.flag, which makes
  the app pause new sign-ups (the shared demo account still works). The file is removed when the script
  ends. The Tomcat folder is %CATALINA_BASE%, else %CATALINA_HOME%, else the Windows default.

  Optional environment variables:
    LOCAL_URL  where the app answers (default http://localhost:8080)
    PROTOCOL   tunnel protocol: http2 (default, works on most networks) or quic

.EXAMPLE
  .\scripts\public-demo.ps1
#>
$ErrorActionPreference = 'Stop'
$local = if ($env:LOCAL_URL) { $env:LOCAL_URL } else { 'http://localhost:8080' }
$protocol = if ($env:PROTOCOL) { $env:PROTOCOL } else { 'http2' }

$cf = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cf) {
    foreach ($p in "C:\Program Files (x86)\cloudflared\cloudflared.exe", "C:\Program Files\cloudflared\cloudflared.exe") {
        if (Test-Path $p) { $cf = $p; break }
    }
}
if (-not $cf) {
    Write-Host 'cloudflared is not installed. Install it first:'
    Write-Host '  winget install --id Cloudflare.cloudflared -e'
    exit 1
}

try {
    Invoke-WebRequest -Uri "$local/ResumeMatch/login" -UseBasicParsing -TimeoutSec 10 | Out-Null
} catch {
    Write-Host "The app does not answer at $local/ResumeMatch/login."
    Write-Host 'Start Tomcat and MySQL, deploy ResumeMatch (build.bat deploy), then run this again.'
    exit 1
}

$tomcat = if ($env:CATALINA_BASE) { $env:CATALINA_BASE } elseif ($env:CATALINA_HOME) { $env:CATALINA_HOME } else { 'C:\Program Files\Apache Software Foundation\Tomcat 9.0' }
$flag = Join-Path $tomcat 'resumematch-demo-mode.flag'
try {
    New-Item -ItemType File -Path $flag -Force -ErrorAction Stop | Out-Null
} catch {
    Write-Host "Cannot write $flag. Set CATALINA_BASE to your Tomcat folder and make sure you may write there."
    exit 1
}

$log = Join-Path $env:TEMP ("public-demo-{0}.log" -f [guid]::NewGuid())
$proc = $null
try {
    Write-Host 'Starting the tunnel (this takes a few seconds)...'
    $proc = Start-Process -FilePath $cf -ArgumentList @('tunnel', '--url', $local, '--protocol', $protocol, '--no-autoupdate') `
        -RedirectStandardError $log -RedirectStandardOutput "$log.out" -NoNewWindow -PassThru

    $url = $null
    $connected = $false
    for ($i = 0; $i -lt 100 -and -not $proc.HasExited; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Path $log) {
            $text = Get-Content $log -Raw -ErrorAction SilentlyContinue
            if (-not $url -and $text -match 'https://[a-z0-9-]+\.trycloudflare\.com') { $url = $Matches[0] }
            if ($text -match 'Registered tunnel connection') { $connected = $true; break }
        }
    }
    if (-not $url -or -not $connected) {
        Write-Host 'The tunnel could not connect to Cloudflare. Outbound port 7844 (TCP and UDP) may be blocked'
        Write-Host 'on this network; try another network or a phone hotspot. Last lines of its log:'
        if (Test-Path $log) { Get-Content $log -Tail 6 }
        exit 1
    }

    Write-Host ''
    Write-Host 'ResumeMatch is live:'
    Write-Host "  $url/ResumeMatch/"
    Write-Host 'Demo login: demo@resumematch.in / demo1234'
    Write-Host ''
    Write-Host 'Live-demo mode is ON: new sign-ups are paused. Everyone shares the demo account, so change its'
    Write-Host 'password or reset its data afterwards (see DEPLOY.md).'
    Write-Host ''
    Write-Host 'The address changes every run and stops when this window closes. Press Ctrl+C to stop.'
    $proc.WaitForExit()
} finally {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    Remove-Item $log, "$log.out", $flag -ErrorAction SilentlyContinue
    Write-Host ''
    Write-Host 'Tunnel stopped. The public address no longer works.'
}
