<#
.SYNOPSIS
  One-command demo setup for ResumeMatch (Windows PowerShell 5.1).

.DESCRIPTION
  Checks the tools, creates src\main\resources\db.properties, optionally loads the
  database, runs the unit tests, builds and deploys the war, then waits until the
  site answers. Tomcat and MySQL are never started, stopped or restarted.

  -ResetDb  also (re)loads database\01_schema.sql and 02_seed_data.sql.
            WARNING: this deletes every account, resume and job record.

  Optional environment variables:
    CATALINA_HOME  Tomcat folder (default: C:\Program Files\Apache Software Foundation\Tomcat 9.0)
    MYSQL          path to mysql.exe (else "mysql" on the PATH, else the MySQL 8.0 default)
    BASE_URL       site address to wait for (default: http://localhost:8080/ResumeMatch)
    WAIT_SECONDS   how long to wait for the site (default 120)

.EXAMPLE
  .\scripts\demo-setup.ps1
  .\scripts\demo-setup.ps1 -ResetDb
#>
param([switch]$ResetDb)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$Props = Join-Path $Root 'src\main\resources\db.properties'
$BaseUrl = if ($env:BASE_URL) { $env:BASE_URL.TrimEnd('/') } else { 'http://localhost:8080/ResumeMatch' }
$Wait = if ($env:WAIT_SECONDS) { [int]$env:WAIT_SECONDS } else { 120 }
if (-not $env:CATALINA_HOME) { $env:CATALINA_HOME = 'C:\Program Files\Apache Software Foundation\Tomcat 9.0' }

function Step($t) { Write-Host ''; Write-Host "== $t" }
function Ok($t) { Write-Host "   ok: $t" }
function Fail($t) { Write-Host "   ERROR: $t" -ForegroundColor Red; exit 1 }

Step '1/7 Checking the tools'
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fail 'java not found. Install JDK 17 or newer and put it on the PATH.' }
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { Fail 'javac not found. You need a JDK (17 or newer), not only a JRE.' }
$jv = (& cmd /c 'javac -version 2>&1') | Select-Object -First 1
if ("$jv" -notmatch 'javac (\d+)' -or [int]$Matches[1] -lt 17) { Fail "JDK 17 or newer is required (found: $jv)." }
Ok "javac $($Matches[1])"

$mysql = $env:MYSQL
if (-not $mysql) {
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($cmd) { $mysql = $cmd.Source }
    elseif (Test-Path 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe') { $mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
    else { Fail 'mysql client not found. Install MySQL 8 or set MYSQL to the client path.' }
}
Ok "mysql client: $mysql"

if (-not (Test-Path 'lib\servlet-api.jar')) { Fail 'lib\servlet-api.jar is missing. Copy it from <tomcat>\lib\ (see README, Quick start step 3).' }
if (-not (Get-ChildItem 'lib\mysql-connector-j*.jar' -ErrorAction SilentlyContinue)) { Fail 'lib\mysql-connector-j-<version>.jar is missing (see README, Quick start step 3).' }
Ok 'lib\ jars present'

if (-not (Test-Path (Join-Path $env:CATALINA_HOME 'webapps'))) { Fail "Tomcat not found at: $($env:CATALINA_HOME) (set CATALINA_HOME to your Tomcat 9 folder)." }
Ok "Tomcat folder: $($env:CATALINA_HOME)"

Step '2/7 Connection settings'
if (Test-Path $Props) {
    Ok 'db.properties already exists, keeping it'
} else {
    $example = "$Props.example"
    if (-not (Test-Path $example)) { Fail 'db.properties.example is missing.' }
    $u = Read-Host '   MySQL user [root]'
    if (-not $u) { $u = 'root' }
    $sec = Read-Host '   MySQL password (not shown)' -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    $p = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    $lines = Get-Content $example | ForEach-Object {
        if ($_ -like 'db.user=*') { "db.user=$u" }
        elseif ($_ -like 'db.password=*') { 'db.password=' + $p.Replace('\', '\\') }
        else { $_ }
    }
    [IO.File]::WriteAllLines($Props, [string[]]$lines, (New-Object Text.UTF8Encoding($false)))
    $p = $null
    Ok 'created src\main\resources\db.properties'
}

function Prop($name) {
    $m = Get-Content $Props | Where-Object { $_ -like "$name=*" } | Select-Object -Last 1
    if ($m) { return $m.Substring($name.Length + 1).Trim() } else { return '' }
}
$dbUser = Prop 'db.user'
$env:MYSQL_PWD = Prop 'db.password'

Step '3/7 Database'
if ($ResetDb) {
    Write-Host '   WARNING: -ResetDb drops and recreates the resumematch database.'
    Write-Host '   Every account, resume and saved job will be deleted.'
    $answer = Read-Host '   Type yes to continue'
    if ($answer -ne 'yes') { Fail 'Cancelled, nothing was changed.' }
    foreach ($f in 'database\01_schema.sql', 'database\02_seed_data.sql') {
        # cmd handles the "<" redirect the same way for every mysql client build
        & cmd /c "`"$mysql`" -u $dbUser < $f"
        if ($LASTEXITCODE -ne 0) { Fail "Loading $f failed (check db.user and db.password)." }
    }
    Ok 'schema and seed data loaded'
} else {
    Write-Host '   Not reloading the database (pass -ResetDb to load the schema and seed data).'
    $jobs = & $mysql -u $dbUser -N -B -e 'SELECT COUNT(*) FROM resumematch.jobs' 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $jobs) {
        Write-Host '   WARNING: could not read resumematch.jobs. On a fresh machine run again with -ResetDb.'
    } else {
        Ok "database reachable, $jobs jobs found"
    }
}
Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue

Step '4/7 Unit tests, build and deploy'
$build = Join-Path $Root 'build.bat'
& cmd /c "`"$build`" test deploy"
if ($LASTEXITCODE -ne 0) { Fail 'Tests or build failed; see the output above.' }

Step "5/7 Waiting for $BaseUrl/login (up to ${Wait}s)"
$up = $false
$deadline = (Get-Date).AddSeconds($Wait)
while ((Get-Date) -lt $deadline) {
    $code = & curl.exe -s -o NUL -w '%{http_code}' --max-time 5 "$BaseUrl/login" 2>$null
    if ($code -eq '200') { $up = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $up) { Fail "The site did not answer within ${Wait}s. Is Tomcat running? See README, Troubleshooting." }
Ok 'site is up'

Step '6/7 Ready'
Write-Host "   Open:   $BaseUrl/"
Write-Host '   Login:  demo@resumematch.in / demo1234'

Step '7/7 First demo steps'
Write-Host '   1. Log in with the demo account above.'
Write-Host '   2. On the dashboard, upload sample-data\arun_kumar_resume.txt.'
Write-Host '   3. Open Analysis (7 skills), then Score (78/100), then Jobs (search "Java": 80, 75, 40, 33).'
Write-Host '   The full guided tour with screenshots is in DEMO.md.'
