// Password peek: double-tap a password box to show what you typed; it hides again when you leave it.
document.addEventListener('dblclick', function (e) {
  var t = e.target;
  if (t && t.tagName === 'INPUT' && t.type === 'password') {
    t.type = 'text';
    t.addEventListener('blur', function once() { t.type = 'password'; t.removeEventListener('blur', once); });
  }
}, true);
