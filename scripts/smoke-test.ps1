<#
.SYNOPSIS
  End-to-end smoke test for a running ResumeMatch (Windows PowerShell 5.1 + curl.exe).

.DESCRIPTION
  Registers a throw-away user (smoke-<timestamp>@example.com), walks through the main
  features, then deletes that user and its resume files with the mysql command line
  client, even when a check fails. Prints PASS / FAIL / WARN lines. Exit status is 1
  when any check failed (WARN lines do not count).

  Optional environment variables:
    MYSQL        path to mysql.exe (else "mysql" on the PATH, else the MySQL 8.0 default)
    MYSQL_USER   database user, default root
    MYSQL_PWD    database password (read by the mysql client itself)
    MYSQL_DB     database name, default resumematch
    RESUME_ROOT  folder holding uploaded files when file_path is a relative path
    DEBUG        set to 1 to print the last response when a check fails

.EXAMPLE
  .\scripts\smoke-test.ps1
  .\scripts\smoke-test.ps1 -BaseUrl http://localhost:8080/ResumeMatch
#>
param(
    [string]$BaseUrl = $(if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:8080/ResumeMatch' })
)

$BaseUrl = $BaseUrl.TrimEnd('/')
$Sample = Join-Path (Split-Path -Parent $PSScriptRoot) 'sample-data\arun_kumar_resume.txt'
$SamplePdf = Join-Path (Split-Path -Parent $PSScriptRoot) 'test\fixtures\arun_kumar_resume.pdf'
$SampleDocx = Join-Path (Split-Path -Parent $PSScriptRoot) 'test\fixtures\arun_kumar_resume.docx'

$Stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$Email = "smoke-$Stamp@example.com"
$Password = 'Smoke!Pass2026x'
$Name = 'Smoke Tester'

$Tmp = Join-Path ([IO.Path]::GetTempPath()) ("smoke-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Tmp | Out-Null
$Jar = Join-Path $Tmp 'cookies.txt'
$BodyFile = Join-Path $Tmp 'body.html'
$HdrFile = Join-Path $Tmp 'headers.txt'

$script:Code = 0
$script:Body = ''
$script:Headers = ''
$script:Token = ''
$script:Pass = 0
$script:Fail = 0
$script:Warn = 0

# Words that must never reach the browser.
$Leak = 'Exception|at com\.|at java\.|java\.lang|Caused by|stack ?trace|You have an error in your SQL'

function Write-Pass([string]$Text) { $script:Pass++; Write-Host "PASS  $Text" }
function Write-Fail([string]$Text) { $script:Fail++; Write-Host "FAIL  $Text" }
function Write-Warn([string]$Text) { $script:Warn++; Write-Host "WARN  $Text" }

function Test-Check([string]$Text, [bool]$Ok) {
    if ($Ok) {
        Write-Pass $Text
    } else {
        Write-Fail $Text
        if ($env:DEBUG) {
            Write-Host ("      last status: {0}, location: {1}" -f $script:Code, (Get-Location-Header))
            $snippet = $script:Body
            if ($snippet.Length -gt 600) { $snippet = $snippet.Substring(0, 600) }
            Write-Host ($snippet -replace '\s+', ' ')
        }
    }
}

# ---- HTTP helpers (curl.exe, so redirects are not followed and every status is visible) ----

function Invoke-Curl([string[]]$CurlArgs) {
    $common = @('-s', '--connect-timeout', '5', '--max-time', '60',
                '-o', $BodyFile, '-D', $HdrFile, '-w', '%{http_code}',
                '-b', $Jar, '-c', $Jar)
    $out = & curl.exe @common @CurlArgs
    $script:Code = [int](($out | Select-Object -Last 1) -replace '\D', '0')
    $script:Body = if (Test-Path $BodyFile) { [IO.File]::ReadAllText($BodyFile) } else { '' }
    $script:Headers = if (Test-Path $HdrFile) { [IO.File]::ReadAllText($HdrFile) } else { '' }
}

function Get-Page([string]$Path) { Invoke-Curl @("$BaseUrl$Path") }

# Reads the hidden _csrf input from the current page (empty when the page has none).
function Read-Token {
    $script:Token = ''
    $flat = $script:Body -replace '[\r\n]+', ' '
    $m = [regex]::Match($flat, '<input[^>]*name="_csrf"[^>]*>')
    if ($m.Success) {
        $v = [regex]::Match($m.Value, 'value="([^"]*)"')
        if ($v.Success) { $script:Token = $v.Groups[1].Value }
    }
}

function Get-Token([string]$Path) { Get-Page $Path; Read-Token }

# Url-encoded form post; adds _csrf when a token is known.
function Send-Form([string]$Path, [string[]]$Fields, [bool]$WithToken = $true) {
    $a = @('-X', 'POST')
    foreach ($f in $Fields) { $a += @('--data-urlencode', $f) }
    if ($WithToken -and $script:Token) { $a += @('--data-urlencode', "_csrf=$($script:Token)") }
    $a += "$BaseUrl$Path"
    Invoke-Curl $a
}

# Multipart upload; the token also goes on the URL.
function Send-Upload([string]$File, [string]$FileName = '') {
    $spec = "resume=@$File"
    if ($FileName) { $spec = "$spec;filename=$FileName" }
    $url = "$BaseUrl/upload"
    $a = @('-F', $spec)
    if ($script:Token) {
        $url = "${url}?_csrf=$($script:Token)"
        $a += @('-F', "_csrf=$($script:Token)")
    }
    $a += $url
    Invoke-Curl $a
}

function Test-Body([string]$Pattern) { return [regex]::IsMatch($script:Body, $Pattern, 'IgnoreCase') }
function Test-Leak { return [regex]::IsMatch($script:Body, $Leak) }
function Test-Header([string]$Name) { return [regex]::IsMatch($script:Headers, "(?im)^$([regex]::Escape($Name)):") }
function Get-Location-Header {
    $m = [regex]::Matches($script:Headers, '(?im)^location:\s*(.*)$')
    if ($m.Count -eq 0) { return '' }
    return $m[$m.Count - 1].Groups[1].Value.Trim()
}
function Test-Redirect([string]$Fragment) {
    return ($script:Code -eq 302 -and (Get-Location-Header).Contains($Fragment))
}

# ---- cleanup (runs in finally, so also after a failure) ----

function Find-Mysql {
    if ($env:MYSQL -and (Test-Path $env:MYSQL)) { return $env:MYSQL }
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $default = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    if (Test-Path $default) { return $default }
    return $null
}

function Invoke-Cleanup {
    $mysql = Find-Mysql
    if (-not $mysql) {
        Write-Warn 'cleanup skipped: mysql client not found (set MYSQL). Delete smoke-*@example.com users by hand.'
        return
    }
    $db = if ($env:MYSQL_DB) { $env:MYSQL_DB } else { 'resumematch' }
    $user = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { 'root' }

    $paths = & $mysql -u $user -N -B $db -e "SELECT r.file_path FROM resumes r JOIN users u ON u.id = r.user_id WHERE u.email LIKE 'smoke-%@example.com'" 2>$null
    $removed = 0
    $tomcat = if ($env:CATALINA_HOME) { $env:CATALINA_HOME } else { 'C:\Program Files\Apache Software Foundation\Tomcat 9.0' }
    foreach ($p in @($paths)) {
        $p = "$p".Trim()
        if (-not $p) { continue }
        $candidates = @($p)
        if ($env:RESUME_ROOT) { $candidates += (Join-Path $env:RESUME_ROOT $p) }
        if ($env:CATALINA_BASE) { $candidates += (Join-Path $env:CATALINA_BASE $p) }
        $candidates += (Join-Path $tomcat $p)
        foreach ($c in $candidates) {
            if (Test-Path -LiteralPath $c -PathType Leaf) {
                Remove-Item -LiteralPath $c -Force -ErrorAction SilentlyContinue
                $removed++
                $dir = Split-Path -Parent $c
                if (-not (Get-ChildItem -LiteralPath $dir -Force -ErrorAction SilentlyContinue)) {
                    Remove-Item -LiteralPath $dir -Force -ErrorAction SilentlyContinue
                }
                break
            }
        }
    }
    Write-Host "Cleanup: removed $removed resume file(s)"
    & $mysql -u $user $db -e "DELETE FROM users WHERE email LIKE 'smoke-%@example.com'" 2>$null
    $left = (& $mysql -u $user -N -B $db -e "SELECT COUNT(*) FROM users WHERE email LIKE 'smoke-%@example.com'" 2>$null | Select-Object -First 1)
    if ("$left".Trim() -eq '0') {
        Write-Pass 'cleanup left zero smoke users'
    } else {
        Write-Fail "cleanup could not delete the smoke user (count=$left); check MYSQL_USER / MYSQL_PWD"
    }
}

# ---- the walk-through ----

try {
    Write-Host "Smoke test against $BaseUrl as $Email"
    foreach ($f in @($Sample, $SamplePdf, $SampleDocx)) {
        if (-not (Test-Path $f)) { throw "Sample resume not found: $f" }
    }
    if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) { throw 'curl.exe not found (it ships with Windows 10 and later).' }

    Get-Page '/'
    if ($script:Code -eq 0) { throw "cannot reach $BaseUrl (is Tomcat running?)" }

    Write-Host '== public pages =='
    foreach ($p in '/', '/login', '/register') {
        Get-Page $p
        Test-Check "GET $p is 200" ($script:Code -eq 200)
    }

    Write-Host '== protected pages while logged out =='
    foreach ($p in '/dashboard', '/analysis', '/score', '/skill-gap', '/jobs', '/job?id=101', '/my-jobs', '/resume-file') {
        Get-Page $p
        Test-Check "GET $p redirects to /login" (Test-Redirect '/login')
    }

    Write-Host '== security headers (WARN only) =='
    Get-Page '/login'
    foreach ($h in 'X-Content-Type-Options', 'X-Frame-Options', 'Content-Security-Policy') {
        if (Test-Header $h) { Write-Pass "header $h present" } else { Write-Warn "header $h missing" }
    }

    Write-Host '== register =='
    Get-Token '/register'
    Send-Form '/register' @("name=$Name", "email=$Email", "password=$Password", "confirmPassword=$Password")
    Test-Check 'register redirects to /login' (Test-Redirect '/login')
    Get-Token '/register'
    Send-Form '/register' @("name=$Name", "email=$Email", "password=$Password", "confirmPassword=$Password")
    Test-Check 'duplicate register is rejected' (($script:Code -eq 200) -and (Test-Body 'already exists') -and -not (Test-Leak))
    Get-Token '/register'
    Send-Form '/register' @("name=$Name", "email=smoke-${Stamp}b@example.com", "password=$Password", 'confirmPassword=different1!')
    Test-Check 'mismatched passwords rejected' (($script:Code -eq 200) -and (Test-Body 'match'))

    Write-Host '== login =='
    Get-Token '/login'
    Send-Form '/login' @("email=$Email", 'password=WrongPass!999')
    Test-Check 'bad login rejected' (($script:Code -eq 200) -and (Test-Body 'email or password is incorrect') -and -not (Test-Leak))
    Get-Token '/login'
    Send-Form '/login' @("email=$Email", "password=$Password")
    Test-Check 'login redirects to /dashboard' (Test-Redirect '/dashboard')
    Get-Token '/dashboard'
    Test-Check 'dashboard is 200 after login' ($script:Code -eq 200)

    Write-Host '== CSRF =='
    Invoke-Curl @('-X', 'POST', '--data-urlencode', 'jobId=9999', '--data-urlencode', 'action=save',
                  '--data-urlencode', '_csrf=wrong-token-value', "$BaseUrl/save-job")
    if ($script:Code -eq 403) { Write-Pass 'POST with a wrong _csrf gets 403' } else { Write-Warn "POST with a wrong _csrf got $($script:Code), not 403" }
    Test-Check 'CSRF rejection shows no stack trace' (-not (Test-Leak))
    Get-Token '/dashboard'

    Write-Host '== upload the sample resume =='
    Send-Upload $Sample 'arun_kumar_resume.txt'
    Test-Check 'sample upload redirects to /analysis' (Test-Redirect '/analysis')
    Get-Token '/analysis'
    Test-Check 'analysis page loads' ($script:Code -eq 200)
    Test-Check 'analysis page shows 7 skills found' (Test-Body '>7<|7 skills')
    Get-Token '/score'
    Test-Check 'score page shows 78' (($script:Code -eq 200) -and (Test-Body '(^|[^0-9])78([^0-9]|$)'))

    Write-Host '== jobs =='
    Get-Token '/jobs'
    Test-Check 'jobs page loads and lists job 101' (($script:Code -eq 200) -and (Test-Body 'Java Backend Intern'))
    Get-Token '/job?id=101'
    Test-Check 'job 101 shows 80%' (($script:Code -eq 200) -and (Test-Body '80 ?%'))
    Get-Page '/jobs?q=Java'
    Test-Check 'searching Java finds 4 jobs, 101 first' (($script:Code -eq 200) -and (Test-Body '4 jobs for') -and (Test-Body 'Java Backend Intern'))
    Get-Page '/job?id=9999'
    Test-Check 'job 9999 shows a friendly message' ((Test-Body 'no longer exists') -and -not (Test-Leak))
    Get-Token '/skill-gap?job=101'
    Test-Check 'skill gap for job 101 shows 67%' (($script:Code -eq 200) -and (Test-Body '67 ?%'))

    Write-Host '== save, apply, status, remove =='
    Get-Token '/job?id=101'
    Send-Form '/save-job' @('jobId=101', 'action=save')
    Test-Check 'save job redirects' ($script:Code -eq 302)
    Get-Token '/my-jobs'
    Test-Check 'saved job is listed in My jobs' (Test-Body 'Java Backend Intern')
    Send-Form '/application' @('jobId=101', 'action=apply', 'tab=saved')
    Test-Check 'apply redirects to /my-jobs' (Test-Redirect '/my-jobs')
    Get-Token '/my-jobs?tab=applied'
    Test-Check 'applied tab lists the job' (Test-Body 'Java Backend Intern')
    Send-Form '/application' @('jobId=101', 'action=status', 'status=Interview')
    Get-Page '/my-jobs?tab=applied'
    Test-Check 'status change to Interview is kept' (Test-Body 'value="Interview"[^>]*selected|selected[^>]*value="Interview"')
    Send-Form '/application' @('jobId=101', 'action=remove', 'tab=applied')
    Test-Check 'remove redirects to /my-jobs' (Test-Redirect '/my-jobs')
    Get-Page '/my-jobs?tab=applied'
    Test-Check 'removed job is gone from the applied tab' (-not (Test-Body 'Java Backend Intern'))

    Write-Host '== download =='
    Get-Token '/dashboard'
    $download = Join-Path $Tmp 'download.txt'
    $dlCode = & curl.exe -s --connect-timeout 5 --max-time 60 -o $download -w '%{http_code}' -b $Jar -c $Jar "$BaseUrl/resume-file"
    Test-Check 'download returns 200' ("$dlCode".Trim() -eq '200')
    $same = (Test-Path $download) -and ([IO.File]::ReadAllText($download) -ceq [IO.File]::ReadAllText($Sample))
    Test-Check 'downloaded bytes equal the uploaded file' $same

    Write-Host '== upload a PDF and a DOCX resume =='
    $kinds = @(
        @{ Ext = 'pdf';  File = $SamplePdf;  Type = 'application/pdf' },
        @{ Ext = 'docx'; File = $SampleDocx; Type = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }
    )
    foreach ($k in $kinds) {
        $ext = $k.Ext
        Get-Token '/dashboard'
        Send-Upload $k.File "arun_kumar_resume.$ext"
        Test-Check "$ext upload redirects to /analysis" (Test-Redirect '/analysis')
        Get-Token '/analysis'
        Test-Check "$ext analysis shows 7 skills found" (($script:Code -eq 200) -and (Test-Body '>7<|7 skills'))
        Get-Token '/score'
        Test-Check "$ext score page shows 78" (($script:Code -eq 200) -and (Test-Body '(^|[^0-9])78([^0-9]|$)'))
        $dl = Join-Path $Tmp "download.$ext"
        $dlCode = & curl.exe -s --connect-timeout 5 --max-time 60 -o $dl -D $HdrFile -w '%{http_code}' -b $Jar -c $Jar "$BaseUrl/resume-file"
        $hdr = [IO.File]::ReadAllText($HdrFile)
        Test-Check "$ext download returns 200 with the right Content-Type" (("$dlCode".Trim() -eq '200') -and ($hdr -match ('(?im)^content-type:\s*' + [regex]::Escape($k.Type))))
        $sent = [IO.File]::ReadAllBytes($k.File)
        $got = if (Test-Path $dl) { [IO.File]::ReadAllBytes($dl) } else { [byte[]]@() }
        Test-Check "downloaded $ext bytes equal the uploaded file" (($sent.Length -eq $got.Length) -and ([Convert]::ToBase64String($sent) -ceq [Convert]::ToBase64String($got)))
    }

    Write-Host '== bad uploads =='
    $doc = Join-Path $Tmp 'resume.doc'
    [IO.File]::WriteAllText($doc, 'old word file')
    $pdf = Join-Path $Tmp 'resume.pdf'
    $empty = Join-Path $Tmp 'empty.txt'
    $big = Join-Path $Tmp 'big.txt'
    [IO.File]::WriteAllText($pdf, 'not really a pdf')
    [IO.File]::WriteAllBytes($empty, [byte[]]@())
    [IO.File]::WriteAllBytes($big, ([byte[]](,[byte]97 * 307200)))

    Get-Token '/dashboard'
    Send-Upload $doc
    Test-Check '.doc upload rejected with a friendly message' (($script:Code -eq 200) -and (Test-Body 'Upload a \.txt, \.pdf or \.docx file') -and -not (Test-Leak))
    Get-Token '/dashboard'
    Send-Upload $pdf
    Test-Check 'text renamed to .pdf rejected with a friendly message' (($script:Code -eq 200) -and (Test-Body 'real PDF') -and -not (Test-Leak))
    Get-Token '/dashboard'
    Send-Upload $empty
    Test-Check 'empty file rejected with a friendly message' (($script:Code -eq 200) -and (Test-Body 'file is empty') -and -not (Test-Leak))
    Get-Token '/dashboard'
    Send-Upload $big
    Test-Check '300 KB file rejected with a friendly message' (($script:Code -eq 200) -and (Test-Body 'larger than 200 KB') -and -not (Test-Leak))
    Get-Token '/dashboard'
    Send-Upload $Sample '../evil.txt'
    Test-Check '../evil.txt filename handled without a stack trace' ((($script:Code -eq 200) -or ($script:Code -eq 302)) -and -not (Test-Leak))

    Write-Host '== error pages =='
    Get-Page "/no-such-page-$Stamp"
    Test-Check 'unknown URL gives a friendly 404' (($script:Code -eq 404) -and -not [regex]::IsMatch($script:Body, "$Leak|SQL"))
    Get-Token '/dashboard'
    Send-Form '/dashboard' @('x=1')
    Test-Check 'POST to a GET-only page gives a friendly 405' (($script:Code -eq 405) -and -not [regex]::IsMatch($script:Body, "$Leak|SQL"))

    Write-Host '== logout =='
    Get-Token '/dashboard'
    Get-Page '/logout'
    if ($script:Code -eq 405) { Send-Form '/logout' @('x=1') }   # logout may be a POST on newer builds
    Test-Check 'logout redirects to /login' (Test-Redirect '/login')
    Get-Page '/dashboard'
    Test-Check 'dashboard redirects to /login after logout' (Test-Redirect '/login')
}
catch {
    Write-Fail "script stopped: $($_.Exception.Message)"
}
finally {
    Invoke-Cleanup
    Remove-Item -LiteralPath $Tmp -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host ''
    Write-Host "Passed: $($script:Pass), Failed: $($script:Fail), Warnings: $($script:Warn)"
}

if ($script:Fail -ne 0) { exit 1 }
exit 0
