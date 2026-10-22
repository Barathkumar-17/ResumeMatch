<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  My jobs: status counts, Saved / Applied tabs and a table for each.
  Rendered by MyJobsServlet. Display only: every value is prepared by the servlet.

  Request attributes:
    savedRows, appliedRows  (List<MyJobsServlet.Row>)  job, status, dateText, matchText, matchClass
    savedCount, appliedCount (Integer)                 numbers shown on the tabs
    statusCounts (Map<String,Integer>)                 Saved, Applied, Interview, Rejected, Selected
    tab          (String)                              "saved" or "applied" - the open tab
    loadFailed   (Boolean)                             true when the lists could not be loaded
    flash, error (String, optional)                    one-shot messages shown as toasts
    activePage, myJobsCount                            read by the sidebar

  Forms post to /application with jobId and action = apply | status | remove.
  The two loops below only repeat markup for each row; they contain no logic.
--%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="My jobs"/>
</jsp:include>
<body>
<div class="app">
  <jsp:include page="/WEB-INF/views/_sidebar.jsp"/>
  <main class="main">
    <div class="page-head"><div><h1>My jobs</h1><p>Track every job from saved to selected. Change the status as you hear back.</p></div></div>

    <div class="panel empty" ${requestScope.loadFailed ? '' : 'hidden'}>
      <h3>Couldn't load your jobs</h3>
      <p>${rm:h(requestScope.error)}</p>
    </div>

    <div ${requestScope.loadFailed ? 'hidden' : ''}>
      <div class="pipeline-strip">
        <div><b>${requestScope.statusCounts['Saved']}</b><span class="status st-Saved">Saved</span></div>
        <div><b>${requestScope.statusCounts['Applied']}</b><span class="status st-Applied">Applied</span></div>
        <div><b>${requestScope.statusCounts['Interview']}</b><span class="status st-Interview">Interview</span></div>
        <div><b>${requestScope.statusCounts['Rejected']}</b><span class="status st-Rejected">Rejected</span></div>
        <div><b>${requestScope.statusCounts['Selected']}</b><span class="status st-Selected">Selected</span></div>
      </div>

      <div class="tabs" role="tablist" data-tabs>
        <a href="${pageContext.request.contextPath}/my-jobs?tab=saved" role="tab" data-tab="saved"
           class="${requestScope.tab == 'applied' ? '' : 'active'}">Saved jobs (${requestScope.savedCount})</a>
        <a href="${pageContext.request.contextPath}/my-jobs?tab=applied" role="tab" data-tab="applied"
           class="${requestScope.tab == 'applied' ? 'active' : ''}">Applied jobs (${requestScope.appliedCount})</a>
      </div>

      <%-- Saved tab --%>
      <div class="panel" data-tab-panel="saved" ${requestScope.tab == 'applied' ? 'hidden' : ''}>
        <div class="tbl-wrap" ${requestScope.savedCount > 0 ? '' : 'hidden'}>
          <table class="tbl">
            <thead><tr><th>Position</th><th>Company</th><th>Match</th><th>Status</th><th>Date saved</th><th></th></tr></thead>
            <tbody>
<% for (Object item : (java.util.List<?>) request.getAttribute("savedRows")) { pageContext.setAttribute("row", item); %>
              <tr>
                <td><a class="job-link" href="${pageContext.request.contextPath}/job?id=${row.job.id}">${rm:h(row.job.title)}</a></td>
                <td>${rm:h(row.job.company)}</td>
                <td><span class="pct-badge inline ${row.matchClass}"><b>${row.matchText}</b></span></td>
                <td><span class="status st-Saved">Saved</span></td>
                <td>${rm:h(row.dateText)}</td>
                <td class="actions">
                  <form method="post" action="${pageContext.request.contextPath}/application">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="jobId" value="${row.job.id}">
                    <input type="hidden" name="tab" value="saved">
                    <button class="btn btn-primary btn-sm" type="submit" name="action" value="apply">Mark as applied</button>
                    <button class="btn btn-ghost btn-sm" type="submit" name="action" value="remove"
                            data-confirm="Remove ${rm:h(row.job.title)} from My jobs?">Remove</button>
                  </form>
                </td>
              </tr>
<% } %>
            </tbody>
          </table>
        </div>
        <div class="empty" ${requestScope.savedCount > 0 ? 'hidden' : ''}>
          <h3>No saved jobs</h3>
          <p>Save jobs from the search page to compare them later.</p>
          <a class="btn btn-primary" href="${pageContext.request.contextPath}/jobs">Find jobs</a>
        </div>
      </div>

      <%-- Applied tab --%>
      <div class="panel" data-tab-panel="applied" ${requestScope.tab == 'applied' ? '' : 'hidden'}>
        <div class="tbl-wrap" ${requestScope.appliedCount > 0 ? '' : 'hidden'}>
          <table class="tbl">
            <thead><tr><th>Position</th><th>Company</th><th>Match</th><th>Status</th><th>Date updated</th><th></th></tr></thead>
            <tbody>
<% for (Object item : (java.util.List<?>) request.getAttribute("appliedRows")) { pageContext.setAttribute("row", item); %>
              <tr>
                <td><a class="job-link" href="${pageContext.request.contextPath}/job?id=${row.job.id}">${rm:h(row.job.title)}</a></td>
                <td>${rm:h(row.job.company)}</td>
                <td><span class="pct-badge inline ${row.matchClass}"><b>${row.matchText}</b></span></td>
                <td>
                  <form method="post" action="${pageContext.request.contextPath}/application">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="action" value="status">
                    <input type="hidden" name="jobId" value="${row.job.id}">
                    <select name="status" data-autosubmit aria-label="Status for ${rm:h(row.job.title)}">
                      <option value="Applied" ${row.status == 'Applied' ? 'selected' : ''}>Applied</option>
                      <option value="Interview" ${row.status == 'Interview' ? 'selected' : ''}>Interview</option>
                      <option value="Rejected" ${row.status == 'Rejected' ? 'selected' : ''}>Rejected</option>
                      <option value="Selected" ${row.status == 'Selected' ? 'selected' : ''}>Selected</option>
                    </select>
                    <noscript><button class="btn btn-ghost btn-sm" type="submit">Update</button></noscript>
                  </form>
                </td>
                <td>${rm:h(row.dateText)}</td>
                <td class="actions">
                  <form method="post" action="${pageContext.request.contextPath}/application">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="jobId" value="${row.job.id}">
                    <input type="hidden" name="tab" value="applied">
                    <button class="btn btn-ghost btn-sm" type="submit" name="action" value="remove"
                            data-confirm="Remove ${rm:h(row.job.title)} from My jobs?">Remove</button>
                  </form>
                </td>
              </tr>
<% } %>
            </tbody>
          </table>
        </div>
        <div class="empty" ${requestScope.appliedCount > 0 ? 'hidden' : ''}>
          <h3>No applications yet</h3>
          <p>Mark a saved job as applied to start tracking it.</p>
          <a class="btn btn-primary" href="${pageContext.request.contextPath}/jobs">Find jobs</a>
        </div>
      </div>
    </div>
  </main>
</div>

<div class="toasts" aria-live="polite">
  <div class="toast good" role="status">${rm:h(requestScope.flash)}</div>
  <div class="toast bad" role="alert">${rm:h(requestScope.loadFailed ? '' : requestScope.error)}</div>
</div>
</body>
</html>
