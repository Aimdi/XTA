import 'dart:convert';

import 'package:xta/reading/article_reading_store.dart';

/// Only injected into the app's sanitized article document, never a live site.
String articleReadingBridge(ArticleReadingState state, {double textScale = 1}) =>
    '''
(function() {
  if (window.xtaArticle) { window.xtaArticle.apply(${state.fontSize * textScale}, ${state.lineHeight}); return; }
  const saved = ${jsonEncode(state.point.toJson())};
  const root = document.querySelector('.content') || document.querySelector('article') || document.body;
  const selector = 'p,h1,h2,h3,h4,li,blockquote,figure,pre,table';
  const blocks = Array.from(root.querySelectorAll(selector)).filter(function(block) { return !block.querySelector(selector); });
  let interacted = false, userScrolled = false, restoring = true, timer;
  function maxScroll() { return Math.max(0, document.documentElement.scrollHeight - window.innerHeight); }
  function point() {
    let index = blocks.findIndex(function(b) { return b.getBoundingClientRect().bottom > 0; });
    if (index < 0) index = 0;
    return {paragraph: index, leading: blocks[index] ? blocks[index].getBoundingClientRect().top : 0,
      fraction: maxScroll() > 0 ? Math.max(0, Math.min(1, window.scrollY / maxScroll())) : 0};
  }
  function move(p) {
    if (p.fraction <= 0.001) { window.scrollTo(0, 0); return; }
    const block = blocks[p.paragraph];
    window.scrollTo(0, block ? window.scrollY + block.getBoundingClientRect().top - p.leading : p.fraction * maxScroll());
  }
  function send() {
    if (restoring || !window.XtaReading) return;
    const p = point();
    p.interacted = interacted;
    p.userScrolled = userScrolled;
    p.atEnd = maxScroll() > 24 && window.scrollY >= maxScroll() - 24;
    window.XtaReading.postMessage(JSON.stringify(p));
  }
  window.xtaArticle = {
    apply: function(size, spacing) {
      const current = point();
      document.documentElement.style.fontSize = size + 'px';
      document.body.style.fontSize = size + 'px';
      document.body.style.lineHeight = spacing;
      requestAnimationFrame(function() { move(current); send(); });
    },
    startOver: function() { interacted = true; userScrolled = false; window.scrollTo(0, 0); send(); }
  };
  document.documentElement.style.fontSize = ${state.fontSize * textScale} + 'px';
  document.body.style.fontSize = ${state.fontSize * textScale} + 'px';
  document.body.style.lineHeight = ${state.lineHeight};
  function interact() { interacted = true; restoring = false; }
  ['touchstart','wheel','keydown'].forEach(function(event) { window.addEventListener(event, interact, {passive: true}); });
  window.addEventListener('scroll', function() {
    if (interacted) userScrolled = true;
    clearTimeout(timer); timer = setTimeout(send, 200);
  }, {passive: true});
  const observer = new ResizeObserver(function() { if (!interacted) move(saved); });
  observer.observe(document.body);
  requestAnimationFrame(function() { move(saved); restoring = false; send(); });
  setTimeout(function() { observer.disconnect(); }, 3000);
})();
''';
