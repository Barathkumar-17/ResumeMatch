#!/bin/bash
# Share the locally running ResumeMatch on a temporary public https address
# using a free Cloudflare quick tunnel. Runs until you press Ctrl+C.
#
#   scripts/public-demo.sh
#
# Optional environment variables:
#   LOCAL_URL   where the app answers (default http://localhost:8080)
#   PROTOCOL    tunnel protocol: http2 (default, works on most networks) or quic
#
# While it runs, the script also creates the flag file <tomcat>/resumematch-demo-mode.flag, which
# makes the app pause new sign-ups (the shared demo account still works). The file is removed again
# when the script ends. The Tomcat folder is $CATALINA_BASE, else $CATALINA_HOME, else the Windows default.
#
# The public address changes every run and stops working when this script ends.

LOCAL_URL="${LOCAL_URL:-http://localhost:8080}"
PROTOCOL="${PROTOCOL:-http2}"

CF="$(command -v cloudflared || command -v cloudflared.exe)"
for p in "/c/Program Files (x86)/cloudflared/cloudflared.exe" "/c/Program Files/cloudflared/cloudflared.exe"; do
  [ -z "$CF" ] && [ -x "$p" ] && CF="$p"
done
if [ -z "$CF" ]; then
  echo "cloudflared is not installed. Install it first:"
  echo "  Windows : winget install --id Cloudflare.cloudflared -e"
  echo "  macOS   : brew install cloudflared"
  echo "  Linux   : https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/"
  exit 1
fi

if ! curl -s -o /dev/null -f --max-time 10 "$LOCAL_URL/ResumeMatch/login"; then
  echo "The app does not answer at $LOCAL_URL/ResumeMatch/login."
  echo "Start Tomcat and MySQL, deploy ResumeMatch (./build.sh deploy), then run this again."
  exit 1
fi

TOMCAT="${CATALINA_BASE:-${CATALINA_HOME:-/c/Program Files/Apache Software Foundation/Tomcat 9.0}}"
FLAG="$TOMCAT/resumematch-demo-mode.flag"
if ! : > "$FLAG" 2>/dev/null; then
  echo "Cannot write $FLAG. Set CATALINA_BASE to your Tomcat folder and make sure you may write there."
  exit 1
fi

LOG="$(mktemp)"
cleanup() { [ -n "$PID" ] && kill "$PID" 2>/dev/null; rm -f "$LOG" "$FLAG"; echo; echo "Tunnel stopped. The public address no longer works."; }
trap cleanup EXIT
trap 'exit 0' INT TERM

echo "Starting the tunnel (this takes a few seconds)..."
MSYS_NO_PATHCONV=1 "$CF" tunnel --url "$LOCAL_URL" --protocol "$PROTOCOL" --no-autoupdate >"$LOG" 2>&1 &
PID=$!

URL=""
for _ in $(seq 1 60); do
  URL="$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$LOG" | head -1)"
  [ -n "$URL" ] && break
  kill -0 "$PID" 2>/dev/null || break
  sleep 1
done
if [ -z "$URL" ]; then
  echo "The tunnel did not start. Last lines of its log:"; tail -n 10 "$LOG"; exit 1
fi

# The address is printed before the connection is up; wait for the first registered connection.
for _ in $(seq 1 40); do
  grep -q 'Registered tunnel connection' "$LOG" && break
  kill -0 "$PID" 2>/dev/null || break
  sleep 1
done
if ! grep -q 'Registered tunnel connection' "$LOG"; then
  echo "The tunnel could not connect to Cloudflare. Outbound port 7844 (TCP and UDP) may be blocked"
  echo "on this network; try another network or a phone hotspot. Last lines of its log:"
  tail -n 6 "$LOG"; exit 1
fi

echo
echo "ResumeMatch is live:"
echo "  $URL/ResumeMatch/"
echo "Demo login: demo@resumematch.in / demo1234"
echo
echo "Live-demo mode is ON: new sign-ups are paused. Everyone shares the demo account, so change its"
echo "password or reset its data afterwards (see DEPLOY.md)."
echo
echo "The address changes every run and stops when this window closes. Press Ctrl+C to stop."
wait "$PID"
