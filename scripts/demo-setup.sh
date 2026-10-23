#!/bin/bash
# One-command demo setup for ResumeMatch: checks the tools, creates db.properties,
# optionally loads the database, runs the unit tests, builds and deploys the war,
# then waits until the site answers.
#
#   scripts/demo-setup.sh               use the database as it is
#   scripts/demo-setup.sh --reset-db    also (re)load database/01_schema.sql and 02_seed_data.sql
#                                       WARNING: this deletes every account, resume and job record
#
# Tomcat and MySQL are never started, stopped or restarted by this script.
#
# Optional environment variables:
#   CATALINA_HOME  Tomcat folder (default: C:/Program Files/Apache Software Foundation/Tomcat 9.0)
#   MYSQL          path to the mysql client (else "mysql" on the PATH, else the MySQL 8.0 default)
#   BASE_URL       site address to wait for (default: http://localhost:8080/ResumeMatch)
#   WAIT_SECONDS   how long to wait for the site (default 120)

cd "$(dirname "$0")/.." || exit 1
PROPS="src/main/resources/db.properties"
BASE_URL="${BASE_URL:-http://localhost:8080/ResumeMatch}"
BASE_URL="${BASE_URL%/}"
WAIT_SECONDS="${WAIT_SECONDS:-120}"
export CATALINA_HOME="${CATALINA_HOME:-C:/Program Files/Apache Software Foundation/Tomcat 9.0}"

RESET=0
for arg in "$@"; do
  case "$arg" in
    --reset-db) RESET=1 ;;
    -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $arg (use --reset-db or --help)"; exit 1 ;;
  esac
done

step() { echo; echo "== $*"; }
ok()   { echo "   ok: $*"; }
die()  { echo "   ERROR: $*" >&2; exit 1; }

step "1/7 Checking the tools"
command -v java  >/dev/null 2>&1 || die "java not found. Install JDK 17 or newer and put it on the PATH."
command -v javac >/dev/null 2>&1 || die "javac not found. You need a JDK (17 or newer), not only a JRE."
JV="$(javac -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')"
[ -n "$JV" ] && [ "$JV" -ge 17 ] 2>/dev/null || die "JDK 17 or newer is required (found: $(javac -version 2>&1))."
ok "javac $JV"

if [ -z "$MYSQL" ]; then
  if command -v mysql >/dev/null 2>&1; then MYSQL=mysql
  elif [ -x "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" ]; then MYSQL="C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
  else die "mysql client not found. Install MySQL 8 or set MYSQL to the client's path."; fi
fi
ok "mysql client: $MYSQL"

ls lib/servlet-api.jar >/dev/null 2>&1 || die "lib/servlet-api.jar is missing. Copy it from <tomcat>/lib/ (see README, Setup step 1)."
ls lib/mysql-connector-j*.jar >/dev/null 2>&1 || die "lib/mysql-connector-j-<version>.jar is missing (see README, Setup step 1)."
ok "lib/ jars present"

[ -d "$CATALINA_HOME/webapps" ] || die "Tomcat not found at: $CATALINA_HOME (set CATALINA_HOME to your Tomcat 9 folder)."
ok "Tomcat folder: $CATALINA_HOME"

step "2/7 Connection settings"
if [ -f "$PROPS" ]; then
  ok "$PROPS already exists, keeping it"
else
  [ -f "$PROPS.example" ] || die "$PROPS.example is missing."
  read -r -p "   MySQL user [root]: " DBUSER
  DBUSER="${DBUSER:-root}"
  read -r -s -p "   MySQL password (not shown): " DBPASS
  echo
  DBUSER="$DBUSER" DBPASS="$DBPASS" awk '
    /^db\.user=/     { print "db.user=" ENVIRON["DBUSER"]; next }
    /^db\.password=/ { p = ENVIRON["DBPASS"]; gsub(/\\/, "\\\\", p); print "db.password=" p; next }
    { print }' "$PROPS.example" > "$PROPS"
  ok "created $PROPS"
fi

prop() { sed -n "s/^$1=//p" "$PROPS" | tail -n 1 | tr -d '\r'; }
DBUSER="$(prop db.user)"
MYSQL_PWD="$(prop db.password)"
export MYSQL_PWD

step "3/7 Database"
if [ $RESET -eq 1 ]; then
  echo "   WARNING: --reset-db drops and recreates the resumematch database."
  echo "   Every account, resume and saved job will be deleted."
  read -r -p "   Type yes to continue: " ANSWER
  [ "$ANSWER" = "yes" ] || die "Cancelled, nothing was changed."
  "$MYSQL" -u "$DBUSER" < database/01_schema.sql || die "Loading database/01_schema.sql failed (check db.user and db.password)."
  "$MYSQL" -u "$DBUSER" < database/02_seed_data.sql || die "Loading database/02_seed_data.sql failed."
  ok "schema and seed data loaded"
else
  echo "   Not reloading the database (pass --reset-db to load the schema and seed data)."
  JOBS="$("$MYSQL" -u "$DBUSER" -N -B -e "SELECT COUNT(*) FROM resumematch.jobs" 2>/dev/null)"
  if [ -z "$JOBS" ]; then
    echo "   WARNING: could not read resumematch.jobs. On a fresh machine run again with --reset-db."
  else
    ok "database reachable, $JOBS jobs found"
  fi
fi
unset MYSQL_PWD

step "4/7 Unit tests, build and deploy"
./build.sh test deploy || die "Tests or build failed; see the output above."

step "5/7 Waiting for $BASE_URL/login (up to ${WAIT_SECONDS}s)"
command -v curl >/dev/null 2>&1 || die "curl not found, so the site cannot be checked. Open $BASE_URL/ in a browser instead."
UP=0
for ((i = 0; i < WAIT_SECONDS; i += 2)); do
  CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE_URL/login" 2>/dev/null)"
  if [ "$CODE" = "200" ]; then UP=1; break; fi
  sleep 2
done
[ $UP -eq 1 ] || die "The site did not answer within ${WAIT_SECONDS}s. Is Tomcat running? See README, Troubleshooting."
ok "site is up"

step "6/7 Ready"
echo "   Open:   $BASE_URL/"
echo "   Login:  demo@resumematch.in / demo1234"

step "7/7 First demo steps"
echo "   1. Log in with the demo account above."
echo "   2. On the dashboard, upload sample-data/arun_kumar_resume.txt."
echo "   3. Open Analysis (7 skills), then Score (78/100), then Jobs (search \"Java\": 80, 75, 40, 33)."
echo "   The full guided tour with screenshots is in DEMO.md."
