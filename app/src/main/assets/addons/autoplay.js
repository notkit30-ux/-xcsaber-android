// Stop autoplay: pauses videos that start by themselves. Videos you tap to play still work.
var touched = false;
['touchstart', 'mousedown', 'keydown'].forEach(function (n) {
  addEventListener(n, function () { touched = true; }, { capture: true, passive: true });
});
document.addEventListener('play', function (e) {
  var v = e.target;
  if (!touched && v && v.pause) { try { v.pause(); v.removeAttribute('autoplay'); } catch (x) {} }
}, true);
xcReady(function () {
  var vids = document.querySelectorAll('video[autoplay]');
  for (var i = 0; i < vids.length; i++) { vids[i].removeAttribute('autoplay'); try { vids[i].pause(); } catch (e) {} }
});
