// Cookie banner hider: hides common consent pop-ups. It only hides them; it does not click
// "reject" for you, so the site may still assume you have not answered.
var sels = ['#onetrust-banner-sdk', '#onetrust-consent-sdk', '#CybotCookiebotDialog', '#cookiebanner',
  '#cookie-banner', '#cookie-notice', '#cookieconsent', '#gdpr-cookie-message', '#gdpr-banner',
  '.cookie-banner', '.cookie-notice', '.cookie-consent', '.cookies-banner', '.cc-window', '.cc-banner',
  '.js-cookie-consent', '[id*="cookie-consent"]', '[class*="cookie-consent"]', '[aria-label*="cookie" i]',
  '#sp_message_container_1', '[id^="sp_message_container"]', '.fc-consent-root', '#qc-cmp2-container',
  '.truste_box_overlay', '#truste-consent-track', '#didomi-host', '#usercentrics-root'];
xcCss(sels.join(',') + '{display:none!important}');
xcReady(function () {
  // Some banners lock page scrolling; give it back once they are hidden.
  var hit = false;
  for (var i = 0; i < sels.length; i++) { try { if (document.querySelector(sels[i])) { hit = true; break; } } catch (e) {} }
  if (hit) xcCss('html,body{overflow:auto!important;position:static!important}');
});
