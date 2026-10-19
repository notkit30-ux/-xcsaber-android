// Force zoom: lets you pinch-zoom on pages that disable it.
function fix() {
  var m = document.querySelector('meta[name=viewport]');
  if (!m) { m = document.createElement('meta'); m.name = 'viewport'; (document.head || document.documentElement).appendChild(m); }
  var want = 'width=device-width, initial-scale=1, maximum-scale=10, user-scalable=yes';
  if (m.content !== want) m.content = want;
}
fix();
xcReady(function () {
  fix();
  if (document.head) new MutationObserver(fix).observe(document.head, { childList: true, subtree: true, attributes: true });
});
