// Same tab: links that would open a new tab open right here instead.
document.addEventListener('click', function (e) {
  var a = e.target && e.target.closest ? e.target.closest('a[target=_blank]') : null;
  if (!a || e.defaultPrevented || e.ctrlKey || e.metaKey) return;
  if (!/^https?:/i.test(a.href)) return;
  e.preventDefault();
  location.href = a.href;
}, true);
