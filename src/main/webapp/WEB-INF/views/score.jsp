<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Resume score page (URL /score). Rendered by ScoreServlet. Display only, no logic:
  the small scriptlets below only choose a block or walk a list the servlet prepared,
  and every value is printed with EL.

  Request attributes:
    activePage  (String)                "score"
    error       (String, optional)      friendly message when the score could not be loaded
    resume      (Resume, optional)      latest resume; missing means nothing uploaded yet
    score       (Integer)               total out of 100
    ringClass   (String)                hi | mid | lo - colour of the ring
    verdict     (String)                one-line summary next to the ring
    breakdown   (List<ScoreBreakdown>)  the five rule rows (label, points, maxPoints, percent, reason)
    strengths   (List<String>)
    weaknesses  (List<String>)
--%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="Resume score"/>
</jsp:include>
<body>
<div class="app">
  <jsp:include page="/WEB-INF/views/_sidebar.jsp"/>
  <main class="main" id="main" tabindex="-1">
<% if (request.getAttribute("error") != null) { %>
    <div class="page-head"><div><h1>Resume score</h1></div></div>
    <div class="alert bad" role="alert">${rm:h(requestScope.error)}</div>
<% } else if (request.getAttribute("resume") == null) { %>
    <div class="page-head"><div><h1>Resume score</h1></div></div>
    <div class="panel empty">
      <h3>No resume analyzed yet</h3>
      <p>Upload a .txt resume on the dashboard to see your score.</p>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/dashboard">Upload resume</a>
    </div>
<% } else { %>
    <div class="page-head">
      <div><h1>Resume score</h1><p>Five sections, each with a fixed rule. Improve a section and re-upload to see the score change.</p></div>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/skill-gap">Check skill gap</a>
    </div>
    <div class="grid g-side">
      <div class="panel">
        <div class="ring-head">
          <div class="ring ${rm:h(requestScope.ringClass)}" data-p="${requestScope.score}" role="img" aria-label="${requestScope.score} out of 100"><span>${requestScope.score}<small>/100</small></span></div>
          <div><h2>Resume score: ${requestScope.score}/100</h2>
            <p class="muted">${rm:h(requestScope.verdict)}</p></div>
        </div>
<%   for (Object row : (java.util.List<?>) request.getAttribute("breakdown")) {
       pageContext.setAttribute("row", row); %>
        <div class="bar-row">
          <div class="bar-top"><b>${rm:h(row.label)}</b><span>${row.points} / ${row.maxPoints}</span></div>
          <div class="bar"><i data-w="${row.percent}"></i></div>
          <div class="bar-why">${rm:h(row.reason)}</div>
        </div>
<%   } %>
      </div>
      <div class="grid stack">
        <div class="panel"><h2>Strengths</h2>
          <ul class="checklist good">
<%   for (Object item : (java.util.List<?>) request.getAttribute("strengths")) {
       pageContext.setAttribute("item", item); %>
            <li>${rm:h(item)}</li>
<%   } %>
            ${empty requestScope.strengths ? '<li>Add more sections to find strengths.</li>' : ''}
          </ul>
        </div>
        <div class="panel"><h2>Areas to improve</h2>
          <ul class="checklist bad">
<%   for (Object item : (java.util.List<?>) request.getAttribute("weaknesses")) {
       pageContext.setAttribute("item", item); %>
            <li>${rm:h(item)}</li>
<%   } %>
            ${empty requestScope.weaknesses ? '<li>Nothing major. Keep your resume updated.</li>' : ''}
          </ul>
        </div>
      </div>
    </div>
<% } %>
  </main>
</div>
</body>
</html>
