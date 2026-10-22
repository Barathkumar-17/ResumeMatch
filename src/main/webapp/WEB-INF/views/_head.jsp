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
<script src="${pageContext.request.contextPath}/assets/js/theme-init.js"></script>
<title>${rm:h(param.title)}${empty param.title ? 'ResumeMatch — Smart Resume Analyzer &amp; Job Recommendation System' : ' · ResumeMatch'}</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
<script src="${pageContext.request.contextPath}/assets/js/app.js" defer></script>
</head>
