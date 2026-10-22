-- =====================================================================
-- ResumeMatch - seed data
-- 18 skills, 8 jobs, 46 job_skills rows and one demo user.
-- Run after 01_schema.sql. Clears the seeded tables first, so it can be
-- run again on its own without duplicate key errors.
-- =====================================================================

USE resumematch;
SET NAMES utf8mb4;

-- Empty the tables in foreign-key-safe order (children first) and reset the AUTO_INCREMENT
-- counters so the skill ids below always come out as 1..18.
DELETE FROM applications;
DELETE FROM saved_jobs;
DELETE FROM resume_skills;
DELETE FROM resumes;
DELETE FROM job_skills;
DELETE FROM jobs;
DELETE FROM skills;
DELETE FROM users;
ALTER TABLE skills AUTO_INCREMENT = 1;
ALTER TABLE jobs AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 1;

-- ---------------------------------------------------------------------
-- Skill dictionary. Insert in this order so the ids stay stable.
-- aliases = other spellings that count as the same skill.
-- ---------------------------------------------------------------------
INSERT INTO skills (id, skill_name, category, aliases) VALUES
    (1,  'Java',        'Language',     NULL),
    (2,  'C++',         'Language',     'CPP'),
    (3,  'Python',      'Language',     NULL),
    (4,  'SQL',         'Database',     NULL),
    (5,  'MySQL',       'Database',     NULL),
    (6,  'JDBC',        'Database',     NULL),
    (7,  'Git',         'Tool',         'GitHub'),
    (8,  'DSA',         'Fundamentals', 'Data Structures and Algorithms,Data Structures'),
    (9,  'Spring Boot', 'Framework',    'SpringBoot'),
    (10, 'REST API',    'Web',          'REST APIs,RESTful'),
    (11, 'Docker',      'Tool',         NULL),
    (12, 'HTML',        'Web',          'HTML5'),
    (13, 'CSS',         'Web',          'CSS3'),
    (14, 'JavaScript',  'Web',          'JS'),
    (15, 'Flask',       'Framework',    NULL),
    (16, 'Linux',       'Tool',         NULL),
    (17, 'Excel',       'Data',         'MS Excel'),
    (18, 'Power BI',    'Data',         'PowerBI');

-- ---------------------------------------------------------------------
-- Jobs. posted_at is set a few days back so "posted N days ago" varies.
-- ---------------------------------------------------------------------
INSERT INTO jobs (id, title, company, location, job_type, experience, description, salary, posted_at) VALUES
    (101, 'Java Backend Intern', 'Kaveri Systems', 'Chennai', 'Internship', 'Fresher',
     'Work with the platform team on the order-management backend. You''ll write data-access code, fix bugs in existing services and help move modules to Spring Boot. Six-month internship with a possible full-time offer.',
     '₹20,000 / month', NOW() - INTERVAL 2 DAY),
    (102, 'Python Developer Intern', 'BlueOrbit Labs', 'Bangalore', 'Internship', 'Fresher',
     'Build internal tools and small web services in Python. You''ll write Flask endpoints, query a SQL database and automate reports for the operations team.',
     '₹18,000 / month', NOW() - INTERVAL 5 DAY),
    (103, 'Software Engineer Intern', 'Nimbus Softworks', 'Hyderabad', 'Internship', 'Fresher',
     'Join a product squad and ship features end to end. Strong problem-solving is expected; interviews include two data-structures rounds.',
     '₹30,000 / month', NOW() - INTERVAL 1 DAY),
    (104, 'Frontend Developer Intern', 'Pixelcraft Studio', 'Pune', 'Internship', 'Fresher',
     'Turn Figma designs into responsive pages for client websites. You''ll work closely with designers and review each other''s code.',
     '₹15,000 / month', NOW() - INTERVAL 3 DAY),
    (105, 'Data Analyst Intern', 'Datawise Analytics', 'Chennai', 'Internship', 'Fresher',
     'Clean sales data, write SQL queries and build weekly dashboards for retail clients. Good fit if you enjoy finding patterns in numbers.',
     '₹16,000 / month', NOW() - INTERVAL 6 DAY),
    (106, 'Junior Java Developer', 'Coromandel FinTech', 'Coimbatore', 'Full-time', '0-1 years',
     'Maintain payment reconciliation services written in Java. You''ll build REST endpoints, write MySQL queries and deploy with Docker.',
     '₹4.5 LPA', NOW() - INTERVAL 4 DAY),
    (107, 'Full Stack Intern', 'Stackline Technologies', 'Remote', 'Internship', 'Fresher',
     'Work on a student-facing web app across frontend and backend. Java on the server, plain JavaScript in the browser.',
     '₹12,000 / month', NOW() - INTERVAL 8 DAY),
    (108, 'Systems Programming Intern', 'Corelogic Embedded', 'Bangalore', 'Internship', 'Fresher',
     'Write and profile C++ modules that run on Linux-based network devices. Expect to reason about memory, performance and algorithms.',
     '₹25,000 / month', NOW() - INTERVAL 2 DAY);

-- ---------------------------------------------------------------------
-- Job skills. Skill ids (see list above):
--  1 Java   2 C++    3 Python  4 SQL   5 MySQL   6 JDBC   7 Git
--  8 DSA    9 Spring Boot  10 REST API  11 Docker  12 HTML  13 CSS
-- 14 JavaScript  15 Flask  16 Linux  17 Excel  18 Power BI
-- ---------------------------------------------------------------------
INSERT INTO job_skills (job_id, skill_id, required_level) VALUES
    -- 101 Java Backend Intern: Java, SQL, JDBC, Spring Boot, Git | REST API
    (101, 1,  'REQUIRED'),
    (101, 4,  'REQUIRED'),
    (101, 6,  'REQUIRED'),
    (101, 9,  'REQUIRED'),
    (101, 7,  'REQUIRED'),
    (101, 10, 'PREFERRED'),
    -- 102 Python Developer Intern: Python, Flask, SQL | Git, REST API
    (102, 3,  'REQUIRED'),
    (102, 15, 'REQUIRED'),
    (102, 4,  'REQUIRED'),
    (102, 7,  'PREFERRED'),
    (102, 10, 'PREFERRED'),
    -- 103 Software Engineer Intern: Java, DSA, Git, Docker | C++, Linux
    (103, 1,  'REQUIRED'),
    (103, 8,  'REQUIRED'),
    (103, 7,  'REQUIRED'),
    (103, 11, 'REQUIRED'),
    (103, 2,  'PREFERRED'),
    (103, 16, 'PREFERRED'),
    -- 104 Frontend Developer Intern: HTML, CSS, JavaScript, Git | REST API
    (104, 12, 'REQUIRED'),
    (104, 13, 'REQUIRED'),
    (104, 14, 'REQUIRED'),
    (104, 7,  'REQUIRED'),
    (104, 10, 'PREFERRED'),
    -- 105 Data Analyst Intern: Python, SQL, Excel, Power BI | Git
    (105, 3,  'REQUIRED'),
    (105, 4,  'REQUIRED'),
    (105, 17, 'REQUIRED'),
    (105, 18, 'REQUIRED'),
    (105, 7,  'PREFERRED'),
    -- 106 Junior Java Developer: Java, Spring Boot, REST API, SQL, Docker | MySQL, Git
    (106, 1,  'REQUIRED'),
    (106, 9,  'REQUIRED'),
    (106, 10, 'REQUIRED'),
    (106, 4,  'REQUIRED'),
    (106, 11, 'REQUIRED'),
    (106, 5,  'PREFERRED'),
    (106, 7,  'PREFERRED'),
    -- 107 Full Stack Intern: Java, JavaScript, HTML, CSS, SQL, REST API | Git
    (107, 1,  'REQUIRED'),
    (107, 14, 'REQUIRED'),
    (107, 12, 'REQUIRED'),
    (107, 13, 'REQUIRED'),
    (107, 4,  'REQUIRED'),
    (107, 10, 'REQUIRED'),
    (107, 7,  'PREFERRED'),
    -- 108 Systems Programming Intern: C++, DSA, Git, Linux | Python
    (108, 2,  'REQUIRED'),
    (108, 8,  'REQUIRED'),
    (108, 7,  'REQUIRED'),
    (108, 16, 'REQUIRED'),
    (108, 3,  'PREFERRED');

-- ---------------------------------------------------------------------
-- Demo account: demo@resumematch.in / demo1234
-- password column = PBKDF2 hash of "demo1234" (pbkdf2$iterations$salt$hash)
-- ---------------------------------------------------------------------
INSERT INTO users (id, name, email, password) VALUES
    (1, 'Arun Kumar', 'demo@resumematch.in',
     'pbkdf2$210000$A4xz70OwsDPn0SSGyca8gw==$CMC+nktYjhPFCpi0G2ObkPnmTCaexNZ2kSRuEdzif2I=');
