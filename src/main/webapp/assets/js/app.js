/*
  ResumeMatch client-side behaviour.

  Only things that do not need the server live here:
    1. Login / register tab switch
    2. Client-side form validation with per-field messages
    3. Drag-and-drop resume selection and the upload progress steps
    4. Minimum-match slider live label
    5. Filters and dropdowns that submit their form on change
    6. Client-side tabs, confirm dialogs and auto-hiding flash messages

  All analysis, scoring and matching happens in Java on the server.
  Every feature is opt-in through a class or data-* attribute, so this one
  file can be loaded on every page.
*/
(function () {
  "use strict";

  var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  var MAX_UPLOAD_BYTES = 200 * 1024;
  var reduceMotion = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);

  function byId(id) { return document.getElementById(id); }
  function all(selector, root) {
    var found = (root || document).querySelectorAll(selector), list = [];
    for (var i = 0; i < found.length; i++) { list.push(found[i]); }
    return list;
  }

  /* ------------------------------------------------------------------
     1 + 2. Auth tabs and form validation
     ------------------------------------------------------------------ */

  /** Shows or clears the error under one field. Returns true when the field is valid. */
  function fieldErr(id, msg) {
    var input = byId(id), err = byId(id + "-err");
    if (err) { err.textContent = msg || ""; }
    if (input) { input.classList.toggle("err", !!msg); }
    return !msg;
  }

  function clearAuthMessages() {
    all(".auth-box .ferr").forEach(function (e) { e.textContent = ""; });
    all(".auth-box .input").forEach(function (e) { e.classList.remove("err"); });
    all(".auth-box .alert").forEach(function (e) { e.textContent = ""; });
  }

  function switchAuth(tab) {
    var loginTab = byId("tab-login"), registerTab = byId("tab-register");
    var loginForm = byId("form-login"), registerForm = byId("form-register");
    if (!loginTab || !registerTab || !loginForm || !registerForm) { return; }
    loginTab.classList.toggle("active", tab === "login");
    registerTab.classList.toggle("active", tab === "register");
    loginTab.setAttribute("aria-selected", tab === "login" ? "true" : "false");
    registerTab.setAttribute("aria-selected", tab === "register" ? "true" : "false");
    loginForm.hidden = tab !== "login";
    registerForm.hidden = tab !== "register";
    clearAuthMessages();
  }

  all("[data-auth-tab]").forEach(function (link) {
    link.addEventListener("click", function (e) {
      e.preventDefault();
      var tab = link.getAttribute("data-auth-tab");
      switchAuth(tab);
      // Keep the address bar in step so a refresh opens the same tab.
      if (window.history && window.history.replaceState && link.href) {
        window.history.replaceState(null, "", link.href);
      }
    });
  });

  function emailMessage(email) {
    if (!email) { return "Enter your email."; }
    if (!EMAIL_RE.test(email)) { return "Enter a valid email, like name@college.edu."; }
    return "";
  }

  function validateLogin() {
    var email = byId("li-email").value.trim(), pass = byId("li-pass").value;
    var ok = fieldErr("li-email", emailMessage(email));
    ok = fieldErr("li-pass", !pass ? "Enter your password." : "") && ok;
    return ok;
  }

  function validateRegister() {
    var name = byId("rg-name").value.trim(), email = byId("rg-email").value.trim();
    var pass = byId("rg-pass").value, conf = byId("rg-conf").value;
    var ok = fieldErr("rg-name", !name ? "Enter your full name." : "");
    ok = fieldErr("rg-email", emailMessage(email)) && ok;
    ok = fieldErr("rg-pass", !pass ? "Create a password." : pass.length < 6 ? "Use at least 6 characters." : "") && ok;
    ok = fieldErr("rg-conf", !conf ? "Re-enter your password." : conf !== pass ? "Passwords don't match." : "") && ok;
    return ok;
  }

  var VALIDATORS = { login: validateLogin, register: validateRegister };

  all("form[data-validate]").forEach(function (form) {
    var check = VALIDATORS[form.getAttribute("data-validate")];
    if (!check) { return; }
    form.addEventListener("submit", function (e) {
      all(".alert", form).forEach(function (a) { a.textContent = ""; });
      if (!check()) {
        e.preventDefault();
        var firstBad = form.querySelector(".input.err");
        if (firstBad) { firstBad.focus(); }
      }
    });
    // Clear a field's message as soon as the user starts fixing it.
    all(".input", form).forEach(function (input) {
      input.addEventListener("input", function () {
        if (input.classList.contains("err")) { fieldErr(input.id, ""); }
      });
    });
  });

  /* ------------------------------------------------------------------
     3. Resume upload: choose or drop a file, check it, show progress
        Markup: <form data-upload enctype="multipart/form-data"> holding
          [data-drop]        the dashed drop area
          input[type=file]   the real file field (can be hidden)
          [data-drop-title]  heading that changes with the state
          [data-file-chip]   shows the chosen file name and size
          [data-file-err]    message when the file is rejected
          [data-idle]        shown before a file is chosen
          [data-ready]       shown once a valid file is chosen
          [data-pick-file]   button that opens the file dialog
          [data-clear-file]  button that goes back to the idle state
          [data-pipeline]    <ul class="pipeline"> of steps shown on submit
     ------------------------------------------------------------------ */

  function uploadProblem(file) {
    if (!file) { return "Choose a resume file first."; }
    if (!/\.txt$/i.test(file.name)) {
      return "\"" + file.name + "\" isn't a .txt file. This version reads plain-text resumes only; PDF support comes later.";
    }
    if (file.size === 0) { return "This file is empty. Choose a resume that contains text."; }
    if (file.size > MAX_UPLOAD_BYTES) { return "This file is larger than 200 KB. Resumes are usually under 20 KB of text."; }
    return "";
  }

  function setupUpload(form) {
    var drop = form.querySelector("[data-drop]") || form;
    var input = form.querySelector("input[type=file]");
    if (!input) { return; }
    var title = form.querySelector("[data-drop-title]");
    var chip = form.querySelector("[data-file-chip]");
    var err = form.querySelector("[data-file-err]");
    var idleParts = all("[data-idle]", form);
    var readyParts = all("[data-ready]", form);
    var pipeline = form.querySelector("[data-pipeline]");
    var idleTitle = title ? title.textContent : "";
    var busy = false;

    function showIdle(message) {
      if (title) { title.textContent = idleTitle; }
      if (chip) { chip.hidden = true; chip.textContent = ""; }
      idleParts.forEach(function (b) { b.hidden = false; });
      readyParts.forEach(function (b) { b.hidden = true; });
      if (err) { err.textContent = message || ""; }
    }

    function showReady(file) {
      if (title) { title.textContent = "Ready to analyze"; }
      if (chip) {
        chip.textContent = "";
        chip.appendChild(document.createTextNode("\uD83D\uDCC4 " + file.name + " "));
        var size = document.createElement("span");
        size.className = "muted small";
        size.textContent = (file.size / 1024).toFixed(1) + " KB";
        chip.appendChild(size);
        chip.hidden = false;
      }
      idleParts.forEach(function (b) { b.hidden = true; });
      readyParts.forEach(function (b) { b.hidden = false; });
      if (err) { err.textContent = ""; }
    }

    function accept(files) {
      var file = files && files[0];
      var problem = uploadProblem(file);
      if (problem) { input.value = ""; showIdle(problem); return; }
      showReady(file);
    }

    all("[data-pick-file]", form).forEach(function (b) {
      b.addEventListener("click", function (e) { e.preventDefault(); input.click(); });
    });
    all("[data-clear-file]", form).forEach(function (b) {
      b.addEventListener("click", function (e) { e.preventDefault(); input.value = ""; showIdle(""); });
    });
    input.addEventListener("change", function () { accept(input.files); });

    ["dragenter", "dragover"].forEach(function (type) {
      drop.addEventListener(type, function (e) { e.preventDefault(); if (!busy) { drop.classList.add("over"); } });
    });
    ["dragleave", "drop"].forEach(function (type) {
      drop.addEventListener(type, function (e) { e.preventDefault(); drop.classList.remove("over"); });
    });
    drop.addEventListener("drop", function (e) {
      if (busy || !e.dataTransfer) { return; }
      var files = e.dataTransfer.files;
      var problem = uploadProblem(files && files[0]);
      if (problem) { input.value = ""; showIdle(problem); return; }
      try { input.files = files; } catch (ignored) { /* very old browsers: fall back to Choose file */ }
      accept(input.files && input.files.length ? input.files : files);
    });

    form.addEventListener("submit", function (e) {
      var problem = uploadProblem(input.files && input.files[0]);
      if (problem) { e.preventDefault(); showIdle(problem); return; }
      if (busy) { e.preventDefault(); return; }
      busy = true;
      // The server does the real work; these steps only show that it is running.
      if (title) { title.textContent = "Analyzing " + input.files[0].name; }
      if (chip) { chip.hidden = true; }
      readyParts.forEach(function (b) { b.hidden = true; });
      idleParts.forEach(function (b) { b.hidden = true; });
      if (pipeline) {
        pipeline.hidden = false;
        var steps = all("li", pipeline), i = 0;
        var tick = function () {
          if (i > 0) { steps[i - 1].className = "done"; }
          if (i < steps.length) { steps[i].className = "now"; i++; setTimeout(tick, reduceMotion ? 0 : 320); }
        };
        tick();
      }
    });

    // Keep any message the server rendered (e.g. "This file is empty.").
    showIdle(err ? err.textContent : "");
  }

  all("form[data-upload]").forEach(setupUpload);

  /* ------------------------------------------------------------------
     4. Range slider live label, e.g. "Minimum match: 60%"
     ------------------------------------------------------------------ */
  all("input[type=range][data-range-label]").forEach(function (range) {
    var label = byId(range.getAttribute("data-range-label"));
    var update = function () { if (label) { label.textContent = range.value + "%"; } };
    range.addEventListener("input", update);
    update();
  });

  /* ------------------------------------------------------------------
     5. Submit the surrounding form when a filter or dropdown changes
     ------------------------------------------------------------------ */
  function submitForm(form) {
    if (!form) { return; }
    if (typeof form.requestSubmit === "function") { form.requestSubmit(); } else { form.submit(); }
  }
  all("[data-autosubmit]").forEach(function (el) {
    el.addEventListener("change", function () { submitForm(el.form); });
  });

  /* ------------------------------------------------------------------
     6a. Client-side tabs: <button data-tab="saved"> shows [data-tab-panel="saved"]
     ------------------------------------------------------------------ */
  all("[data-tabs]").forEach(function (group) {
    var buttons = all("[data-tab]", group);
    buttons.forEach(function (btn) {
      btn.addEventListener("click", function (e) {
        e.preventDefault();
        var name = btn.getAttribute("data-tab");
        buttons.forEach(function (b) { b.classList.toggle("active", b === btn); });
        all("[data-tab-panel]").forEach(function (panel) {
          panel.hidden = panel.getAttribute("data-tab-panel") !== name;
        });
      });
    });
  });

  /* 6b. Ask before destructive actions: data-confirm="Remove this job?" */
  all("[data-confirm]").forEach(function (el) {
    var event = el.tagName === "FORM" ? "submit" : "click";
    el.addEventListener(event, function (e) {
      if (!window.confirm(el.getAttribute("data-confirm"))) { e.preventDefault(); }
    });
  });

  /* 6c. Flash messages rendered by the server fade out on their own */
  all(".toast").forEach(function (t) {
    if (!t.textContent.trim()) { t.parentNode.removeChild(t); return; }
    setTimeout(function () { if (t.parentNode) { t.parentNode.removeChild(t); } }, 4200);
  });
})();
