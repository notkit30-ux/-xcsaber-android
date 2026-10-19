// Unlock selection: lets you select and copy text on pages that try to block it.
xcCss('*{-webkit-user-select:text!important;user-select:text!important;-webkit-touch-callout:default!important}');
['copy', 'cut', 'selectstart', 'contextmenu', 'dragstart'].forEach(function (n) {
  window.addEventListener(n, function (e) { e.stopImmediatePropagation(); }, true);
});
