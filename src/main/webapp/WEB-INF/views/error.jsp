<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%--
  Friendly error page for 404, 500 and any uncaught exception (see web.xml).
  It never prints the exception, the stack trace or any SQL message.
--%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="${requestScope['javax.servlet.error.status_code'] == 404 ? 'Page not found' : 'Something went wrong'}"/>
</jsp:include>
<body>
<div class="error-page">
  <a class="logo" href="${pageContext.request.contextPath}/"><span class="logo-mark"></span><span>ResumeMatch</span></a>
  <div class="panel empty">
    <div class="error-code">${requestScope['javax.servlet.error.status_code']}</div>
    <h3>${requestScope['javax.servlet.error.status_code'] == 404 ? 'Page not found' : 'Something went wrong'}</h3>
    <p>${requestScope['javax.servlet.error.status_code'] == 404 ? "The page you asked for doesn't exist or has moved." : "We couldn't finish that request. Please try again in a moment."}</p>
    <div class="btn-row">
      <a class="btn btn-ghost" href="${pageContext.request.contextPath}/">Back to home</a>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/dashboard">Go to dashboard</a>
    </div>
  </div>
</div>
</body>
</html>
