// Pop-up remover: hides full-screen newsletter / app / notification prompts. It only touches
// overlays that mention those words and contain no password box, so login dialogs are left alone.
var words = /newsletter|subscribe|sign up for|get the app|install our app|open in app|enable notifications|turn on notifications/i;
function sweep() {
  var vw = innerWidth, vh = innerHeight, all = document.body ? document.body.querySelectorAll('div,section,aside,dialog') : [];
  for (var i = 0; i < all.length && i < 1500; i++) {
    var el = all[i];
    if (el.getAttribute('data-xc-hidden')) continue;
    var cs = getComputedStyle(el);
    if (cs.position !== 'fixed' && cs.position !== 'sticky') continue;
    if ((parseInt(cs.zIndex, 10) || 0) < 100) continue;
    var r = el.getBoundingClientRect();
    if (r.width < vw * 0.6 || r.height < vh * 0.3) continue;
    if (el.querySelector('input[type=password]')) continue;
    if (!words.test((el.innerText || '').slice(0, 600))) continue;
    el.style.setProperty('display', 'none', 'important');
    el.setAttribute('data-xc-hidden', '1');
    document.documentElement.style.setProperty('overflow', 'auto', 'important');
    if (document.body) document.body.style.setProperty('overflow', 'auto', 'important');
  }
}
xcReady(function () {
  var n = 0, t = setInterval(function () { sweep(); if (++n > 12) clearInterval(t); }, 800);
  sweep();
});
