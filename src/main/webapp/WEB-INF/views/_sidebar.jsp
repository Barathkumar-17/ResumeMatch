<%@ page pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Shared left sidebar for every logged-in page (becomes a top bar on phones).
  Use inside <div class="app">, before <main class="main">:

    <jsp:include page="/WEB-INF/views/_sidebar.jsp"/>

  Reads:
    request attribute "activePage"  (String)  dashboard | analysis | score | skill-gap | jobs | my-jobs
    request attribute "myJobsCount" (Integer, optional) number shown next to My jobs
    session attribute "user"        (User)    getName(), getEmail()
--%>
<aside class="side">
  <a class="skip" href="#main">Skip to main content</a>
  <a class="logo" href="${pageContext.request.contextPath}/dashboard"><span class="logo-mark"></span><span>ResumeMatch</span></a>
  <nav class="nav" aria-label="Main">
    <a href="${pageContext.request.contextPath}/dashboard" class="${requestScope.activePage == 'dashboard' ? 'active' : ''}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>Dashboard</a>
    <a href="${pageContext.request.contextPath}/analysis" class="${requestScope.activePage == 'analysis' ? 'active' : ''}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><path d="M14 3v6h6M8 13h8M8 17h5"/></svg>Resume analysis</a>
    <a href="${pageContext.request.contextPath}/score" class="${requestScope.activePage == 'score' ? 'active' : ''}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>Resume score</a>
    <a href="${pageContext.request.contextPath}/skill-gap" class="${requestScope.activePage == 'skill-gap' ? 'active' : ''}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 20V10M10 20V4M16 20v-6M22 20H2"/></svg>Skill gap</a>
    <a href="${pageContext.request.contextPath}/jobs" class="${requestScope.activePage == 'jobs' ? 'active' : ''}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>Find jobs</a>
    <a href="${pageContext.request.contextPath}/my-jobs" class="${requestScope.activePage == 'my-jobs' ? 'active' : ''}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>My jobs<span class="count">${requestScope.myJobsCount}</span></a>
  </nav>
  <div class="me">
    <div class="avatar" aria-hidden="true">${empty sessionScope.user.name ? '?' : sessionScope.user.name.substring(0, 1).toUpperCase()}</div>
    <div class="who" title="${rm:h(sessionScope.user.name)} (${rm:h(sessionScope.user.email)})"><b>${rm:h(sessionScope.user.name)}</b><span>${rm:h(sessionScope.user.email)}</span></div>
    <form class="inline" method="post" action="${pageContext.request.contextPath}/logout"><input type="hidden" name="_csrf" value="${sessionScope.csrfToken}"><button class="icon-btn" type="submit" title="Log out" aria-label="Log out"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/></svg></button></form>
  </div>
</aside>
