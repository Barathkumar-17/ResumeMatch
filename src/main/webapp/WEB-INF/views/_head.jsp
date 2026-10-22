<%@ page pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Shared page head: doctype, meta tags, fonts, stylesheet and script.
  Include it at the very top of every page, then open <body> yourself:

    <jsp:include page="/WEB-INF/views/_head.jsp">
        <jsp:param name="title" value="Dashboard"/>
    </jsp:include>
    <body>

  Always pass the title param (an empty value gives the full product name).
--%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<script>
  (function () {
    var theme = null;
    try { theme = localStorage.getItem("rm-theme"); } catch (e) { }
    if (theme !== "dark" && theme !== "light") {
      theme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    }
    document.documentElement.setAttribute("data-theme", theme);
  })();
</script>
<title>${rm:h(param.title)}${empty param.title ? 'ResumeMatch — Smart Resume Analyzer &amp; Job Recommendation System' : ' · ResumeMatch'}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,400;12..96,500;12..96,600;12..96,700;12..96,800&amp;family=Source+Serif+4:opsz,wght@8..60,400;8..60,600&amp;display=swap">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
<script src="${pageContext.request.contextPath}/assets/js/app.js" defer></script>
</head>
