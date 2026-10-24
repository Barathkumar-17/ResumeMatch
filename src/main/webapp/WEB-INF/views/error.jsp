<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%--
  Friendly error page for 404, 500 and any uncaught exception (see web.xml).
  It never prints the exception, the stack trace or any SQL message.
--%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="${requestScope['javax.servlet.error.status_code'] == 404 ? 'Page not found' : requestScope['javax.servlet.error.status_code'] == 403 ? 'Form expired' : requestScope['javax.servlet.error.status_code'] == 413 ? 'Upload too large' : requestScope['javax.servlet.error.status_code'] == 405 || requestScope['javax.servlet.error.status_code'] == 400 ? 'Request not accepted' : 'Something went wrong'}"/>
</jsp:include>
<body>
<main class="error-page">
  <a class="logo" href="${pageContext.request.contextPath}/"><span class="logo-mark"></span><span>ResumeMatch</span></a>
  <div class="panel empty">
    <div class="error-code" aria-hidden="true">${requestScope['javax.servlet.error.status_code']}</div>
    <h1>${requestScope['javax.servlet.error.status_code'] == 404 ? 'Page not found' : requestScope['javax.servlet.error.status_code'] == 403 ? 'Form expired' : requestScope['javax.servlet.error.status_code'] == 413 ? 'Upload too large' : requestScope['javax.servlet.error.status_code'] == 405 || requestScope['javax.servlet.error.status_code'] == 400 ? 'Request not accepted' : 'Something went wrong'}</h1>
    <p>${requestScope['javax.servlet.error.status_code'] == 404 ? "The page you asked for doesn't exist or has moved." : requestScope['javax.servlet.error.status_code'] == 403 ? "This form expired. Go back, reload the page and try again." : requestScope['javax.servlet.error.status_code'] == 413 ? "That upload is too large. A .txt resume can be up to 200 KB; a .pdf or .docx up to 2 MB." : requestScope['javax.servlet.error.status_code'] == 405 || requestScope['javax.servlet.error.status_code'] == 400 ? "That request couldn't be understood. Go back to the dashboard and use the buttons on the page." : "We couldn't finish that request. Please try again in a moment."}</p>
    <div class="btn-row">
      <a class="btn btn-ghost" href="${pageContext.request.contextPath}/">Back to home</a>
      <a class="btn btn-primary" href="${pageContext.request.contextPath}/dashboard">Go to dashboard</a>
    </div>
  </div>
</main>
</body>
</html>
