package com.vandam.ink

/**
 * Styles added to every page of a book. The placeholders in braces are filled from the reading settings.
 * The body is laid out in columns exactly one screen wide, so page n starts n screen widths across.
 */
internal const val READER_STYLES = """
@font-face { font-family: "Maia Literata"; src: url("/__reader/Literata.ttf"); font-style: normal; font-weight: 100 900; }
@font-face { font-family: "Maia Literata"; src: url("/__reader/Literata-Italic.ttf"); font-style: italic; font-weight: 100 900; }
@font-face { font-family: "Maia Inter"; src: url("/__reader/Inter-Regular.otf"); }
html {
  margin: 0 !important; padding: 0 !important; height: 100vh !important; overflow: hidden !important;
  background: #000 !important; -webkit-text-size-adjust: none;
}
body {
  visibility: hidden;
  margin: 0 !important; padding: {top} {margin} {bottom} !important; box-sizing: border-box !important;
  width: 100vw !important; max-width: none !important; height: 100vh !important;
  column-width: calc(100vw - {gap}); column-gap: {gap}; column-fill: auto;
  background: transparent !important; color: #fff !important;
  font-family: {font} !important; font-size: {size} !important; line-height: {lineHeight} !important;
  font-optical-sizing: auto; -webkit-hyphens: {hyphens} !important; hyphens: {hyphens} !important;
  -webkit-user-select: none; user-select: none;
}
body * {
  color: inherit !important; background-color: transparent !important; border-color: #7f7f84 !important;
  font-family: inherit !important; line-height: inherit !important;
  -webkit-hyphens: inherit !important; hyphens: inherit !important;
}
img, svg, video { max-width: 100% !important; max-height: calc(100vh - {top} - {bottom}) !important; object-fit: contain; break-inside: avoid; }
h1, h2, h3, h4, h5, h6 { break-after: avoid; line-height: 1.25 !important; }
a { text-decoration: none !important; }
"""

/** Runs in every page of a book: lays out the pages, turns them, and reports the position. */
internal const val READER_SCRIPT = """
(function () {
  var bridge = window.MaiaReader;
  if (!bridge) return;
  var config = JSON.parse(bridge.config());
  // A replaced document can still call the bridge while its WebView starts the next load.
  config.generation = Number(new URL(window.location.href).searchParams.get("g"));
  var page = 0;
  var pages = 1;
  var words = 0;
  var ready = false;
  var lastLayout = "";

  // The body is exactly 100vw; innerWidth is rounded, which would drift a little further each page.
  function width() { return document.body.getBoundingClientRect().width; }

  // The last column ends a margin short of a full page, so round up, then widen the root so the
  // last page can be scrolled to in full.
  function measure() {
    pages = Math.max(1, Math.ceil(document.body.scrollWidth / width() - 0.01));
    document.documentElement.style.minWidth = pages * width() + "px";
    var chapters = [];
    config.chapters.forEach(function (entry) {
      var anchor = entry.anchor && document.getElementById(entry.anchor);
      if (!entry.anchor || anchor) chapters.push({ index: entry.index, page: anchor ? pageOf(anchor) : 0 });
    });
    var layout = JSON.stringify([pages, words, chapters]);
    if (layout !== lastLayout) {
      lastLayout = layout;
      bridge.layout(config.generation, pages, words, JSON.stringify(chapters));
    }
  }

  function show(next) {
    measure();
    page = Math.max(0, Math.min(pages - 1, next));
    window.scrollTo(page * width(), 0);
    bridge.position(config.generation, page);
  }

  function pageOf(element) {
    var x = element.getBoundingClientRect().left + window.scrollX;
    return Math.floor(x / width());
  }

  function start() {
    if (ready) return;
    ready = true;
    words = (document.body.textContent || "").split(/\s+/).filter(Boolean).length;
    measure();
    if (config.paginate) {
      bridge.ready(config.generation);
      return;
    }
    var target = 0;
    var anchor = config.anchor && document.getElementById(config.anchor);
    if (config.end) target = pages - 1;
    else if (anchor) target = pageOf(anchor);
    else if (config.fraction) target = Math.floor(config.fraction * pages + 0.001);
    show(target);
    document.body.style.visibility = "visible";
    // Two frames later the page has been painted, so it can be brought to the top without a blank frame.
    requestAnimationFrame(function () {
      requestAnimationFrame(function () { bridge.ready(config.generation); });
    });
  }

  function next() {
    if (!ready) return;
    if (page < pages - 1) show(page + 1);
    else bridge.turn(config.generation, 1);
  }

  function previous() {
    if (!ready) return;
    if (page > 0) show(page - 1);
    else bridge.turn(config.generation, -1);
  }

  window.maia = { next: next, previous: previous };

  // Hyphenation needs the language; chapter files often leave it to the package.
  if (config.language && !document.documentElement.getAttribute("lang")) {
    document.documentElement.setAttribute("lang", config.language);
  }

  document.addEventListener("DOMContentLoaded", function () {
    // A sideways swipe on the screen turns the page too; the tap that ends it must not turn another.
    var touchStart = null;
    var swiped = false;
    document.addEventListener("touchstart", function (event) {
      var touch = event.touches[0];
      touchStart = { x: touch.clientX, y: touch.clientY };
      swiped = false;
    }, { passive: true });
    document.addEventListener("touchend", function (event) {
      if (!touchStart) return;
      var touch = event.changedTouches[0];
      var dx = touch.clientX - touchStart.x;
      var dy = touch.clientY - touchStart.y;
      touchStart = null;
      if (!config.swipes || Math.abs(dx) < 40 || Math.abs(dx) < Math.abs(dy) * 1.5) return;
      swiped = true;
      if (dx < 0) next();
      else previous();
    });
    document.addEventListener("click", function (event) {
      if (swiped) {
        swiped = false;
        event.preventDefault();
        return;
      }
      if (event.target.closest && event.target.closest("a[href]")) return;
      event.preventDefault();
      if (!config.taps) return;
      if (event.clientX < width() / 3) previous();
      else next();
    }, true);
  });

  // Wait for fonts and images so the page count is right.
  window.addEventListener("load", function () {
    (document.fonts ? document.fonts.ready : Promise.resolve()).then(function () {
      start();
    });
  });
  window.addEventListener("resize", function () {
    if (!ready) return;
    var fraction = page / pages;
    measure();
    if (config.paginate) return;
    show(Math.floor(fraction * pages + 0.001));
  });
})();
"""
