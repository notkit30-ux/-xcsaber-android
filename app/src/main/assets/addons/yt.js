// Distraction-free YouTube (best effort): hides Shorts shelves. YouTube changes its page often,
// so some elements may stay visible after an update.
xcCss('ytd-reel-shelf-renderer,ytm-reel-shelf-renderer,ytd-rich-shelf-renderer[is-shorts],' +
  'ytm-shorts-lockup-view-model,ytm-shorts-lockup-view-model-v2,a[title="Shorts"],' +
  'ytd-guide-entry-renderer:has(a[title="Shorts"]),ytm-pivot-bar-item-renderer:has(.pivot-shorts),' +
  'ytm-rich-section-renderer:has(ytm-shorts-lockup-view-model){display:none!important}');
