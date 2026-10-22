<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="rm" uri="http://resumematch/functions" %>
<%--
  Login and register on one page (URLs /login and /register).
  Rendered by LoginServlet and RegisterServlet. Display only, no logic.

  Request attributes:
    mode    (String)               "login" or "register" - which tab is open (default login)
    errors  (Map<String,String>)   per-field messages, keyed by input name:
                                   login:    email, password
                                   register: name, email, password, confirmPassword
    error   (String)               message for the red alert strip, e.g. wrong password
    message (String, optional)     message for the green alert strip, e.g. "Logged out."
  Submitted name and email are refilled from the request parameters.
  Passwords are never refilled.
--%>
<jsp:include page="/WEB-INF/views/_head.jsp">
  <jsp:param name="title" value="${requestScope.mode == 'register' ? 'Create account' : 'Log in'}"/>
</jsp:include>
<body>
<div class="auth">
  <aside class="auth-side">
    <a class="logo" href="${pageContext.request.contextPath}/"><span class="logo-mark"></span><span>ResumeMatch</span></a>
    <blockquote>Know your <span>match score</span> before you apply.</blockquote>
    <div class="demo">Demo account<br><b>demo@resumematch.in</b> / <b>demo1234</b></div>
  </aside>
  <main class="auth-main">
    <div class="auth-box">
      <a class="back-link" href="${pageContext.request.contextPath}/">&larr; Back to home</a>
      <div class="auth-tabs" role="tablist" aria-label="Log in or register">
        <a id="tab-login" href="${pageContext.request.contextPath}/login" role="tab" aria-controls="form-login" data-auth-tab="login"
           class="${requestScope.mode == 'register' ? '' : 'active'}" aria-selected="${requestScope.mode != 'register'}">Log in</a>
        <a id="tab-register" href="${pageContext.request.contextPath}/register" role="tab" aria-controls="form-register" data-auth-tab="register"
           class="${requestScope.mode == 'register' ? 'active' : ''}" aria-selected="${requestScope.mode == 'register'}">Register</a>
      </div>

      <form id="form-login" role="tabpanel" aria-labelledby="tab-login" method="post" action="${pageContext.request.contextPath}/login" data-validate="login" novalidate ${requestScope.mode == 'register' ? 'hidden' : ''}>
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <h2>Welcome back</h2>
        <p class="muted">Log in to see your resume score and matched jobs.</p>
        <div class="field">
          <label for="li-email">Email</label>
          <input class="input ${requestScope.mode != 'register' and not empty requestScope.errors.email ? 'err' : ''}" id="li-email" aria-describedby="li-email-err" name="email" type="email" autocomplete="email"
                 value="${rm:h(requestScope.mode == 'register' ? '' : param.email)}">
          <div class="ferr" id="li-email-err">${rm:h(requestScope.mode == 'register' ? '' : requestScope.errors.email)}</div>
        </div>
        <div class="field">
          <label for="li-pass">Password</label>
          <input class="input ${requestScope.mode != 'register' and not empty requestScope.errors.password ? 'err' : ''}" id="li-pass" aria-describedby="li-pass-err" name="password" type="password" autocomplete="current-password">
          <div class="ferr" id="li-pass-err">${rm:h(requestScope.mode == 'register' ? '' : requestScope.errors.password)}</div>
        </div>
        <div class="alert bad" id="li-alert" role="alert">${rm:h(requestScope.mode == 'register' ? '' : requestScope.error)}</div>
        <div class="alert good" role="status">${rm:h(requestScope.mode == 'register' ? '' : requestScope.message)}</div>
        <button class="btn btn-primary" type="submit">Log in</button>
        <p class="small muted auth-hint">Try the demo account: demo@resumematch.in / demo1234</p>
      </form>

      <form id="form-register" role="tabpanel" aria-labelledby="tab-register" method="post" action="${pageContext.request.contextPath}/register" data-validate="register" novalidate ${requestScope.mode == 'register' ? '' : 'hidden'}>
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <h2>Create your account</h2>
        <p class="muted">You'll need an account to save jobs and track applications.</p>
        <div class="field">
          <label for="rg-name">Full name</label>
          <input class="input ${requestScope.mode == 'register' and not empty requestScope.errors.name ? 'err' : ''}" id="rg-name" aria-describedby="rg-name-err" name="name" autocomplete="name"
                 value="${rm:h(requestScope.mode == 'register' ? param.name : '')}">
          <div class="ferr" id="rg-name-err">${rm:h(requestScope.mode == 'register' ? requestScope.errors.name : '')}</div>
        </div>
        <div class="field">
          <label for="rg-email">Email</label>
          <input class="input ${requestScope.mode == 'register' and not empty requestScope.errors.email ? 'err' : ''}" id="rg-email" aria-describedby="rg-email-err" name="email" type="email" autocomplete="email"
                 value="${rm:h(requestScope.mode == 'register' ? param.email : '')}">
          <div class="ferr" id="rg-email-err">${rm:h(requestScope.mode == 'register' ? requestScope.errors.email : '')}</div>
        </div>
        <div class="field">
          <label for="rg-pass">Password</label>
          <input class="input ${requestScope.mode == 'register' and not empty requestScope.errors.password ? 'err' : ''}" id="rg-pass" aria-describedby="rg-pass-err" name="password" type="password" autocomplete="new-password">
          <div class="ferr" id="rg-pass-err">${rm:h(requestScope.mode == 'register' ? requestScope.errors.password : '')}</div>
        </div>
        <div class="field">
          <label for="rg-conf">Confirm password</label>
          <input class="input ${requestScope.mode == 'register' and not empty requestScope.errors.confirmPassword ? 'err' : ''}" id="rg-conf" aria-describedby="rg-conf-err" name="confirmPassword" type="password" autocomplete="new-password">
          <div class="ferr" id="rg-conf-err">${rm:h(requestScope.mode == 'register' ? requestScope.errors.confirmPassword : '')}</div>
        </div>
        <div class="alert bad" id="rg-alert" role="alert">${rm:h(requestScope.mode == 'register' ? requestScope.error : '')}</div>
        <div class="alert good" role="status">${rm:h(requestScope.mode == 'register' ? requestScope.message : '')}</div>
        <button class="btn btn-primary" type="submit">Create account</button>
      </form>
    </div>
  </main>
</div>
</body>
</html>
