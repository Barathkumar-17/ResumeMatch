<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%--
  Landing page (URL "/"). Static content only.
  "Analyze resume" and "Find jobs" go to protected pages; the AuthFilter
  sends visitors who are not logged in to /login first.
  Reads session attribute "user" only to swap the top-right buttons.
--%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value=""/>
</jsp:include>
<body>
<div class="landing">
  <header class="l-nav">
    <a class="logo" href="${pageContext.request.contextPath}/"><span class="logo-mark"></span><span>ResumeMatch</span></a>
    <div class="actions">
      <a class="btn btn-ghost" href="${pageContext.request.contextPath}/login" ${empty sessionScope.user ? '' : 'hidden'}>Log in</a>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/register" ${empty sessionScope.user ? '' : 'hidden'}>Create account</a>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/dashboard" ${empty sessionScope.user ? 'hidden' : ''}>Go to dashboard</a>
    </div>
  </header>

  <section class="hero">
    <div>
      <h1>Find out which jobs your resume already fits.</h1>
      <p class="lead">Upload your resume as a text file. ResumeMatch picks out your skills, scores each section out of 100, and ranks internships by how many of their required skills you already have.</p>
      <div class="cta">
        <a class="btn btn-hl" href="${pageContext.request.contextPath}/dashboard">Analyze resume</a>
        <a class="btn btn-ghost" href="${pageContext.request.contextPath}/jobs">Find jobs</a>
      </div>
      <p class="note">Rule-based analysis: skills are found by matching your resume text against a fixed skill dictionary. No AI or machine learning is involved.</p>
    </div>
    <div class="sheet-wrap" aria-hidden="true">
      <div class="sheet">
        <div class="nm">Arun Kumar</div>
        <div class="small deg">B.E. Computer Science, Anna University</div>
        <div class="sh">Skills</div>
        <mark style="animation-delay:.3s">Java</mark>, <mark style="animation-delay:.45s">C++</mark>, <mark style="animation-delay:.6s">Python</mark>, <mark style="animation-delay:.75s">SQL</mark>, <mark style="animation-delay:.9s">JDBC</mark>, <mark style="animation-delay:1.05s">Git</mark>, <mark style="animation-delay:1.2s">DSA</mark>
        <div class="sh">Projects</div>
        Library Management System built in <mark style="animation-delay:1.4s">Java</mark> with <mark style="animation-delay:1.55s">JDBC</mark> and <mark style="animation-delay:1.7s">SQL</mark> to manage books and members.
      </div>
      <div class="float-match">
        <div class="t">Java Backend Intern</div>
        <div class="s">Kaveri Systems, Chennai</div>
        <div class="pct">80% match</div>
        <div class="s last">4 of 5 required skills. Missing: Spring Boot</div>
      </div>
    </div>
  </section>

  <section class="how">
    <div class="how-inner">
      <h2>How the analysis works</h2>
      <p>Every step is plain, explainable logic, built with Java file handling, collections and JDBC.</p>
      <div class="steps">
        <div class="step"><h3>Upload</h3><p>Your .txt resume is saved to a resume folder and its path is recorded in the database.</p></div>
        <div class="step"><h3>Extract skills</h3><p>The text is scanned for 18 known skills, such as Java, SQL, JDBC and Git, grouped by category.</p></div>
        <div class="step"><h3>Score</h3><p>Skills, projects, education, certifications and experience earn points toward a score out of 100.</p></div>
        <div class="step"><h3>Match and rank</h3><p>Each job is compared with your skill set, then sorted from best to weakest match.</p></div>
      </div>
      <div class="formula">
        <b>Match score</b>
        <code>matched required skills ÷ total required skills × 100</code>
        <span class="muted">4 of 5 required skills = 80% match</span>
      </div>
    </div>
  </section>
  <footer class="l-foot">ResumeMatch · College project · Core Java, OOP, JDBC, MySQL, File Handling, Collections</footer>
</div>
</body>
</html>
