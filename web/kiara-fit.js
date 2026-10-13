/* kiara shell: scale the fixed 390x843 design to the screen before the first paint */
(function () {
  var W = 390, H = 843, lastW = 0, lastH = 0;
  function fit() {
    var w = window.innerWidth, h = window.innerHeight;
    if (!w || !h) return;
    var de = document.documentElement;
    var top = window.__kiaraInsetTop || 0;
    var s = Math.min(w / W, h / H);
    // the status row of the design is ~44px; push the stage down only if the real bar is taller
    var extra = Math.max(0, top - 44 * s);
    if (extra) s = Math.min(w / W, (h - extra) / H);
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
