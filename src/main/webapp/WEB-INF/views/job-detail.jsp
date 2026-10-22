<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"
         import="java.util.Collection, com.resumematch.model.Job, com.resumematch.model.JobMatch" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Job detail (URL /job?id=101). Rendered by JobDetailServlet. Display only: the match
  and every number in the worked calculation come from the servlet. The small
  scriptlets below only loop over skill lists and pick which block to show.

  Request attributes:
    job            (Job)            null when the id was missing or unknown
    match          (JobMatch)       null when the student has no resume yet
    hasResume      (Boolean)
    matchedCount, requiredCount, missingCount, score (Integer)  set when match is set
    saved          (Boolean)        job is on the student's list (saved or applied)
    applied        (Boolean)        job has been marked as applied
    savedJobIds    (Set<Integer>)
    error          (String)         e.g. "This job no longer exists."
    flash, flashError (String)      one-time messages after Save / Remove
--%>
<%
    Job job = (Job) request.getAttribute("job");
    JobMatch match = (JobMatch) request.getAttribute("match");
%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="${empty requestScope.job ? 'Job not found' : requestScope.job.title}"/>
</jsp:include>
<body>
<div class="app">
  <jsp:include page="/WEB-INF/views/_sidebar.jsp"/>
  <main class="main" id="main" tabindex="-1">
<% if (job == null) { %>
    <div class="panel empty">
      <h3>Job not found</h3>
      <p>${empty requestScope.error ? 'It may have been removed.' : rm:h(requestScope.error)}</p>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/jobs">Back to jobs</a>
    </div>
<% } else { %>
    <a class="back-link" href="${pageContext.request.contextPath}/jobs">&larr; Back to jobs</a>
    <div class="alert bad mb" role="alert">${rm:h(requestScope.error)}</div>

    <div class="panel mb">
      <div class="detail-head">
        <div>
          <h1>${rm:h(job.title)}</h1>
          <p class="co">${rm:h(job.company)}</p>
          <div class="meta spaced">
            <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>${rm:h(job.location)}</span>
            <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/></svg>${rm:h(job.jobType)}</span>
            <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3 2 8l10 5 10-5z"/><path d="M6 10.5V16c3 2 9 2 12 0v-5.5"/></svg>${rm:h(job.experience)}</span>
          </div>
          <p class="salary">${rm:h(job.salary)}</p>
        </div>
<%     if (match != null) { %>
        <div class="ring-label">
          <div class="ring ${score >= 75 ? 'hi' : score >= 50 ? 'mid' : 'lo'}" data-p="${score}" role="img" aria-label="${score} percent match"><span>${score}<small>%</small></span></div>
          <div class="small muted">match</div>
        </div>
<%     } %>
      </div>
      <div class="detail-actions">
        <form class="inline" method="post" action="${pageContext.request.contextPath}/save-job">
          <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
          <input type="hidden" name="jobId" value="${job.id}">
          <input type="hidden" name="action" value="${saved ? 'remove' : 'save'}">
          <button class="btn btn-ghost ${saved ? 'is-on' : ''}" type="submit">${saved ? 'Saved' : 'Save job'}</button>
        </form>
        <form class="inline" method="post" action="${pageContext.request.contextPath}/application">
          <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
          <input type="hidden" name="jobId" value="${job.id}">
          <input type="hidden" name="action" value="apply">
          <button class="btn btn-primary" type="submit" ${applied ? 'disabled' : ''}>${applied ? 'Applied' : 'Mark as applied'}</button>
        </form>
      </div>
    </div>

    <div class="grid g-side">
      <div class="panel">
        <h2>About the role</h2>
        <p class="about">${rm:h(job.description)}</p>
        <h2 class="mt">Required skills</h2>
        <div class="tags">
<%     for (String skill : job.getRequiredSkills()) { pageContext.setAttribute("skill", skill); %>
          <span class="tag ${empty match ? 'plain' : match.matched.contains(skill) ? 'ok' : 'no'}">${empty match ? '' : match.matched.contains(skill) ? '&#10003; ' : '&#10007; '}${rm:h(skill)}</span>
<%     } %>
        </div>
        <h2 class="mt">Preferred skills</h2>
        <div class="tags">
<%     for (String skill : job.getPreferredSkills()) { pageContext.setAttribute("skill", skill); %>
          <span class="tag ${empty match ? 'plain' : match.missingPreferred.contains(skill) ? 'no' : 'ok'}">${empty match ? '' : match.missingPreferred.contains(skill) ? '&#10007; ' : '&#10003; '}${rm:h(skill)}</span>
<%     } %>
        </div>
      </div>

      <div class="panel">
        <h2>How your resume compares</h2>
<%     if (match != null) { %>
        <div class="skill-compare">
          <div class="skill-col">
            <h3>Matching skills (${matchedCount})</h3>
<%         if (match.getMatched().isEmpty()) { %>
            <p class="small muted">None</p>
<%         } else { %>
            <div class="tags">
<%             for (String skill : match.getMatched()) { pageContext.setAttribute("skill", skill); %>
              <span class="tag ok">&#10003; ${rm:h(skill)}</span>
<%             } %>
            </div>
<%         } %>
          </div>
          <div class="skill-col">
            <h3>Missing skills (${missingCount})</h3>
<%         if (match.getMissing().isEmpty() && match.getMissingPreferred().isEmpty()) { %>
            <p class="small muted">None</p>
<%         } else { %>
            <div class="tags">
<%             for (String skill : match.getMissing()) { pageContext.setAttribute("skill", skill); %>
              <span class="tag no">&#10007; ${rm:h(skill)}</span>
<%             } %>
<%             for (String skill : match.getMissingPreferred()) { pageContext.setAttribute("skill", skill); %>
              <span class="tag warn">&#10007; ${rm:h(skill)} (preferred)</span>
<%             } %>
            </div>
<%         } %>
          </div>
        </div>
        <div class="calc">matched required = <b>${matchedCount}</b><br>total required = <b>${requiredCount}</b><br>${matchedCount} &divide; ${requiredCount} &times; 100 = <b>${score}% match</b></div>
<%     } else { %>
        <p class="muted">Analyze your resume to see which of these skills you already have.</p>
        <a class="btn btn-primary mt-btn" href="${pageContext.request.contextPath}/dashboard">Upload resume</a>
<%     } %>
      </div>
    </div>
<% } %>
  </main>
</div>
<div class="toasts" aria-live="polite">
  <div class="toast good">${rm:h(requestScope.flash)}</div>
  <div class="toast bad">${rm:h(requestScope.flashError)}</div>
</div>
</body>
</html>
