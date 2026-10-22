# ResumeMatch

**Smart Resume Analyzer & Job Recommendation System**

A student uploads a plain-text resume. ResumeMatch extracts the skills it contains,
scores the resume out of 100, shows the skills missing for a target job, and ranks
job openings by how many of their required skills the student already has.

Analysis is **rule-based**: skills are found by matching the resume text against a
fixed skill dictionary stored in the database.

## Features
- Register and log in (SHA-256 hashed passwords, session-based access control)
- Upload a `.txt` resume with validation (type, size, empty file)
- Skill extraction and a five-part resume score with the reason for every rule
- Job search with filters and a minimum-match slider, ranked best match first
- Skill gap analysis against a chosen job
- Save jobs, mark them applied and track application status

## Tech stack
Java 17 · OOP · JDBC · MySQL 8 · Java file handling · Collections · exception handling ·
Servlets and JSP (Tomcat 9) · HTML · CSS · JavaScript

No frameworks and no build tool: the project compiles with plain `javac`.

## Project layout
```
database/        MySQL schema and seed data
sample-data/     sample resumes for the demo
src/main/java/   com.resumematch: model, exception, util, dao, service, web, console
src/main/webapp/ JSP views, CSS and JavaScript
lib/             MySQL Connector/J jar (not committed)
resumes/         uploaded files at runtime (not committed)
```

Layers: servlet → service → DAO → MySQL. All SQL lives in the DAO classes and uses
`PreparedStatement`; JSP pages only render data prepared by the servlets.

## Team
| Member | Area |
|---|---|
| Barathkumar M P | Database, analysis engine, packaging |
| Gokul K | Models, exceptions, JDBC data access |
| Hemanth Suresh | Authentication and the page shell |
| Faseesh | Resume upload, analysis and score pages |
| Rithik | Job search, job detail and skill gap |
| Rishi | Saved jobs and application tracking |

Setup, build and deployment instructions are added as the project is completed.
