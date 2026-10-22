# ResumeMatch

**Smart Resume Analyzer & Job Recommendation System**

A student uploads a plain-text resume. ResumeMatch extracts the skills it contains,
scores the resume out of 100, shows the skills missing for a target job, and ranks
job openings by how many of their required skills the student already has.

Analysis is **rule-based**: skills are found by matching the resume text against a
fixed skill dictionary stored in the database.

## Features
- Register and log in (salted PBKDF2 password hashes, session-based access control, sign-in attempt limit)
- Upload a `.txt` resume (type, size, encoding and empty-file validation, 200 KB limit)
- Skill extraction, grouped by category, with the skills highlighted in the resume text
- Five-part resume score (skills, projects, education, certifications, experience) with the reason for every rule
- Job search with keyword, location, type and experience filters and a minimum-match slider
- Job detail page that shows the match calculation, e.g. 4 of 5 required skills = 80%
- Skill gap analysis against a chosen job, with the skills most worth learning next
- Save jobs, mark them as applied and track status (Applied, Interview, Rejected, Selected)
- Light and dark theme, phone-width layout, keyboard accessible

## Tech stack
Java 17 · OOP · JDBC · MySQL 8 · Java file handling · Collections · exception handling ·
Servlets and JSP (Tomcat 9, `javax.servlet`) · HTML · CSS · JavaScript

No frameworks, no ORM and no build tool: the project compiles with plain `javac`. The only
jars are MySQL Connector/J and Tomcat's servlet API. Fonts (Bricolage Grotesque and
Source Serif 4, SIL Open Font License) are bundled, so pages work offline.

## Architecture
```
Browser  ->  Filters (security headers, CSRF, login)  ->  Servlet (web)  ->  Service (service)  ->  DAO (dao)  ->  MySQL
                                                              |
                                                         JSP views (display only)
```
- `model`: plain objects passed between layers (`User`, `Resume`, `Skill`, `Job`, `JobMatch`, ...)
- `exception`: custom exceptions with user-friendly messages; no stack trace ever reaches the browser
- `util`: JDBC connection, password hashing, sign-in attempt limit, input validation, HTML escaping
- `dao`: all SQL, using `PreparedStatement` only; the resume save is one transaction
- `service`: file reading and storage, resume analysis, scoring and job matching
  (`JobMatcher` interface with `SkillBasedJobMatcher` implementation)
- `web`: servlets, filters and the startup listener that sets up logging
- `console`: `ConsoleDemo`, which runs the whole analysis pipeline without a server

## Project layout
```
database/          01_schema.sql, 02_seed_data.sql
sample-data/       two sample resumes
src/main/java/     com.resumematch.{model,exception,util,dao,service,web,console}
src/main/webapp/   JSP views, CSS, JavaScript and fonts
test/              plain-Java unit tests (TestRunner)
scripts/           end-to-end smoke tests (bash and PowerShell)
site/              static project page published to GitHub Pages
lib/               MySQL Connector/J and servlet-api jars (not committed)
build.sh           build, test and deploy script for Linux/macOS/Git Bash
build.bat          the same for Windows
Dockerfile, docker-compose.yml   container setup (Tomcat 9 + MySQL 8)
```

## Prerequisites
| Tool | Version | Needed for |
|---|---|---|
| JDK | 17 or newer | building and running |
| MySQL | 8.x | the database |
| Apache Tomcat | 9.x (`javax.servlet`) | running the web app |
| MySQL Connector/J | `mysql-connector-j-<version>.jar` | the JDBC driver |
| bash + curl, or PowerShell 5.1 | any | the smoke test (optional) |
| Docker with Compose | any recent | the container setup (optional, replaces all of the above) |

## Quick start with Docker
```
cp .env.example .env        # then change the passwords in .env
docker compose up --build
```
Open http://localhost:8080/ResumeMatch/ and log in with `demo@resumematch.in` / `demo1234`.
MySQL runs `database/01_schema.sql` and `02_seed_data.sql` the first time it starts. The data
lives in the `db-data` volume and uploaded resumes in the `resumes` volume.
`docker compose down` stops everything; `docker compose down -v` also deletes the data.

## Manual setup
1. **Jars.** Put `mysql-connector-j-<version>.jar` (from https://dev.mysql.com/downloads/connector/j/,
   *Platform Independent*) and Tomcat's `servlet-api.jar` (from `<tomcat>/lib/`) into `lib/`.
   Copy the MySQL jar into `<tomcat>/lib/` as well and restart Tomcat once.
2. **Database.** Run the two scripts in order:
   ```
   mysql -u root -p < database/01_schema.sql
   mysql -u root -p < database/02_seed_data.sql
   ```
   This creates the `resumematch` database with 18 skills, 8 jobs and a demo user. Both scripts can be
   run again: the schema script drops and recreates the database, and the seed script clears the
   seeded tables before inserting. **Running either again deletes all accounts and resumes.**
3. **Connection settings.** Copy `src/main/resources/db.properties.example` to
   `src/main/resources/db.properties` and set `db.user` and `db.password`. This file is not committed.
4. **Build.** `build.bat` on Windows, `./build.sh` elsewhere. This produces `build/ResumeMatch.war`.
5. **Deploy.** `build.bat deploy` (or `./build.sh deploy`) builds the war and copies it into
   `%CATALINA_HOME%\webapps` (default `C:\Program Files\Apache Software Foundation\Tomcat 9.0`).
   Tomcat notices the new war and redeploys it by itself; the script never stops or starts Tomcat
   or any other service. Then open http://localhost:8080/ResumeMatch/

## Configuration
| Setting | Where | Default |
|---|---|---|
| Database URL, user, password | `db.properties` keys `db.url`, `db.user`, `db.password`, or the environment variables `RESUMEMATCH_DB_URL`, `RESUMEMATCH_DB_USER`, `RESUMEMATCH_DB_PASSWORD` (these win) | `jdbc:mysql://localhost:3306/resumematch`, `root`, empty |
| Database timeouts | added to the URL automatically unless it already sets them | connect 5 s, socket 30 s |
| Resume folder | context parameter `resumeDir` in `WEB-INF/web.xml` | `<tomcat>/resumes/user_<id>/` |
| Application log | `java.util.logging`, set up at startup | `<tomcat>/logs/resumematch.0.log` (1 MB × 5 files, rotated) |
| Session timeout | `WEB-INF/web.xml` | 30 minutes |

To use another resume folder, add this to `WEB-INF/web.xml`:
```xml
<context-param>
  <param-name>resumeDir</param-name>
  <param-value>C:/resumematch-uploads</param-value>
</context-param>
```

## Security notes
- **Passwords** are stored as salted PBKDF2 hashes (`PBKDF2WithHmacSHA256`, 210,000 iterations,
  16-byte random salt) in the form `pbkdf2$210000$<salt>$<hash>`. Accounts created by older versions
  (unsalted SHA-256) can still log in, and their hash is upgraded to PBKDF2 on that login.
- **Sign-in limit:** after 5 failed attempts for one email, or 30 from one IP address, within
  15 minutes, sign-in is refused until the window ends. The counters are kept in memory and reset
  when Tomcat restarts.
- **CSRF:** every form carries a random per-session token (`_csrf`) that is checked on every POST.
  A missing or wrong token gets a friendly 403 page.
- **Session cookie** is `HttpOnly` and `SameSite=Lax`, sessions end after 30 idle minutes, and a new
  session is started at login.
- **Response headers:** `Content-Security-Policy` (scripts, styles and fonts from the app only),
  `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`.
  Private pages are sent with `Cache-Control: no-store`.
- **Request limits:** request bodies over 1 MB are refused (413). Resumes must be `.txt`, 1 byte to
  200 KB, valid UTF-8 text without NUL bytes. Name, email and password lengths are limited.
- **Uploads** are saved under a generated file name inside the user's own folder; the submitted name
  is only displayed. Paths are checked to stay inside the resume folder, and users can only download
  their own resumes.
- **Errors:** users only see friendly messages; details go to the application log.
- **SQL:** every query is a `PreparedStatement` in a DAO class.
- **HTTPS** is not set up by the app itself. When it is put online, run it behind a host or proxy
  that provides HTTPS. The Docker image trusts `X-Forwarded-For` / `X-Forwarded-Proto` only from
  private-network proxies.

## Running the tests
**Unit tests** (plain Java, no extra libraries, no database needed):
```
./build.sh test          # or: build.bat test
```
`test/com/resumematch/TestRunner.java` checks the analyzer, scorer, job matcher, validation,
password hashing, sign-in limit and file service. Among other things it asserts that the sample
resume scores 78/100 with 7 skills, job 101 matches 80% and the skill gap for it is 67%. It exits
with status 1 on any failure. `./build.sh test deploy` deploys only when the tests pass.

**End-to-end smoke test** against a running server:
```
MYSQL_PWD=<mysql root password> scripts/smoke-test.sh http://localhost:8080/ResumeMatch
```
```
$env:MYSQL_PWD = "<mysql root password>"; .\scripts\smoke-test.ps1 -BaseUrl http://localhost:8080/ResumeMatch
```
It registers a throw-away `smoke-<timestamp>@example.com` user, walks through every page and flow
(including a `.pdf`, an empty file, an oversized file and a `../` file name), checks the security
headers, CSRF and the friendly error pages, and then deletes that user's resume files and database
rows, even when a check fails. The clean-up uses the `mysql` client; `MYSQL` (client path),
`MYSQL_USER` and `MYSQL_DB` can be set as well.

## Backup and restore
Back up the database and the resume folder together, because each resume row points to a file:
```
mysqldump -u root -p --single-transaction resumematch > resumematch-backup.sql
```
and copy `<tomcat>/resumes/` (with Docker, the `resumes` volume). To restore:
```
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS resumematch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mysql -u root -p resumematch < resumematch-backup.sql
```
then put the resume folder back in the same place. With Docker:
```
docker compose exec db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" resumematch' > resumematch-backup.sql
docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" resumematch' < resumematch-backup.sql
```

## Putting it online
GitHub Pages only serves static files, so it hosts the **project page** in `site/`, not the app.
The workflow `.github/workflows/pages.yml` publishes that page on every push to `main` once Pages
is enabled (Settings → Pages → Source: GitHub Actions).

The app itself needs a host that runs a Docker image and a MySQL 8 database:
1. Create a MySQL 8 database with any provider and run `database/01_schema.sql` and
   `02_seed_data.sql` against it. If the provider does not allow `DROP DATABASE`, run the schema
   script without its first lines (the `DROP`/`CREATE DATABASE`/`USE` statements) inside the
   database you were given.
2. Deploy this repository's `Dockerfile` as a web service on port 8080 with the environment
   variables `RESUMEMATCH_DB_URL` (for example `jdbc:mysql://<host>:3306/resumematch?sslMode=REQUIRED`),
   `RESUMEMATCH_DB_USER` and `RESUMEMATCH_DB_PASSWORD`. The app is served at `/ResumeMatch/` and
   `/` redirects there.
3. Give the service persistent storage at `/usr/local/tomcat/resumes`, or uploaded files are lost
   when it restarts.
4. Set the repository variable `LIVE_APP_URL` (Settings → Secrets and variables → Actions →
   Variables) to the app's address and re-run the Pages workflow; the project page then shows an
   "Open the app" button.
5. Change the demo account's password, or delete it, before sharing the link.

## Demo script
Log in with `demo@resumematch.in` / `demo1234`, or register a new account.

1. **Dashboard:** upload `sample-data/arun_kumar_resume.txt`. Uploading a `.pdf`, an empty file or a
   file over 200 KB shows a message and saves nothing.
2. **Analysis:** 7 skills are found (Java, SQL, JDBC, Git, DSA, C++, Python) and highlighted in the text.
3. **Score:** the resume scores **78/100**; every one of the five rules shows why it earned its points.
4. **Jobs:** search "Java". Four jobs are listed best match first: 80%, 75%, 40%, 33%.
5. **Job detail:** the Java Backend Intern job shows `4 of 5 required skills = 80%`, with Spring Boot missing.
6. **Skill gap:** for the same job the skill match is 67% (Spring Boot and REST API missing) and the
   page suggests what to learn next.
7. **My jobs:** save a job, mark it applied, change its status and remove it.
8. **Security:** sign out, then enter a wrong password five times for one email; the sixth try is
   refused with a "please wait" message.

### Run the analysis without a server
```
javac --release 17 -cp "lib/*" -d build/classes $(find src/main/java -name '*.java')
java -cp "build/classes:src/main/resources:lib/*" com.resumematch.console.ConsoleDemo
```
(On Windows use `;` instead of `:` in the classpath.) It prints the extracted skills, the score
breakdown and the ranked job list for `sample-data/arun_kumar_resume.txt`.

## Troubleshooting
| Problem | Fix |
|---|---|
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` in the log | The MySQL jar is missing from `<tomcat>/lib/`. Copy it there and restart Tomcat. |
| "Can't reach the database right now" | MySQL is not running, or `db.user` / `db.password` are wrong. Check `<tomcat>/logs/resumematch.0.log`, fix `db.properties` and rebuild. |
| `Access denied for user 'root'` in the log | The password in `db.properties` does not match your MySQL password. |
| "This form expired" (403) after submitting a form | The session timed out or the page was open in an old tab. Reload the page and submit again. |
| "Too many failed sign-in attempts" | Wait for the time shown, or restart Tomcat to clear the in-memory counters. |
| Build says `servlet-api.jar is missing` | Copy `servlet-api.jar` from `<tomcat>/lib/` into `lib/`. |
| `build.sh` fails with `$'\r': command not found` | The script was checked out with Windows line endings. Run `git add --renormalize . && git checkout -- build.sh`. |
| Port 8080 already in use | Stop the other program, or change the connector port in `<tomcat>/conf/server.xml` (Docker: set `APP_PORT` in `.env`). |
| `package javax.servlet does not exist` or `jakarta` errors | This project targets Tomcat 9 (`javax.servlet`). Tomcat 10 and later use `jakarta.servlet`. |
| Upload fails or downloads return 404 | Make sure Tomcat can write to `<tomcat>/resumes/`, or set the `resumeDir` parameter shown above. |
| Dates are a few hours off | Remove any `serverTimezone` setting from `db.url`; the driver detects the server time zone itself. |
| Smoke test cannot clean up | Put the `mysql` client on the PATH (or set `MYSQL`) and set `MYSQL_PWD`. |

## Team
| Member | Area |
|---|---|
| Barathkumar M P | Database, analysis engine, authentication, upload and analysis pages, build, packaging and deployment |
| Gokul K | Models, exceptions, JDBC data access, password hashing and sign-in limit |
| Hemanth Suresh | Page shell: stylesheet, scripts, landing and login pages, security filters |
| Faseesh | Resume score page, resume download, accessibility and mobile layout |
| Rithik | Job search, job detail and skill gap, logging and upload checks |
| Rishi | Saved jobs and application tracking, unit and smoke tests |
