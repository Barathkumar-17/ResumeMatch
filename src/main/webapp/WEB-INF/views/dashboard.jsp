<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Dashboard (URL /dashboard). Rendered by DashboardServlet, and by ResumeUploadServlet
  when an upload is rejected. Display only, no logic.

  Request attributes:
    activePage     (String)                    "dashboard"
    firstName      (String)                    shown as "Hi, <firstName>"
    resume         (Resume, null if none yet)  the latest resume: score
    skillCount     (Integer)                   skills found in the latest resume
    goodMatchCount (Integer)                   jobs at 50% or more for the latest resume
    jobCount       (Integer)                   all open jobs
    savedCount     (Integer)                   saved plus applied jobs
    recentAnalyses (List<Map>)                 up to 4 rows, newest first, keys:
                                               fileName, date, skillCount, score,
                                               ringClass (hi|mid|lo), latest (Boolean)
    topMatches     (List<JobMatch>)            up to 3 best matches
    error          (String, optional)          why the upload was rejected

  Lists are printed with EL collection operations (stream().map(...).reduce(...)),
  because the project has no tag library jar. Every text value inside them goes
  through rm:h(...) before it is joined into the page.
--%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="Dashboard"/>
</jsp:include>
<body>
<div class="app">
  <jsp:include page="/WEB-INF/views/_sidebar.jsp"/>
  <main class="main">
    <div class="page-head">
      <div>
        <h1>Hi, ${rm:h(requestScope.firstName)}</h1>
        <p ${empty requestScope.resume ? '' : 'hidden'}>Upload your resume to get a score and see which jobs you match.</p>
        <p ${empty requestScope.resume ? 'hidden' : ''}>Your latest resume scored ${requestScope.resume.score}/100 and fits ${requestScope.goodMatchCount} of ${requestScope.jobCount} open jobs at 50% or more.</p>
      </div>
      <a class="btn btn-ghost" href="${pageContext.request.contextPath}/jobs" ${empty requestScope.resume ? 'hidden' : ''}>See matched jobs</a>
    </div>

    <div class="stats">
      <div class="stat"><div class="v">${empty requestScope.resume ? '—' : requestScope.resume.score}<small>${empty requestScope.resume ? '' : '/100'}</small></div><div class="k">Resume score</div></div>
      <div class="stat"><div class="v">${empty requestScope.resume ? '—' : requestScope.skillCount}</div><div class="k">Skills extracted</div></div>
      <div class="stat"><div class="v">${empty requestScope.resume ? '—' : requestScope.goodMatchCount}</div><div class="k">Jobs at 50%+ match</div></div>
      <div class="stat"><div class="v">${requestScope.savedCount}</div><div class="k">Saved or applied jobs</div></div>
    </div>

    <div class="grid g-side">
      <div class="panel">
        <h2>${empty requestScope.resume ? 'Upload your resume' : 'Upload a new version'}</h2>
        <form method="post" action="${pageContext.request.contextPath}/upload" enctype="multipart/form-data" data-upload novalidate>
          <div class="drop" data-drop>
            <div class="drop-icon"></div>
            <h3 data-drop-title>Drop your resume here</h3>
            <p class="muted" data-idle>Plain text (.txt) files up to 200 KB. Use headings like EDUCATION, SKILLS and PROJECTS.</p>
            <div class="file-chip" data-file-chip hidden></div>
            <input type="file" name="resume" accept=".txt,text/plain" hidden>
            <div class="row" data-idle>
              <button class="btn btn-primary" type="button" data-pick-file>Choose file</button>
            </div>
            <div class="row" data-ready hidden>
              <button class="btn btn-primary" type="submit">Analyze resume</button>
              <button class="btn btn-ghost" type="button" data-clear-file>Choose another file</button>
            </div>
            <ul class="pipeline" data-pipeline hidden>
              <li><span class="dot"></span>Saving file to the resumes folder</li>
              <li><span class="dot"></span>Reading the text</li>
              <li><span class="dot"></span>Matching against the skill dictionary</li>
              <li><span class="dot"></span>Calculating the resume score</li>
              <li><span class="dot"></span>Ranking jobs by match</li>
            </ul>
            <div class="ferr" data-file-err role="alert">${rm:h(requestScope.error)}</div>
          </div>
        </form>
      </div>

      <div class="grid stack">
        <div class="panel">
          <h2>Recent analysis</h2>
          ${requestScope.recentAnalyses.stream().map(r ->
              '<div class="list-row">'
              += '<div class="ring sm ' += r.ringClass += '" data-p="' += r.score += '" role="img" aria-label="' += r.score += ' percent"><span>' += r.score += '%</span></div>'
              += '<div class="grow"><b>' += rm:h(r.fileName) += '</b><span class="small muted">' += rm:h(r.date) += ', ' += r.skillCount += ' skills</span></div>'
              += (r.latest ? '<a class="btn btn-ghost btn-sm" href="' += pageContext.request.contextPath += '/analysis">Open</a>' : '')
              += '</div>'
            ).reduce('', (a, b) -> a += b)}
          <p class="muted small" ${empty requestScope.recentAnalyses ? '' : 'hidden'}>Your analyses will appear here.</p>
        </div>

        <div class="panel" ${empty requestScope.topMatches ? 'hidden' : ''}>
          <h2>Best matches</h2>
          ${requestScope.topMatches.stream().map(m ->
              '<div class="list-row">'
              += '<div class="grow"><b>' += rm:h(m.job.title) += '</b><span class="small muted">' += rm:h(m.job.company) += ', ' += rm:h(m.job.location) += '</span></div>'
              += '<a class="btn btn-ghost btn-sm" href="' += pageContext.request.contextPath += '/job?id=' += m.job.id += '">' += m.score += '%</a>'
              += '</div>'
            ).reduce('', (a, b) -> a += b)}
        </div>
      </div>
    </div>
  </main>
</div>
</body>
</html>
