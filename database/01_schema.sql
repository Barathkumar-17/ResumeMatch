-- =====================================================================
-- ResumeMatch - database schema
-- Drops and recreates the whole database, so it is safe to run again.
--
-- Run:  mysql -u root -p < database/01_schema.sql
-- Then: mysql -u root -p < database/02_seed_data.sql
-- =====================================================================

DROP DATABASE IF EXISTS resumematch;
CREATE DATABASE resumematch
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE resumematch;

-- ---------------------------------------------------------------------
-- users: one row per registered student.
-- The UNIQUE index on email is what makes a second sign-up with the same
-- email fail (SQLIntegrityConstraintViolationException in JDBC).
-- password holds the SHA-256 hex of the password, never plain text.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- resumes: every uploaded .txt resume. file_path is where the file was
-- saved on disk, resume_text is the full text, score is out of 100.
-- ---------------------------------------------------------------------
CREATE TABLE resumes (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT          NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    file_path    VARCHAR(500) NOT NULL,
    resume_text  TEXT         NOT NULL,
    score        INT          NOT NULL DEFAULT 0,
    uploaded_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resumes_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- skills: the skill dictionary used for text matching.
-- aliases is a comma separated list of other spellings (e.g. "CPP").
-- ---------------------------------------------------------------------
CREATE TABLE skills (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    skill_name  VARCHAR(50)  NOT NULL,
    category    VARCHAR(30)  NOT NULL,
    aliases     VARCHAR(200) NULL,
    CONSTRAINT uq_skills_name UNIQUE (skill_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- resume_skills: which skills were found in which resume (many-to-many).
-- ---------------------------------------------------------------------
CREATE TABLE resume_skills (
    resume_id  INT NOT NULL,
    skill_id   INT NOT NULL,
    PRIMARY KEY (resume_id, skill_id),
    CONSTRAINT fk_resume_skills_resume
        FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE,
    CONSTRAINT fk_resume_skills_skill
        FOREIGN KEY (skill_id) REFERENCES skills(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- jobs: job and internship listings shown on the search page.
-- ---------------------------------------------------------------------
CREATE TABLE jobs (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(120) NOT NULL,
    company      VARCHAR(120) NOT NULL,
    location     VARCHAR(80),
    job_type     ENUM('Internship','Full-time','Part-time'),
    experience   VARCHAR(40),
    description  TEXT,
    salary       VARCHAR(50),
    posted_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- job_skills: skills each job asks for.
-- Only REQUIRED rows count towards the match score; PREFERRED rows are
-- shown as "nice to have" and on the skill gap page.
-- ---------------------------------------------------------------------
CREATE TABLE job_skills (
    job_id          INT NOT NULL,
    skill_id        INT NOT NULL,
    required_level  ENUM('REQUIRED','PREFERRED') NOT NULL DEFAULT 'REQUIRED',
    PRIMARY KEY (job_id, skill_id),
    CONSTRAINT fk_job_skills_job
        FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_skills_skill
        FOREIGN KEY (skill_id) REFERENCES skills(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- saved_jobs: jobs a student bookmarked but has not applied to yet.
-- ---------------------------------------------------------------------
CREATE TABLE saved_jobs (
    user_id   INT NOT NULL,
    job_id    INT NOT NULL,
    saved_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, job_id),
    CONSTRAINT fk_saved_jobs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_jobs_job
        FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- applications: jobs a student marked as applied, with a status.
-- One application per user per job.
-- ---------------------------------------------------------------------
CREATE TABLE applications (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    job_id      INT NOT NULL,
    status      ENUM('Applied','Interview','Rejected','Selected') NOT NULL DEFAULT 'Applied',
    applied_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_applications_user_job UNIQUE (user_id, job_id),
    CONSTRAINT fk_applications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_job
        FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
