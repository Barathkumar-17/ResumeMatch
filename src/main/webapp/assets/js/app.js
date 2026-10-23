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
  var MAX_TXT_BYTES = 200 * 1024;
  var MAX_DOC_BYTES = 2 * 1024 * 1024;
  var reduceMotion = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);

  function byId(id) { return document.getElementById(id); }
  function all(selector, root) {
    var found = (root || document).querySelectorAll(selector), list = [];
    for (var i = 0; i < found.length; i++) { list.push(found[i]); }
    return list;
  }

  /* ------------------------------------------------------------------
     0. Values the server prints as data-* attributes. They are applied
        through the CSSOM so the pages need no inline style="" attributes.
          data-p="78"  score ring  -> --p custom property
          data-w="80"  bar fill    -> width in percent
     ------------------------------------------------------------------ */
  function applyData() {
    all("[data-p]").forEach(function (el) {
      var p = parseFloat(el.getAttribute("data-p"));
      el.style.setProperty("--p", isNaN(p) ? "0" : String(Math.max(0, Math.min(100, p))));
    });
    var bars = all("[data-w]");
    var fill = function () {
      bars.forEach(function (el) {
        var w = parseFloat(el.getAttribute("data-w"));
        el.style.width = (isNaN(w) ? 0 : Math.max(0, Math.min(100, w))) + "%";
      });
    };
    if (reduceMotion || !window.requestAnimationFrame) { fill(); } else { window.requestAnimationFrame(fill); }
  }
  applyData();

  /* ------------------------------------------------------------------
     1 + 2. Auth tabs and form validation
     ------------------------------------------------------------------ */

  /** Shows or clears the error under one field. Returns true when the field is valid. */
  function fieldErr(id, msg) {
    var input = byId(id), err = byId(id + "-err");
    if (err) { err.textContent = msg || ""; }
    if (input) { input.classList.toggle("err", !!msg); input.setAttribute("aria-invalid", msg ? "true" : "false"); }
    return !msg;
  }

  function clearAuthMessages() {
    all(".auth-box .ferr").forEach(function (e) { e.textContent = ""; });
    all(".auth-box .input").forEach(function (e) { e.classList.remove("err"); e.removeAttribute("aria-invalid"); });
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
    loginTab.tabIndex = tab === "login" ? 0 : -1;
    registerTab.tabIndex = tab === "register" ? 0 : -1;
    loginForm.hidden = tab !== "login";
    registerForm.hidden = tab !== "register";
    clearAuthMessages();
  }

  /**
   * Arrow keys, Home and End move between the tabs of a tablist and activate
   * the new one (the "automatic activation" pattern). Only the selected tab
   * stays in the Tab order.
   */
  function enableTabKeys(tabs, activate) {
    tabs.forEach(function (tab, index) {
      tab.addEventListener("keydown", function (e) {
        var next = -1;
        if (e.key === "ArrowRight" || e.key === "ArrowDown") { next = (index + 1) % tabs.length; }
        else if (e.key === "ArrowLeft" || e.key === "ArrowUp") { next = (index - 1 + tabs.length) % tabs.length; }
        else if (e.key === "Home") { next = 0; }
        else if (e.key === "End") { next = tabs.length - 1; }
        if (next < 0) { return; }
        e.preventDefault();
        tabs[next].focus();
        activate(tabs[next]);
      });
    });
  }

  function activateAuthTab(link) {
    switchAuth(link.getAttribute("data-auth-tab"));
    // Keep the address bar in step so a refresh opens the same tab.
    if (window.history && window.history.replaceState && link.href) {
      window.history.replaceState(null, "", link.href);
    }
  }

  var authTabs = all("[data-auth-tab]");
  authTabs.forEach(function (link) {
    link.tabIndex = link.getAttribute("aria-selected") === "true" ? 0 : -1;
    link.addEventListener("click", function (e) {
      e.preventDefault();
      activateAuthTab(link);
    });
  });
  enableTabKeys(authTabs, activateAuthTab);
  all(".auth-box .input.err").forEach(function (input) { input.setAttribute("aria-invalid", "true"); });

  function emailMessage(email) {
    if (!email) { return "Enter your email."; }
    if (!EMAIL_RE.test(email)) { return "Enter a valid email, like name@example.com."; }
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
          [data-drop-title]  title line that changes with the state
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
    var isTxt = /\.txt$/i.test(file.name);
    if (!isTxt && !/\.(pdf|docx)$/i.test(file.name)) {
      return "\"" + file.name + "\" can't be used. Upload a .txt, .pdf or .docx file.";
    }
    if (file.size === 0) { return "This file is empty. Choose a resume that contains text."; }
    if (isTxt && file.size > MAX_TXT_BYTES) { return "This file is larger than 200 KB. Resumes are usually under 20 KB of text."; }
    if (!isTxt && file.size > MAX_DOC_BYTES) { return "This file is larger than 2 MB. Export a smaller PDF or DOCX, or upload a .txt file."; }
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
    var live = form.querySelector("[data-live]");
    function announce(text) { if (live) { live.textContent = ""; live.textContent = text; } }
    function focusPick() { var pick = form.querySelector("[data-pick-file]"); if (pick) { pick.focus(); } }
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
      announce("Selected " + file.name + ", " + (file.size / 1024).toFixed(1) + " KB. Ready to analyze.");
      var go = form.querySelector("[data-ready] button[type=submit]");
      if (go) { go.focus(); }
    }

    function accept(files) {
      var file = files && files[0];
      var problem = uploadProblem(file);
      if (problem) { input.value = ""; showIdle(problem); focusPick(); return; }
      showReady(file);
    }

    all("[data-pick-file]", form).forEach(function (b) {
      b.addEventListener("click", function (e) { e.preventDefault(); input.click(); });
    });
    all("[data-clear-file]", form).forEach(function (b) {
      b.addEventListener("click", function (e) {
        e.preventDefault(); input.value = ""; showIdle("");
        announce("File removed.");
        var pick = form.querySelector("[data-pick-file]");
        if (pick) { pick.focus(); }
      });
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
      if (problem) { input.value = ""; showIdle(problem); focusPick(); return; }
      try { input.files = files; } catch (ignored) { /* very old browsers: fall back to Choose file */ }
      accept(input.files && input.files.length ? input.files : files);
    });

    form.addEventListener("submit", function (e) {
      var problem = uploadProblem(input.files && input.files[0]);
      if (problem) { e.preventDefault(); showIdle(problem); focusPick(); return; }
      if (busy) { e.preventDefault(); return; }
      busy = true;
      // The server does the real work; these steps only show that it is running.
      if (title) { title.textContent = "Analyzing " + input.files[0].name; }
      announce("Analyzing " + input.files[0].name + ". Please wait.");
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
    function show(btn) {
      var name = btn.getAttribute("data-tab");
      buttons.forEach(function (b) {
        b.classList.toggle("active", b === btn);
        b.setAttribute("aria-selected", b === btn ? "true" : "false");
        b.tabIndex = b === btn ? 0 : -1;
      });
      all("[data-tab-panel]").forEach(function (panel) {
        panel.hidden = panel.getAttribute("data-tab-panel") !== name;
      });
    }
    buttons.forEach(function (btn) {
      btn.tabIndex = btn.classList.contains("active") ? 0 : -1;
      btn.addEventListener("click", function (e) {
        e.preventDefault();
        show(btn);
      });
    });
    enableTabKeys(buttons, show);
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

  /* ------------------------------------------------------------------
     7. Light / dark theme toggle. The saved choice (or the system setting)
        is applied by theme-init.js (loaded in the head) before the page paints.
     ------------------------------------------------------------------ */
  (function () {
    var root = document.documentElement;
    var sun = '<svg class="i-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>';
    var moon = '<svg class="i-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>';

    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "theme-toggle";
    btn.innerHTML = sun + moon;

    // A toggle button: the name stays "Dark theme", aria-pressed says whether it is on.
    function label() {
      var dark = root.getAttribute("data-theme") === "dark";
      btn.title = dark ? "Switch to light theme" : "Switch to dark theme";
      btn.setAttribute("aria-label", "Dark theme");
      btn.setAttribute("aria-pressed", dark ? "true" : "false");
    }
    label();

    btn.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      try { localStorage.setItem("rm-theme", next); } catch (e) { /* storage blocked */ }
      label();
    });

    var me = document.querySelector(".me");
    var landing = document.querySelector(".l-nav .actions");
    var authBox = document.querySelector(".auth-box");
    if (me) {
      me.insertBefore(btn, me.querySelector(".icon-btn"));
    } else if (landing) {
      landing.appendChild(btn);
    } else if (authBox) {
      btn.classList.add("float");
      authBox.appendChild(btn);
    }
  })();
})();
