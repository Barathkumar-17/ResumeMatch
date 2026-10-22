# ResumeMatch

**Smart Resume Analyzer & Job Recommendation System**

A student uploads a plain-text resume. ResumeMatch extracts the skills it contains,
scores the resume out of 100, shows the skills missing for a target job, and ranks
job openings by how many of their required skills the student already has.

Analysis is **rule-based**: skills are found by matching the resume text against a
fixed skill dictionary stored in the database.

## Features
- Register and log in (SHA-256 hashed passwords, session-based access control)
- Upload a `.txt` resume (type, size and empty-file validation, 200 KB limit)
- Skill extraction, grouped by category, with the skills highlighted in the resume text
- Five-part resume score (skills, projects, education, certifications, experience) with the reason for every rule
- Job search with keyword, location, type and experience filters and a minimum-match slider
- Job detail page that shows the match calculation, e.g. 4 of 5 required skills = 80%
- Skill gap analysis against a chosen job, with the skills most worth learning next
- Save jobs, mark them as applied and track status (Applied, Interview, Rejected, Selected)

## Tech stack
Java 17 · OOP · JDBC · MySQL 8 · Java file handling · Collections · exception handling ·
Servlets and JSP (Tomcat 9, `javax.servlet`) · HTML · CSS · JavaScript

No frameworks, no ORM and no build tool: the project compiles with plain `javac`.

## Architecture
```
Browser  ->  Servlet (web)  ->  Service (service)  ->  DAO (dao)  ->  MySQL
                  |
                 JSP views (display only)
```
- `model`: plain objects passed between layers (`User`, `Resume`, `Skill`, `Job`, `JobMatch`, ...)
- `exception`: custom exceptions with user-friendly messages; no stack trace ever reaches the browser
- `util`: JDBC connection, password hashing, input validation, HTML escaping
- `dao`: all SQL, using `PreparedStatement` only; the resume save is one transaction
- `service`: file reading and storage, resume analysis, scoring and job matching
  (`JobMatcher` interface with `SkillBasedJobMatcher` implementation)
- `web`: servlets and the login filter
- `console`: `ConsoleDemo`, which runs the whole analysis pipeline without a server

## Project layout
```
database/        01_schema.sql, 02_seed_data.sql
sample-data/     two sample resumes
src/main/java/   com.resumematch.{model,exception,util,dao,service,web,console}
src/main/webapp/ JSP views, CSS and JavaScript
lib/             MySQL Connector/J and servlet-api jars (not committed)
build.sh         build script for Linux/macOS/Git Bash
build.bat        build script for Windows
```

## Prerequisites
| Tool | Version |
|---|---|
| JDK | 17 or newer |
| MySQL | 8.x |
| Apache Tomcat | 9.x |
| MySQL Connector/J | 8.x or newer (`mysql-connector-j-<version>.jar`) |

## Setup
1. **Jars.** Put `mysql-connector-j-<version>.jar` (from https://dev.mysql.com/downloads/connector/j/,
   *Platform Independent*) and Tomcat's `servlet-api.jar` (from `<tomcat>/lib/`) into `lib/`.
   Copy the MySQL jar into `<tomcat>/lib/` as well and restart Tomcat.
2. **Database.** Run the two scripts in order:
   ```
   mysql -u root -p < database/01_schema.sql
   mysql -u root -p < database/02_seed_data.sql
   ```
   This creates the `resumematch` database with 18 skills, 8 jobs and a demo user.
3. **Connection settings.** Copy `src/main/resources/db.properties.example` to
   `src/main/resources/db.properties` and set `db.user` and `db.password`.
4. **Build.** `build.bat` on Windows, `./build.sh` elsewhere. This produces `build/ResumeMatch.war`.
5. **Deploy.** Copy `build/ResumeMatch.war` into `<tomcat>/webapps/`, start Tomcat and open
   http://localhost:8080/ResumeMatch/

Uploaded resumes are stored in `<tomcat>/resumes/user_<id>/`. To use another folder, add this
to `WEB-INF/web.xml`:
```xml
<context-param>
  <param-name>resumeDir</param-name>
  <param-value>C:/resumematch-uploads</param-value>
</context-param>
```

## Demo script
Log in with `demo@resumematch.in` / `demo1234`, or register a new account.

1. **Dashboard:** upload `sample-data/arun_kumar_resume.txt`. Uploading a `.pdf`, an empty file or a
   file over 200 KB shows a message and saves nothing.
2. **Analysis:** 7 skills are found (Java, SQL, JDBC, Git, DSA, C++, Python) and highlighted in the text.
3. **Score:** the resume scores **78/100**; every one of the five rules shows why it earned its points.
4. **Jobs:** search "Java". Four jobs are listed best match first: 80%, 75%, 40%, 33%.
5. **Job detail:** the Java Backend Intern job shows `4 of 5 required skills = 80%`, with Spring Boot missing.
6. **Skill gap:** for the same job the skill match is 67% (Spring Boot and REST API missing) and the page
   suggests what to learn next.
7. **My jobs:** save a job, mark it applied, change its status and remove it.

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
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | The MySQL jar is missing from `<tomcat>/lib/`. Copy it there and restart Tomcat. |
| "Can't reach the database right now" | MySQL is not running, or `db.user` / `db.password` in `db.properties` are wrong. Rebuild after editing. |
| `Access denied for user 'root'` | The password in `db.properties` does not match your MySQL root password. |
| Build says `servlet-api.jar is missing` | Copy `servlet-api.jar` from `<tomcat>/lib/` into `lib/`. |
| Port 8080 already in use | Stop the other program, or change the connector port in `<tomcat>/conf/server.xml`. |
| `package javax.servlet does not exist` or `jakarta` errors | This project targets Tomcat 9 (`javax.servlet`). Tomcat 10 and later use `jakarta.servlet` and need the imports changed. |
| Upload fails or downloads return 404 | Make sure Tomcat can write to `<tomcat>/resumes/`, or set the `resumeDir` parameter shown above. |
| Dates are a few hours off | Remove any `serverTimezone` setting from `db.url`; the driver detects the server time zone itself. |

## Team
| Member | Area |
|---|---|
| Barathkumar M P | Database, analysis engine, authentication, upload and analysis pages, build and packaging |
| Gokul K | Models, exceptions, JDBC data access |
| Hemanth Suresh | Page shell: stylesheet, scripts, landing and login pages |
| Faseesh | Resume score page and resume download |
| Rithik | Job search, job detail and skill gap |
| Rishi | Saved jobs and application tracking |
