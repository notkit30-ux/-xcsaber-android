// Sticky header remover (experimental): lets top bars scroll away so they stop covering the page.
function pass() {
  var vw = innerWidth, els = document.body ? document.body.querySelectorAll('header,nav,div') : [];
  for (var i = 0; i < els.length && i < 1200; i++) {
    var el = els[i], cs = getComputedStyle(el);
    if (cs.position !== 'fixed' && cs.position !== 'sticky') continue;
    var r = el.getBoundingClientRect();
    if (r.top <= 8 && r.height > 0 && r.height < 200 && r.width > vw * 0.8) {
      el.style.setProperty('position', 'absolute', 'important');
      el.style.setProperty('top', '0', 'important');
    }
  }
}
xcReady(function () { setTimeout(pass, 600); setTimeout(pass, 2500); });
