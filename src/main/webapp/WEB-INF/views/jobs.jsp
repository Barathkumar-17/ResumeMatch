<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"
         import="java.util.Collection, java.util.Set, com.resumematch.model.JobMatch" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Find jobs (URL /jobs). Rendered by JobSearchServlet. Display only: the servlet has
  already searched, scored, sorted and filtered the jobs. The small scriptlets below
  only loop over lists and pick which block to show.

  Request attributes:
    matches          (List<JobMatch>)  jobs to show, best match first when hasResume
    resultCount      (Integer)         matches.size()
    hasResume        (Boolean)         false: no scores, show the upload banner
    savedJobIds      (Set<Integer>)    jobs already on the user's list
    appliedJobIds    (Set<Integer>)    jobs the user has marked as applied
    q, location, type, exp (String)    the filters as submitted ("" when not set)
    minMatch         (Integer)         slider value 0-100
    locations, jobTypes, experienceLevels (Set<String>)  dropdown options
    error            (String)          message for the red alert strip
    flash, flashError (String)         one-time messages after Save / Remove
--%>
<%
    Collection<?> matches = (Collection<?>) request.getAttribute("matches");
    Set<?> savedJobIds = (Set<?>) request.getAttribute("savedJobIds");
    Set<?> appliedJobIds = (Set<?>) request.getAttribute("appliedJobIds");
    boolean hasResume = Boolean.TRUE.equals(request.getAttribute("hasResume"));
%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="Find jobs"/>
</jsp:include>
<body>
<div class="app">
  <jsp:include page="/WEB-INF/views/_sidebar.jsp"/>
  <main class="main" id="main" tabindex="-1">
    <div class="page-head">
      <div>
        <h1>Find jobs</h1>
        <p>${requestScope.hasResume ? 'Jobs are ranked by how many of their required skills appear in your resume.' : 'Browse all openings. Analyze your resume to see match scores.'}</p>
      </div>
    </div>

<% if (!hasResume) { %>
    <div class="banner"><span>Match scores appear once your resume is analyzed.</span><a class="btn btn-primary btn-sm" href="${pageContext.request.contextPath}/dashboard">Upload resume</a></div>
<% } %>

    <form method="get" action="${pageContext.request.contextPath}/jobs">
      <div class="search-bar">
        <input name="q" type="search" placeholder="Search jobs, skills or companies" aria-label="Search jobs, skills or companies" value="${rm:h(requestScope.q)}">
        <button class="btn btn-primary" type="submit">Search</button>
      </div>
      <div class="filters">
        <div>
          <label for="f-location">Location</label>
          <select id="f-location" name="location" data-autosubmit>
            <option value="">All</option>
<% for (Object option : (Collection<?>) request.getAttribute("locations")) { pageContext.setAttribute("option", option); %>
            <option value="${rm:h(option)}" ${option == requestScope.location ? 'selected' : ''}>${rm:h(option)}</option>
<% } %>
          </select>
        </div>
        <div>
          <label for="f-type">Job type</label>
          <select id="f-type" name="type" data-autosubmit>
            <option value="">All</option>
<% for (Object option : (Collection<?>) request.getAttribute("jobTypes")) { pageContext.setAttribute("option", option); %>
            <option value="${rm:h(option)}" ${option == requestScope.type ? 'selected' : ''}>${rm:h(option)}</option>
<% } %>
          </select>
        </div>
        <div>
          <label for="f-exp">Experience level</label>
          <select id="f-exp" name="exp" data-autosubmit>
            <option value="">All</option>
<% for (Object option : (Collection<?>) request.getAttribute("experienceLevels")) { pageContext.setAttribute("option", option); %>
            <option value="${rm:h(option)}" ${option == requestScope.exp ? 'selected' : ''}>${rm:h(option)}</option>
<% } %>
          </select>
        </div>
        <div>
          <label for="f-min">Minimum match: <span class="range-val" id="min-val">${requestScope.minMatch}%</span></label>
          <input type="range" id="f-min" name="minMatch" min="0" max="100" step="10" value="${requestScope.minMatch}"
                 data-range-label="min-val" data-autosubmit ${requestScope.hasResume ? '' : 'disabled aria-describedby="min-hint"'}>
          <span class="sr-only" id="min-hint" ${requestScope.hasResume ? 'hidden' : ''}>Available once your resume is analyzed.</span>
        </div>
      </div>
    </form>

    <div class="alert bad mb" role="alert">${rm:h(requestScope.error)}</div>

    <div class="result-meta">
      <span role="status">${requestScope.resultCount} job${requestScope.resultCount == 1 ? '' : 's'}${empty requestScope.q ? '' : ' for &quot;'}${rm:h(requestScope.q)}${empty requestScope.q ? '' : '&quot;'}${requestScope.hasResume ? ', best match first' : ''}</span>
      <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/jobs">Clear filters</a>
    </div>

    <div class="jobs">
<% for (Object item : matches) {
       JobMatch m = (JobMatch) item;
       pageContext.setAttribute("m", m);
       pageContext.setAttribute("saved", savedJobIds.contains(m.getJob().getId()));
       pageContext.setAttribute("applied", appliedJobIds.contains(m.getJob().getId()));
       pageContext.setAttribute("missingText", String.join(", ", m.getMissing()));
       pageContext.setAttribute("missingPreferredText", String.join(", ", m.getMissingPreferred()));
%>
      <article class="job">
        <div class="job-top">
          <div><h2>${rm:h(m.job.title)}</h2><div class="co">${rm:h(m.job.company)}</div></div>
          <div class="pct-badge ${not requestScope.hasResume ? 'pct-na' : ('pct-' += m.level)}"><b>${requestScope.hasResume ? m.score : '&mdash;'}${requestScope.hasResume ? '%' : ''}</b><span>match</span></div>
        </div>
        <div class="meta">
          <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>${rm:h(m.job.location)}</span>
          <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/></svg>${rm:h(m.job.jobType)}</span>
          <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3 2 8l10 5 10-5z"/><path d="M6 10.5V16c3 2 9 2 12 0v-5.5"/></svg>${rm:h(m.job.experience)}</span>
        </div>
        <div>
          <div class="req-label">Required</div>
          <div class="tags">
<%     for (String skill : m.getJob().getRequiredSkills()) { pageContext.setAttribute("skill", skill); %>
            <span class="tag ${not requestScope.hasResume ? 'plain' : m.matched.contains(skill) ? 'ok' : 'no'}">${not requestScope.hasResume ? '' : m.matched.contains(skill) ? '&#10003; ' : '&#10007; '}${rm:h(skill)}</span>
<%     } %>
          </div>
        </div>
<%     if (hasResume && (!m.getMissing().isEmpty() || !m.getMissingPreferred().isEmpty())) { %>
        <div class="small muted">Missing: ${empty missingText ? 'none required' : rm:h(missingText)}${empty missingPreferredText ? '' : '; preferred: '}${rm:h(missingPreferredText)}</div>
<%     } %>
        <div class="job-actions">
          <a class="btn btn-primary btn-sm" href="${pageContext.request.contextPath}/job?id=${m.job.id}" aria-label="View details for ${rm:h(m.job.title)}, ${rm:h(m.job.company)}">View details</a>
          <span class="btn btn-ghost btn-sm is-on" role="img" aria-label="Applied: ${rm:h(m.job.title)}" ${applied ? '' : 'hidden'}>Applied</span>
          <form class="inline" method="post" action="${pageContext.request.contextPath}/save-job" ${applied ? 'hidden' : ''}>
            <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
            <input type="hidden" name="jobId" value="${m.job.id}">
            <input type="hidden" name="action" value="${saved ? 'remove' : 'save'}">
            <button class="btn btn-ghost btn-sm ${saved ? 'is-on' : ''}" type="submit" aria-label="Save job: ${rm:h(m.job.title)}" aria-pressed="${saved}">${saved ? 'Saved' : 'Save'}</button>
          </form>
        </div>
      </article>
<% } %>
<% if (matches.isEmpty() && request.getAttribute("error") == null) { %>
      <div class="panel empty full">
        <h2>No jobs match these filters</h2>
        <p>Lower the minimum match or clear the search to see more.</p>
        <a class="btn btn-ghost" href="${pageContext.request.contextPath}/jobs">Clear filters</a>
      </div>
<% } %>
    </div>
  </main>
</div>
<div class="toasts">
  <div class="toast good" role="status">${rm:h(requestScope.flash)}</div>
  <div class="toast bad" role="alert">${rm:h(requestScope.flashError)}</div>
</div>
</body>
</html>
