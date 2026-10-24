<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Resume analysis (URL /analysis). Rendered by AnalysisServlet. Display only, no logic.

  Request attributes:
    activePage       (String)                     "analysis"
    resume           (Resume, null if none yet)   fileName, filePath; null shows the empty state
    uploadedDate     (String)                     e.g. "22 Oct 2026"
    skillCount       (Integer)                    number of skills found
    skillGroups      (Map<String,List<String>>)   category -> skill names, dictionary order
    skillsLine       (String)                     all skill names joined with ", "
    dictionarySize   (Integer)                    number of skills in the dictionary
    info             (ResumeInfo)                 name, contact, education, certifications
    projectTitles    (List<String>)               project names (text before the first ':')
    experienceTitles (List<String>)               experience entries (text before the first ':')
    highlightedText  (String)                     resume text as HTML with skills in <mark>.
                                                  Printed WITHOUT rm:h on purpose: the servlet
                                                  already escaped every character of the resume
                                                  text with HtmlUtil.escape and only added the
                                                  fixed <mark></mark> tags.
    message          (String, optional)           success toast after an upload
    error            (String, optional)           friendly error, e.g. database down

  Lists are printed with EL collection operations (stream().map(...).reduce(...)),
  because the project has no tag library jar. Every text value inside them goes
  through rm:h(...) before it is joined into the page.
--%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="Resume analysis"/>
</jsp:include>
<body>
<div class="app">
  <jsp:include page="/WEB-INF/views/_sidebar.jsp"/>
  <main class="main" id="main" tabindex="-1">
    <div class="page-head">
      <div>
        <h1>Resume analysis</h1>
        <p ${empty requestScope.resume ? 'hidden' : ''}>${rm:h(requestScope.resume.fileName)}, analyzed ${rm:h(requestScope.uploadedDate)}.</p>
      </div>
      <div class="btn-row" ${empty requestScope.resume ? 'hidden' : ''}>
        <a class="btn btn-ghost" href="${pageContext.request.contextPath}/resume-file">Download resume</a>
        <a class="btn btn-primary" href="${pageContext.request.contextPath}/score">View score</a>
      </div>
    </div>

    <div class="alert bad mb" role="alert">${rm:h(requestScope.error)}</div>

    <div class="panel empty" ${empty requestScope.resume ? '' : 'hidden'}>
      <h2>No resume analyzed yet</h2>
      <p>Upload a resume on the dashboard to see extracted skills and sections.</p>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/dashboard">Upload resume</a>
    </div>

    <div ${empty requestScope.resume ? 'hidden' : ''}>
      <div class="panel mb">
        <h2>${requestScope.skillCount} skills found</h2>
        <div class="cat-grid" ${empty requestScope.skillGroups ? 'hidden' : ''}>
          ${requestScope.skillGroups.entrySet().stream().map(group ->
              '<div><div class="cat-label">' += rm:h(group.key) += '</div><div class="tags">'
              += group.value.stream().map(skill -> '<span class="tag hl">' += rm:h(skill) += '</span>').reduce('', (a, b) -> a += b)
              += '</div></div>'
            ).reduce('', (a, b) -> a += b)}
        </div>
        <p class="muted" ${empty requestScope.skillGroups ? '' : 'hidden'}>No skills from the dictionary were found. Check that your skills are spelled out, for example "Java" or "SQL".</p>
        <p class="panel-foot">Found by searching the text for each of the ${requestScope.dictionarySize} skills in the dictionary. A skill counts once, however often it appears.</p>
      </div>

      <div class="grid g2">
        <div class="panel">
          <h2>Resume overview</h2>
          <dl class="kv">
            <dt>Candidate</dt>
            <dd><b>${rm:h(requestScope.info.name)}</b><div class="small muted">${rm:h(requestScope.info.contact)}</div></dd>
            <dt>Education</dt>
            <dd>${empty requestScope.info.education ? '<span class="muted">Not found</span>'
                : '<ul>' += requestScope.info.education.stream().map(item -> '<li>' += rm:h(item) += '</li>').reduce('', (a, b) -> a += b) += '</ul>'}</dd>
            <dt>Skills</dt>
            <dd>${empty requestScope.skillsLine ? 'None found' : rm:h(requestScope.skillsLine)}</dd>
            <dt>Projects</dt>
            <dd>${empty requestScope.projectTitles ? '<span class="muted">Not found</span>'
                : '<ul>' += requestScope.projectTitles.stream().map(item -> '<li>' += rm:h(item) += '</li>').reduce('', (a, b) -> a += b) += '</ul>'}</dd>
            <dt>Certifications</dt>
            <dd>${empty requestScope.info.certifications ? '<span class="muted">Not found</span>'
                : '<ul>' += requestScope.info.certifications.stream().map(item -> '<li>' += rm:h(item) += '</li>').reduce('', (a, b) -> a += b) += '</ul>'}</dd>
            <dt>Experience</dt>
            <dd>${empty requestScope.experienceTitles ? '<span class="muted">Not found</span>'
                : '<ul>' += requestScope.experienceTitles.stream().map(item -> '<li>' += rm:h(item) += '</li>').reduce('', (a, b) -> a += b) += '</ul>'}</dd>
          </dl>
        </div>
        <div class="panel">
          <h2>Resume text</h2>
          <p class="panel-note">Highlighted words matched the skill dictionary.</p>
          <div class="doc" role="region" aria-label="Resume text" tabindex="0">${requestScope.highlightedText}</div>
        </div>
      </div>
    </div>
  </main>
</div>
<div class="toasts"><div class="toast good" role="status">${rm:h(requestScope.message)}</div></div>
</body>
</html>
