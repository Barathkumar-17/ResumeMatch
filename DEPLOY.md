# Share a live demo

Put ResumeMatch on a public `https://` address from the PC that already runs it, for as long as you
want, with no router changes and no account. Stop the script and the address stops working.

## What the tunnel is

[Cloudflare quick tunnels](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/do-more-with-tunnels/trycloudflare/)
are free. The `cloudflared` program on your PC opens an outbound connection to Cloudflare and Cloudflare
gives it a random address such as `https://some-random-words.trycloudflare.com`. Visitors talk to
Cloudflare over HTTPS; Cloudflare passes each request through the tunnel to Tomcat on
`http://localhost:8080`. The address changes every time you start the tunnel.

Tomcat sees every request as coming from the tunnel program on the same machine. The app is set up for
that: `META-INF/context.xml` has a `RemoteIpValve` that trusts only `127.0.0.1` / `::1`, so the real
visitor address (`X-Forwarded-For`) is used for the sign-in limit and the session cookie gets `Secure`
(`X-Forwarded-Proto: https`). Requests straight to `http://localhost:8080` behave as before.

## Install

Windows:
```
winget install --id Cloudflare.cloudflared -e
```
macOS: `brew install cloudflared`. Linux: see the
[downloads page](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/).
Check it with `cloudflared --version`.

## Run

Tomcat and MySQL must be running and ResumeMatch deployed (`./build.sh deploy`). Then:

```
powershell -ExecutionPolicy Bypass -File scripts\public-demo.ps1     # Windows PowerShell
scripts/public-demo.sh                                               # Git Bash, Linux, macOS
```

The script checks that `cloudflared` is installed and that the app answers on
`http://localhost:8080/ResumeMatch/login`, switches on live-demo mode, starts the tunnel, and prints the
public address with `/ResumeMatch/` already added, plus the demo login. It keeps running until you press
Ctrl+C.

Live-demo mode pauses new sign-ups (the register page says so and `POST /register` is refused);
everything else works. The script switches it on by creating the flag file
`<tomcat>/resumematch-demo-mode.flag` (the app checks for it every 10 seconds) and deletes the file when it
ends. If the script is killed hard (the window is closed, the PC loses power) the file stays, so sign-ups
stay paused until you delete it. That is the safe direction. Set `CATALINA_BASE` if your Tomcat folder is
not the default one. Instead of the flag file you can set the environment variable
`RESUMEMATCH_DEMO_MODE=true` or the `demoMode` context parameter in `web.xml`.

If the tunnel will not connect, the network may block outbound port 7844 (TCP and UDP). Try another
network or a phone hotspot. The script uses `--protocol http2`; set `PROTOCOL=quic` to try the default.

## What to share

Share the printed address, for example `https://some-random-words.trycloudflare.com/ResumeMatch/`, and the
demo login `demo@resumematch.in` / `demo1234`. The root address redirects to `/ResumeMatch/`, so sharing
either works. Send a new address every time you start the tunnel.

## Stop

Press Ctrl+C in the window that runs the script. The address stops working at once, the flag file is
removed and sign-ups are open again. The script never stops or starts Tomcat or MySQL.

## Security checklist

Do these once before sharing the address with people you do not know.

- [x] **Proxy setting.** `META-INF/context.xml` contains the `RemoteIpValve` for loopback only. Without it every
  visitor would look like `127.0.0.1` and the limit of 30 failed sign-ins per address would lock everyone out.
- [ ] **Tomcat defaults hidden.** Move `webapps/docs`, `webapps/examples`, `webapps/manager` and
  `webapps/host-manager` to a folder outside `webapps` (for example `webapps-disabled`), and replace
  `webapps/ROOT` with a page that redirects to `/ResumeMatch/`. Otherwise visitors can see the Tomcat
  version and, worse, the manager login.
- [ ] **Dedicated database user.** Do not run the public site as `root`. In MySQL:
  ```sql
  CREATE USER 'resumematch'@'localhost' IDENTIFIED BY '<a long random password>';
  GRANT SELECT, INSERT, UPDATE, DELETE ON resumematch.* TO 'resumematch'@'localhost';
  ```
  then put that user and password in `src/main/resources/db.properties` (or the `RESUMEMATCH_DB_*`
  environment variables), rebuild and `./build.sh deploy`. Keep the `root` password for admin work only.
- [ ] **Demo password.** Everyone who gets the link shares the demo account. If you share widely, change its
  password (log in as a user you register yourself, or update the row) or reset its data after each
  session (below). Do not put anything private in the demo account.
- [ ] **Limits.** Uploads stop when all stored resumes together reach 200 MB
  (`maxResumeStorageMb` context parameter or `RESUMEMATCH_MAX_STORAGE_MB`), and each user keeps only their
  five newest resumes. Lower the 200 MB if the disk is small.
- [ ] **Hardening the server.** Read [Hardening the server](README.md#hardening-the-server) in the README:
  hide Tomcat's version on error pages, keep `logs/` private, and use a strong MySQL password.

### Reset the demo account's data

This clears only the demo user's resumes, saved jobs and applications. Other accounts are not touched.
Run it as an administrator (the `root` user), then delete the demo user's folder under `<tomcat>/resumes/`
(`user_<id>`, where `<id>` is the first query's result).

```sql
USE resumematch;
SELECT id FROM users WHERE email = 'demo@resumematch.in';
DELETE FROM resumes      WHERE user_id = (SELECT id FROM users WHERE email = 'demo@resumematch.in');
DELETE FROM saved_jobs   WHERE user_id = (SELECT id FROM users WHERE email = 'demo@resumematch.in');
DELETE FROM applications WHERE user_id = (SELECT id FROM users WHERE email = 'demo@resumematch.in');
```

`resume_skills` rows go with their resumes. Re-running `database/02_seed_data.sql` also works but deletes
every account and resume, so only use it if you accept losing the other accounts.

## A permanent address (outline)

A quick tunnel is for demos. For an address that stays up without your PC:

1. A small Ubuntu VM (any cloud provider) with a domain name pointing at its IP address.
2. Install `openjdk-17-jre`, Tomcat 9 and MySQL 8 from the distribution or the official downloads.
   Create the `resumematch` database and the dedicated database user above. Open only ports 80 and 443 in
   the firewall, never 8080 or 3306.
3. Build the war (`./build.sh`), copy it to Tomcat's `webapps/`, and run Tomcat under its own service user.
4. Put [Caddy](https://caddyserver.com/) in front for automatic HTTPS: a one-line `Caddyfile`
   (`example.com { reverse_proxy localhost:8080 }`). Caddy sends `X-Forwarded-For` and
   `X-Forwarded-Proto`, which the `RemoteIpValve` already reads because Caddy runs on the same machine.
5. Hide the Tomcat defaults, as in the checklist, and keep `RESUMEMATCH_DEMO_MODE` unset if you want people
   to be able to register.
