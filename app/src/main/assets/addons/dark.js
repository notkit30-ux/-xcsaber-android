// Dark pages: inverts light pages, then re-inverts media so photos look normal.
xcCss('html{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important}' +
  'img,video,picture,canvas,iframe,svg image,[style*="background-image"]{filter:invert(1) hue-rotate(180deg)!important}');
xcReady(function () {
  try {
    var c = getComputedStyle(document.body || document.documentElement).backgroundColor.match(/[\d.]+/g);
    if (c && c.length >= 3 && (c.length < 4 || +c[3] > 0.5)) {
      var lum = (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255;
      if (lum < 0.35) { // the page is already dark: undo
        var s = document.querySelector('style[data-xc-addon="dark"]');
        if (s) s.remove();
      }
    }
  } catch (e) {}
});
