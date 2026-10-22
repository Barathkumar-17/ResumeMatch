<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"
         import="java.util.Collection, java.util.Map, com.resumematch.model.Job" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Skill gap (URL /skill-gap?job=101). Rendered by SkillGapServlet. Display only: the
  percentage, the four skill lists and the recommendations come from the servlet. The
  small scriptlets below only loop over lists and pick which block to show.

  Request attributes:
    hasResume        (Boolean)       false: show the "No resume analyzed yet" panel
    jobs             (List<Job>)     options for the target job dropdown
    targetJob        (Job)           the job being compared
    gapScore         (Integer)       required + preferred skills the resume has, in %
    haveCount, totalCount (Integer)  e.g. 4 of 6
    matchScore       (Integer)       the job match score (required skills only)
    matchedRequired, matchedPreferred, missingRequired, missingPreferred (List<String>)
    recommendations  (List<Map.Entry<String,Integer>>)  skill -> number of jobs needing it
    totalJobs        (Integer)       number of jobs the recommendations were counted over
    error            (String)        message for the red alert strip
--%>
<%
    boolean hasResume = Boolean.TRUE.equals(request.getAttribute("hasResume"));
    Job targetJob = (Job) request.getAttribute("targetJob");
%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="Skill gap"/>
</jsp:include>
<body>
<div class="app">
  <jsp:include page="/WEB-INF/views/_sidebar.jsp"/>
  <main class="main" id="main" tabindex="-1">
<% if (!hasResume || targetJob == null) { %>
    <div class="page-head"><div><h1>Skill gap</h1></div></div>
    <div class="alert bad mb" role="alert">${rm:h(requestScope.error)}</div>
<%     if (!hasResume && request.getAttribute("error") == null) { %>
    <div class="panel empty">
      <h2>No resume analyzed yet</h2>
      <p>Upload a .txt resume on the dashboard to see which skills you're missing.</p>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/dashboard">Upload resume</a>
    </div>
<%     } %>
<% } else { %>
    <div class="page-head">
      <div>
        <h1>Skill gap</h1>
        <p>Compare your skills with everything a job asks for, including preferred skills.</p>
      </div>
      <form class="target-pick" method="get" action="${pageContext.request.contextPath}/skill-gap">
        <label for="gap-job">Target job</label>
        <select id="gap-job" name="job" class="input" data-autosubmit>
<%     for (Object option : (Collection<?>) request.getAttribute("jobs")) { pageContext.setAttribute("option", option); %>
          <option value="${option.id}" ${option.id == targetJob.id ? 'selected' : ''}>${rm:h(option.title)}, ${rm:h(option.company)}</option>
<%     } %>
        </select>
        <noscript><button class="btn btn-ghost btn-sm" type="submit">Show</button></noscript>
      </form>
    </div>
    <div class="alert bad mb" role="alert">${rm:h(requestScope.error)}</div>

    <div class="grid g-side">
      <div class="panel">
        <div class="ring-head">
          <div class="ring ${gapScore >= 75 ? 'hi' : gapScore >= 50 ? 'mid' : 'lo'}" data-p="${gapScore}" role="img" aria-label="${gapScore} percent skill match"><span>${gapScore}<small>%</small></span></div>
          <div>
            <h2>Skill match: ${gapScore}%</h2>
            <p class="muted">You have ${haveCount} of the ${totalCount} skills listed for ${rm:h(targetJob.title)}.</p>
            <p class="small muted">The job match score (${matchScore}%) counts required skills only.</p>
          </div>
        </div>
        <div class="gap-cols">
          <div>
            <h3>Matched skills</h3>
            <ul class="gap-list ok">
<%     for (Object skill : (Collection<?>) request.getAttribute("matchedRequired")) { pageContext.setAttribute("skill", skill); %>
              <li>&#10003; ${rm:h(skill)}<small>Required</small></li>
<%     } %>
<%     for (Object skill : (Collection<?>) request.getAttribute("matchedPreferred")) { pageContext.setAttribute("skill", skill); %>
              <li>&#10003; ${rm:h(skill)}<small>Preferred</small></li>
<%     } %>
<%     if (Integer.valueOf(0).equals(request.getAttribute("haveCount"))) { %>
              <li>None yet</li>
<%     } %>
            </ul>
          </div>
          <div>
            <h3>Missing skills</h3>
            <ul class="gap-list no">
<%     for (Object skill : (Collection<?>) request.getAttribute("missingRequired")) { pageContext.setAttribute("skill", skill); %>
              <li>&#10007; ${rm:h(skill)}<small>Required</small></li>
<%     } %>
<%     for (Object skill : (Collection<?>) request.getAttribute("missingPreferred")) { pageContext.setAttribute("skill", skill); %>
              <li>&#10007; ${rm:h(skill)}<small>Preferred</small></li>
<%     } %>
<%     if (request.getAttribute("haveCount").equals(request.getAttribute("totalCount"))) { %>
              <li class="none">Nothing missing</li>
<%     } %>
            </ul>
          </div>
        </div>
      </div>

      <div class="panel">
        <h2>Recommended skills to learn</h2>
        <p class="panel-note">Required skills you're missing across all ${totalJobs} jobs. Skills needed by jobs you nearly match come first.</p>
<%     Collection<?> recommendations = (Collection<?>) request.getAttribute("recommendations");
       for (Object item : recommendations) {
           Map.Entry<?, ?> rec = (Map.Entry<?, ?>) item;
           pageContext.setAttribute("recSkill", rec.getKey());
           pageContext.setAttribute("recJobs", rec.getValue());
%>
        <div class="learn-row">
          <b class="lname">${rm:h(recSkill)}</b>
          <div class="bar"><i class="amber" data-w="${recJobs * 100 / totalJobs}"></i></div>
          <span class="lcount">${recJobs} job${recJobs == 1 ? '' : 's'}</span>
        </div>
<%     } %>
<%     if (recommendations.isEmpty()) { %>
        <p class="muted">You meet the required skills for every job.</p>
<%     } %>
      </div>
    </div>
<% } %>
  </main>
</div>
</body>
</html>
