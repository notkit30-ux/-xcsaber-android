// Night shift: a warm, low-blue tint over the page. Touches pass straight through it.
xcReady(function () {
  var d = document.createElement('div');
  d.setAttribute('data-xc-addon', 'warm');
  d.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:2147483646;' +
    'background:rgba(255,150,40,.16);mix-blend-mode:multiply';
  document.documentElement.appendChild(d);
});
