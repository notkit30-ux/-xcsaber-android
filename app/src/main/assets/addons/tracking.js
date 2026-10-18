// Clean links: strips tracking tags (utm_*, fbclid, gclid ...) from the page address and from links you tap.
var bad = /^(utm_[a-z]+|fbclid|gclid|dclid|gbraid|wbraid|msclkid|mc_eid|mc_cid|igshid|yclid|_hsenc|_hsmi|vero_id|oly_enc_id|oly_anon_id)$/i;
function clean(href) {
  try {
    var u = new URL(href, location.href);
    if (!/^https?:$/.test(u.protocol)) return href;
    var keys = [];
    u.searchParams.forEach(function (v, k) { if (bad.test(k)) keys.push(k); });
    if (!keys.length) return href;
    keys.forEach(function (k) { u.searchParams.delete(k); });
    return u.toString();
  } catch (e) { return href; }
}
try {
  var c = clean(location.href);
  if (c !== location.href) history.replaceState(history.state, '', c);
} catch (e) {}
function onTap(e) {
  var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
  if (a) { var c = clean(a.href); if (c !== a.href) a.href = c; }
}
document.addEventListener('click', onTap, true);
document.addEventListener('touchstart', onTap, { capture: true, passive: true });
