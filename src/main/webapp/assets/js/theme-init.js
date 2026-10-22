/*
  Applies the saved or system colour theme before the page paints.
  Loaded as a plain blocking <script src> in <head> so there is no flash of
  the wrong theme. The toggle button itself lives in app.js.
*/
(function () {
  "use strict";
  var theme = null;
  try { theme = localStorage.getItem("rm-theme"); } catch (e) { /* storage blocked */ }
  if (theme !== "dark" && theme !== "light") {
    theme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  document.documentElement.setAttribute("data-theme", theme);
})();
