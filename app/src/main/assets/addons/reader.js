// Reading mode: on article-like pages, hides chrome and sets a comfortable reading column.
xcReady(function () {
  var paras = document.querySelectorAll('p').length;
  var looksLikeArticle = document.querySelector('article') || paras >= 6;
  if (!looksLikeArticle || document.querySelectorAll('input,select,textarea').length > 6) return;
  xcCss('header,nav,aside,footer,[role=banner],[role=navigation],[role=complementary],.sidebar,.ad,.ads,' +
    '[class*="advert"],[id*="advert"],iframe[src*="ads"]{display:none!important}' +
    'body{background:#faf8f3!important;color:#1d1d1f!important}' +
    'article,main,[role=main]{max-width:42rem!important;margin:0 auto!important;float:none!important;' +
    'width:auto!important;padding:0 16px!important}' +
    'p,li{font-size:1.1rem!important;line-height:1.7!important;color:#1d1d1f!important}' +
    'img,video{max-width:100%!important;height:auto!important}');
});
