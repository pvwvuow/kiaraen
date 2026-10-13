/* kiara shell: scale the fixed 390x843 design so it FILLS the real screen.
   Portrait phones are narrower than the design's aspect, so we fill the full
   height (the bottom nav sits on the true bottom edge, no floating gap) and
   allow up to 8% of horizontal crop (4% per side, split evenly). Landscape
   keeps the width-fill letterbox. The status-bar inset still pushes the
   stage down when the real bar is taller than the design's 44px status row. */
(function () {
  var W = 390, H = 843, lastW = 0, lastH = 0;
  function fitFor(w, h) {
    var portrait = w <= h;
    return portrait ? Math.min(h / H, (w / W) * 1.08) : Math.min(w / W, h / H);
  }
  function fit() {
    var w = window.innerWidth, h = window.innerHeight;
    if (!w || !h) return;
    var de = document.documentElement;
    var top = window.__kiaraInsetTop || 0;
    var s0 = fitFor(w, h);
    var extra = Math.max(0, top - 44 * s0);
    var s = fitFor(w, h - extra);
    de.style.setProperty('--ks', String(s));
    de.style.setProperty('--kt', extra + 'px');
  }
  function onResize() {
    var w = window.innerWidth, h = window.innerHeight;
    // the keyboard only makes the window shorter: keep the scale so the screen doesn't jump
    if (w === lastW && h < lastH) return;
    lastW = w; lastH = h;
    fit();
  }
  window.kiaraSetInsetTop = function (px) { window.__kiaraInsetTop = +px || 0; lastW = 0; onResize(); };
  window.addEventListener('resize', onResize);
  onResize();
})();
