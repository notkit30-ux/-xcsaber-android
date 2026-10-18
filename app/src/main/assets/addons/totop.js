// Scroll-to-top button: appears after you scroll down a long way.
xcReady(function () {
  var b = document.createElement('button');
  b.setAttribute('data-xc-addon', 'totop');
  b.setAttribute('aria-label', 'Back to top');
  b.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5M5 12l7-7 7 7"/></svg>';
  b.style.cssText = 'position:fixed;right:16px;bottom:20px;width:44px;height:44px;border-radius:22px;border:0;' +
    'background:rgba(30,40,70,.88);box-shadow:0 2px 10px rgba(0,0,0,.35);z-index:2147483647;display:none;' +
    'align-items:center;justify-content:center;padding:0';
  b.addEventListener('click', function () { scrollTo({ top: 0, behavior: 'smooth' }); });
  document.documentElement.appendChild(b);
  addEventListener('scroll', function () {
    b.style.display = scrollY > innerHeight * 1.5 ? 'flex' : 'none';
  }, { passive: true });
});
