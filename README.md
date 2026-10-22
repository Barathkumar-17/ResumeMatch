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
