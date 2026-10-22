// Shows the "open the app" buttons only when the publish step filled in a real address.
document.addEventListener("DOMContentLoaded", function () {
  ["live-link", "live-cta"].forEach(function (id) {
    var link = document.getElementById(id);
    if (link && /^https?:\/\//.test(link.getAttribute("href"))) {
      link.hidden = false;
    }
  });
});
