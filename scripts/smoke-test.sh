#!/bin/bash
# End-to-end smoke test for a running ResumeMatch (needs bash and curl).
#
#   scripts/smoke-test.sh [BASE_URL]
#
# BASE_URL defaults to http://localhost:8080/ResumeMatch (or the BASE_URL env var).
# It registers a throw-away user (smoke-<timestamp>@example.com), walks through the
# main features, then deletes that user and its resume files with the mysql command
# line client, even when a check fails.
#
# Optional environment variables:
#   MYSQL        path to the mysql client (else "mysql" on the PATH, else the MySQL 8.0 default)
#   MYSQL_USER   database user, default root
#   MYSQL_PWD    database password (read by the mysql client itself)
#   MYSQL_DB     database name, default resumematch
#   RESUME_ROOT  folder that holds the uploaded files when file_path is a relative path
#   DEBUG        set to 1 to print the last response when a check fails
#
# Exit status: 0 when no check failed (WARN lines do not count), 1 otherwise.

BASE_URL="${1:-${BASE_URL:-http://localhost:8080/ResumeMatch}}"
BASE_URL="${BASE_URL%/}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SAMPLE="$SCRIPT_DIR/../sample-data/arun_kumar_resume.txt"
SAMPLE_PDF="$SCRIPT_DIR/../test/fixtures/arun_kumar_resume.pdf"
SAMPLE_DOCX="$SCRIPT_DIR/../test/fixtures/arun_kumar_resume.docx"

STAMP="$(date +%s)"
EMAIL="smoke-${STAMP}@example.com"
PASSWORD='Smoke!Pass2026x'
NAME="Smoke Tester"

TMP="$(mktemp -d)"
JAR="$TMP/cookies.txt"
BODY="$TMP/body.html"
HDRS="$TMP/headers.txt"
CODE=""
TOKEN=""
PASS=0
FAIL=0
WARN=0

# Words that must never reach the browser.
LEAK='Exception|at com\.|at java\.|java\.lang|Caused by|stack ?trace|You have an error in your SQL'

pass() { PASS=$((PASS + 1)); echo "PASS  $1"; }
fail() { FAIL=$((FAIL + 1)); echo "FAIL  $1"; }
warn() { WARN=$((WARN + 1)); echo "WARN  $1"; }
check() {
  if [ "$2" = "1" ]; then
    pass "$1"
  else
    fail "$1"
    # DEBUG=1 shows what the last response looked like
    if [ -n "$DEBUG" ]; then echo "      last status: $CODE, location: $(location)"; head -c 600 "$BODY" | tr '\n' ' '; echo; fi
  fi
}

# ---- HTTP helpers (no redirects are followed, so every status is visible) ----

# get PATH  -> sets CODE, BODY, HDRS
get() {
  CODE=$(curl -s --connect-timeout 5 --max-time 60 -o "$BODY" -D "$HDRS" -w '%{http_code}' -b "$JAR" -c "$JAR" "$BASE_URL$1")
}

# post PATH field=value ...  -> url-encoded form post; adds _csrf when a token is known
post() {
  local path="$1"; shift
  local args=()
  local f
  for f in "$@"; do args+=(--data-urlencode "$f"); done
  if [ -n "$TOKEN" ]; then args+=(--data-urlencode "_csrf=$TOKEN"); fi
  CODE=$(curl -s --connect-timeout 5 --max-time 60 -o "$BODY" -D "$HDRS" -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST "${args[@]}" "$BASE_URL$path")
}

# upload FILE [NAME]  -> multipart post; the token also goes on the URL
upload() {
  local file="$1" name="${2:-}"
  local url="$BASE_URL/upload"
  # on Windows (Git Bash) curl needs a Windows-style path after "@"
  if command -v cygpath >/dev/null 2>&1; then file="$(cygpath -m "$file")"; fi
  local spec="resume=@$file"
  if [ -n "$name" ]; then spec="$spec;filename=$name"; fi
  if [ -n "$TOKEN" ]; then
    url="$url?_csrf=$TOKEN"
    CODE=$(curl -s --connect-timeout 5 --max-time 60 -o "$BODY" -D "$HDRS" -w '%{http_code}' -b "$JAR" -c "$JAR" -F "$spec" -F "_csrf=$TOKEN" "$url")
  else
    CODE=$(curl -s --connect-timeout 5 --max-time 60 -o "$BODY" -D "$HDRS" -w '%{http_code}' -b "$JAR" -c "$JAR" -F "$spec" "$url")
  fi
}

# Reads the hidden _csrf input from the page in BODY (empty when the page has none).
read_token() {
  TOKEN=$(tr '\n\r' '  ' < "$BODY" | grep -o '<input[^>]*name="_csrf"[^>]*>' | head -1 \
          | sed -n 's/.*value="\([^"]*\)".*/\1/p')
}

# get a page and remember its token
get_token() { get "$1"; read_token; }

body_has() { grep -Eqi -- "$1" "$BODY"; }
header_has() { grep -Eqi -- "^$1:" "$HDRS"; }
location() { grep -i '^location:' "$HDRS" | tail -1 | tr -d '\r' | sed 's/^[Ll]ocation: *//'; }
no_leak() { ! grep -Eq -- "$LEAK" "$BODY"; }

# ---- cleanup (runs on every exit) ----

find_mysql() {
  if [ -n "$MYSQL" ] && [ -x "$MYSQL" ]; then echo "$MYSQL"; return; fi
  if command -v mysql >/dev/null 2>&1; then command -v mysql; return; fi
  local d="C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
  if [ -x "$d" ]; then echo "$d"; fi
}

cleanup() {
  local status=$?
  local mysql_bin; mysql_bin="$(find_mysql)"
  if [ -z "$mysql_bin" ]; then
    warn "cleanup skipped: mysql client not found (set MYSQL). Delete smoke-*@example.com users by hand."
  else
    local db="${MYSQL_DB:-resumematch}" user="${MYSQL_USER:-root}"
    local paths
    paths=$("$mysql_bin" -u "$user" -N -B "$db" -e \
      "SELECT r.file_path FROM resumes r JOIN users u ON u.id = r.user_id WHERE u.email LIKE 'smoke-%@example.com'" 2>/dev/null)
    local p removed=0
    while IFS= read -r p; do
      p="${p%$'\r'}"
      [ -z "$p" ] && continue
      local candidate
      for candidate in "$p" "${RESUME_ROOT:+$RESUME_ROOT/}$p" "${CATALINA_BASE:+$CATALINA_BASE/}$p" \
                       "${CATALINA_HOME:-C:/Program Files/Apache Software Foundation/Tomcat 9.0}/$p"; do
        if [ -f "$candidate" ]; then
          rm -f "$candidate" && removed=$((removed + 1))
          rmdir "$(dirname "$candidate")" 2>/dev/null
          break
        fi
      done
    done <<< "$paths"
    echo "Cleanup: removed $removed resume file(s)"
    "$mysql_bin" -u "$user" "$db" -e "DELETE FROM users WHERE email LIKE 'smoke-%@example.com'" 2>/dev/null
    local left
    left=$("$mysql_bin" -u "$user" -N -B "$db" -e \
      "SELECT (SELECT COUNT(*) FROM users WHERE email LIKE 'smoke-%@example.com')" 2>/dev/null | tr -d '\r')
    if [ "$left" = "0" ]; then
      pass "cleanup left zero smoke users"
    else
      fail "cleanup could not delete the smoke user (count=$left); check MYSQL_USER / MYSQL_PWD"
    fi
  fi
  rm -rf "$TMP"
  echo
  echo "Passed: $PASS, Failed: $FAIL, Warnings: $WARN"
  if [ "$FAIL" -ne 0 ] || [ "$status" -ne 0 ]; then exit 1; fi
  exit 0
}
trap cleanup EXIT

# ---- the walk-through ----

echo "Smoke test against $BASE_URL as $EMAIL"
for f in "$SAMPLE" "$SAMPLE_PDF" "$SAMPLE_DOCX"; do
  if [ ! -f "$f" ]; then echo "Sample resume not found: $f"; exit 1; fi
done

get /
if [ "$CODE" = 000 ]; then echo "FAIL  cannot reach $BASE_URL (is Tomcat running?)"; FAIL=$((FAIL + 1)); exit 1; fi

echo "== public pages =="
for p in / /login /register; do
  get "$p"
  check "GET $p is 200" "$([ "$CODE" = 200 ] && echo 1 || echo 0)"
done

echo "== protected pages while logged out =="
for p in /dashboard /analysis /score /skill-gap /jobs "/job?id=101" /my-jobs /resume-file; do
  get "$p"
  check "GET $p redirects to /login" \
    "$([ "$CODE" = 302 ] && location | grep -q '/login' && echo 1 || echo 0)"
done

echo "== security headers (WARN only) =="
get /login
for h in X-Content-Type-Options X-Frame-Options Content-Security-Policy; do
  if header_has "$h"; then pass "header $h present"; else warn "header $h missing"; fi
done

echo "== register =="
get_token /register
post /register "name=$NAME" "email=$EMAIL" "password=$PASSWORD" "confirmPassword=$PASSWORD"
check "register redirects to /login" "$([ "$CODE" = 302 ] && location | grep -q '/login' && echo 1 || echo 0)"
get_token /register
post /register "name=$NAME" "email=$EMAIL" "password=$PASSWORD" "confirmPassword=$PASSWORD"
check "duplicate register is rejected" \
  "$([ "$CODE" = 200 ] && body_has 'already exists' && no_leak && echo 1 || echo 0)"
get_token /register
post /register "name=$NAME" "email=smoke-${STAMP}b@example.com" "password=$PASSWORD" "confirmPassword=different1!"
check "mismatched passwords rejected" "$([ "$CODE" = 200 ] && body_has "match" && echo 1 || echo 0)"

echo "== login =="
get_token /login
post /login "email=$EMAIL" "password=WrongPass!999"
check "bad login rejected" \
  "$([ "$CODE" = 200 ] && body_has 'email or password is incorrect' && no_leak && echo 1 || echo 0)"
get_token /login
post /login "email=$EMAIL" "password=$PASSWORD"
check "login redirects to /dashboard" "$([ "$CODE" = 302 ] && location | grep -q '/dashboard' && echo 1 || echo 0)"
get_token /dashboard
check "dashboard is 200 after login" "$([ "$CODE" = 200 ] && echo 1 || echo 0)"

echo "== CSRF =="
CODE=$(curl -s --connect-timeout 5 --max-time 60 -o "$BODY" -D "$HDRS" -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST \
  --data-urlencode "jobId=9999" --data-urlencode "action=save" --data-urlencode "_csrf=wrong-token-value" \
  "$BASE_URL/save-job")
if [ "$CODE" = 403 ]; then pass "POST with a wrong _csrf gets 403"; else warn "POST with a wrong _csrf got $CODE, not 403"; fi
check "CSRF rejection shows no stack trace" "$(no_leak && echo 1 || echo 0)"
get_token /dashboard

echo "== upload the sample resume =="
upload "$SAMPLE" arun_kumar_resume.txt
check "sample upload redirects to /analysis" "$([ "$CODE" = 302 ] && location | grep -q '/analysis' && echo 1 || echo 0)"
get_token /analysis
check "analysis page loads" "$([ "$CODE" = 200 ] && echo 1 || echo 0)"
check "analysis page shows 7 skills found" "$(body_has '>7<|7 skills' && echo 1 || echo 0)"
get_token /score
check "score page shows 78" "$([ "$CODE" = 200 ] && body_has '(^|[^0-9])78([^0-9]|$)' && echo 1 || echo 0)"

echo "== jobs =="
get_token /jobs
check "jobs page loads and lists job 101" "$([ "$CODE" = 200 ] && body_has 'Java Backend Intern' && echo 1 || echo 0)"
get_token "/job?id=101"
check "job 101 shows 80%" "$([ "$CODE" = 200 ] && body_has '80 ?%' && echo 1 || echo 0)"
get "/job?id=9999"
check "job 9999 shows a friendly message" "$(body_has 'no longer exists' && no_leak && echo 1 || echo 0)"
get_token "/skill-gap?job=101"
check "skill gap for job 101 shows 67%" "$([ "$CODE" = 200 ] && body_has '67 ?%' && echo 1 || echo 0)"

echo "== save, apply, status, remove =="
get_token "/job?id=101"
post /save-job "jobId=101" "action=save"
check "save job redirects" "$([ "$CODE" = 302 ] && echo 1 || echo 0)"
get_token "/my-jobs"
check "saved job is listed in My jobs" "$(body_has 'Java Backend Intern' && echo 1 || echo 0)"
post /application "jobId=101" "action=apply" "tab=saved"
check "apply redirects to /my-jobs" "$([ "$CODE" = 302 ] && location | grep -q '/my-jobs' && echo 1 || echo 0)"
get_token "/my-jobs?tab=applied"
check "applied tab lists the job" "$(body_has 'Java Backend Intern' && echo 1 || echo 0)"
post /application "jobId=101" "action=status" "status=Interview"
get "/my-jobs?tab=applied"
check "status change to Interview is kept" "$(body_has 'value="Interview"[^>]*selected|selected[^>]*value="Interview"' && echo 1 || echo 0)"
post /application "jobId=101" "action=remove" "tab=applied"
check "remove redirects to /my-jobs" "$([ "$CODE" = 302 ] && location | grep -q '/my-jobs' && echo 1 || echo 0)"
get "/my-jobs?tab=applied"
check "removed job is gone from the applied tab" "$(body_has 'Java Backend Intern' && echo 0 || echo 1)"

echo "== download =="
get_token /dashboard
curl -s --connect-timeout 5 --max-time 60 -o "$TMP/download.txt" -D "$HDRS" -b "$JAR" -c "$JAR" "$BASE_URL/resume-file" -w '%{http_code}' > "$TMP/code.txt"
check "download returns 200" "$([ "$(cat "$TMP/code.txt")" = 200 ] && echo 1 || echo 0)"
check "downloaded bytes equal the uploaded file" "$(cmp -s "$TMP/download.txt" "$SAMPLE" && echo 1 || echo 0)"

echo "== upload a PDF and a DOCX resume =="
for kind in pdf docx; do
  if [ "$kind" = pdf ]; then file="$SAMPLE_PDF"; type="application/pdf"
  else file="$SAMPLE_DOCX"; type="application/vnd.openxmlformats-officedocument.wordprocessingml.document"; fi
  get_token /dashboard
  upload "$file" "arun_kumar_resume.$kind"
  check "$kind upload redirects to /analysis" "$([ "$CODE" = 302 ] && location | grep -q '/analysis' && echo 1 || echo 0)"
  get_token /analysis
  check "$kind analysis shows 7 skills found" "$([ "$CODE" = 200 ] && body_has '>7<|7 skills' && echo 1 || echo 0)"
  get_token /score
  check "$kind score page shows 78" "$([ "$CODE" = 200 ] && body_has '(^|[^0-9])78([^0-9]|$)' && echo 1 || echo 0)"
  curl -s --connect-timeout 5 --max-time 60 -o "$TMP/download.$kind" -D "$HDRS" -b "$JAR" -c "$JAR" "$BASE_URL/resume-file" -w '%{http_code}' > "$TMP/code.txt"
  check "$kind download returns 200 with the right Content-Type" \
    "$([ "$(cat "$TMP/code.txt")" = 200 ] && grep -qi "^content-type: $type" "$HDRS" && echo 1 || echo 0)"
  check "downloaded $kind bytes equal the uploaded file" "$(cmp -s "$TMP/download.$kind" "$file" && echo 1 || echo 0)"
done

echo "== bad uploads =="
printf 'not really a pdf' > "$TMP/resume.pdf"
printf 'old word file' > "$TMP/resume.doc"
: > "$TMP/empty.txt"
head -c 307200 /dev/zero | tr '\0' 'a' > "$TMP/big.txt"

get_token /dashboard
upload "$TMP/resume.doc"
check ".doc upload rejected with a friendly message" \
  "$([ "$CODE" = 200 ] && body_has 'Upload a \.txt, \.pdf or \.docx file' && no_leak && echo 1 || echo 0)"
get_token /dashboard
upload "$TMP/resume.pdf"
check "text renamed to .pdf rejected with a friendly message" \
  "$([ "$CODE" = 200 ] && body_has 'real PDF' && no_leak && echo 1 || echo 0)"
get_token /dashboard
upload "$TMP/empty.txt"
check "empty file rejected with a friendly message" \
  "$([ "$CODE" = 200 ] && body_has 'file is empty' && no_leak && echo 1 || echo 0)"
get_token /dashboard
upload "$TMP/big.txt"
check "300 KB file rejected with a friendly message" \
  "$([ "$CODE" = 200 ] && body_has 'larger than 200 KB' && no_leak && echo 1 || echo 0)"
get_token /dashboard
upload "$SAMPLE" "../evil.txt"
check "../evil.txt filename handled without a stack trace" \
  "$({ [ "$CODE" = 200 ] || [ "$CODE" = 302 ]; } && no_leak && echo 1 || echo 0)"

echo "== error pages =="
get "/no-such-page-$STAMP"
check "unknown URL gives a friendly 404" \
  "$([ "$CODE" = 404 ] && ! grep -Eq "$LEAK|SQL" "$BODY" && echo 1 || echo 0)"
get_token /dashboard
post /dashboard "x=1"
check "POST to a GET-only page gives a friendly 405" \
  "$([ "$CODE" = 405 ] && ! grep -Eq "$LEAK|SQL" "$BODY" && echo 1 || echo 0)"

echo "== logout =="
get_token /dashboard
get /logout
if [ "$CODE" = 405 ]; then post /logout "x=1"; fi   # logout may be a POST on newer builds
check "logout redirects to /login" "$([ "$CODE" = 302 ] && location | grep -q '/login' && echo 1 || echo 0)"
get /dashboard
check "dashboard redirects to /login after logout" \
  "$([ "$CODE" = 302 ] && location | grep -q '/login' && echo 1 || echo 0)"

# the EXIT trap prints the summary, cleans up and sets the exit status
