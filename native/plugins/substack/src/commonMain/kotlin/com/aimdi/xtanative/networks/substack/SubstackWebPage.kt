package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import com.aimdi.xtanative.store.settings.SubstackColumnWidth
import com.aimdi.xtanative.store.settings.SubstackReaderFont
import kotlinx.serialization.json.JsonPrimitive

/** The page's colours as CSS (`#rrggbb`), and whether it is dark or written right to left. */
data class SubstackPageColors(
    val background: String,
    val foreground: String,
    val muted: String,
    val link: String,
    val isDark: Boolean,
    val isRtl: Boolean = false,
)

/** The words the page says around the body, in the reader's language. */
data class SubstackPageWords(
    val readTime: String? = null,
    /** Where the free part of a paid post stops; null for a whole post. */
    val footer: String? = null,
    val footerLink: String? = null,
    val footerLinkLabel: String? = null,
    val embedLabel: String = "",
    val webOnlyLabel: String = "",
    val openLabel: String = "",
)

/**
 * The article's page for the web-view reader, as XTA's substack_html.dart
 * writes it: the sanitized body under its title in the reader's colours,
 * type and column, its held embeds as buttons that load them in place, and
 * the page's only scripts: the reader's own, which say where reading starts
 * when text is selected (`xtaSpeakFromSelection`, posting to `XtaTts`) and
 * where the reader stands (`XtaReading`).
 */
object SubstackWebPage {
    /** The page around [body], an article's [SubstackArticle.body] (already sanitized). */
    fun wrap(
        post: SubstackPost,
        body: String,
        colors: SubstackPageColors,
        words: SubstackPageWords,
        font: SubstackReaderFont = SubstackReaderFont.SERIF,
        column: SubstackColumnWidth = SubstackColumnWidth.MEDIUM,
        fontSizePx: Double = 18.0,
        lineHeight: Double = 1.7,
    ): String {
        val meta = listOfNotNull(post.publicationName, post.authorName, words.readTime).filter { it.isNotEmpty() }.joinToString(" · ") { escape(it) }
        val codeBg = if (colors.isDark) "#1E1E1E" else "#F1F3F5"
        val rule = if (colors.isDark) "rgba(255,255,255,0.12)" else "rgba(15,20,25,0.12)"
        val scheme = if (colors.isDark) "dark" else "light"
        val subtitle = post.excerpt?.takeIf { it.isNotEmpty() }
        return """
<!DOCTYPE html>
<html dir="${if (colors.isRtl) "rtl" else "ltr"}">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=3" />
<meta name="color-scheme" content="$scheme" />
<style>
  :root { color-scheme: $scheme; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; background: ${colors.background}; color: ${colors.foreground}; }
  body {
    font-family: ${cssStack(font)};
    font-size: ${fontSizePx}px;
    line-height: $lineHeight;
    padding: 20px 18px 48px;
    -webkit-text-size-adjust: 100%;
    text-rendering: optimizeLegibility;
    word-wrap: break-word;
    overflow-wrap: anywhere;
  }
  .reader { max-width: ${column.rem}rem; margin: 0 auto; }
  .title { font-family: $SANS; font-size: 1.75rem; font-weight: 700; line-height: 1.25; letter-spacing: -0.02em; margin: 0 0 10px; }
  .meta { font-family: $SANS; color: ${colors.muted}; font-size: 0.92rem; margin: 0 0 8px; }
  .subtitle { font-family: $SANS; color: ${colors.muted}; font-size: 1.08rem; line-height: 1.45; margin: 0 0 1.4rem; }
  .content > *:first-child { margin-top: 0; }
  p { margin: 0 0 1.05em; }
  h1, h2, h3, h4, h5, h6 { font-family: $SANS; line-height: 1.3; letter-spacing: -0.01em; margin: 1.5em 0 0.6em; }
  h2 { font-size: 1.35rem; }
  h3 { font-size: 1.2rem; }
  a { color: ${colors.link}; text-decoration-thickness: from-font; }
  strong, b { font-weight: 700; }
  em, i { font-style: italic; }
  ul, ol { padding-inline-start: 1.35em; margin: 0 0 1.05em; }
  li { margin: 0.35em 0; }
  blockquote { margin: 1.2em 0; padding-block: 0.15em; padding-inline-start: 1em; border-inline-start: 3px solid ${colors.link}; color: ${colors.muted}; }
  hr { border: 0; border-top: 1px solid $rule; margin: 1.6em 0; }
  figure, .captioned-image-container, .image2-inset { margin: 1.35em 0; padding: 0; }
  img, picture, video, iframe { max-width: 100%; height: auto; border-radius: 8px; display: block; }
  iframe { width: 100%; min-height: 220px; border: 0; }
  .xta-web-only p { color: ${colors.muted}; font-style: italic; }
  .xta-embed-load {
    width: 100%; min-height: 48px; padding: 12px 16px; border: 1px solid $rule; border-radius: 12px;
    background: transparent; color: ${colors.link}; font: inherit; text-align: center;
  }
  figcaption, .image-caption, .caption { font-family: $SANS; color: ${colors.muted}; font-size: 0.88rem; line-height: 1.4; margin-top: 0.55em; }
  pre, code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; background: $codeBg; border-radius: 8px; }
  code { padding: 0.1em 0.35em; font-size: 0.92em; }
  pre { padding: 12px 14px; overflow-x: auto; line-height: 1.45; }
  pre code { padding: 0; background: transparent; }
  table { display: block; overflow-x: auto; width: 100%; border-collapse: collapse; margin: 1.2em 0; font-size: 0.95em; }
  th, td { border: 1px solid $rule; padding: 8px 10px; text-align: start; vertical-align: top; }
  .twitter-embed, .youtube-wrap, .youtube-inner { margin: 1.2em 0; }
  .preview-end { margin-top: 2em; padding-top: 1em; border-top: 1px solid $rule; color: ${colors.muted}; font-size: 0.9em; }
  .preview-end p { margin: 0.4em 0; }
  [data-xta-block], [id], a[name] { scroll-margin-top: 16px; }
  [data-xta-selected] { outline: 2px solid ${colors.link}; outline-offset: 5px; }
  :focus-visible { outline: 2px solid ${colors.link}; outline-offset: 4px; }
</style>
</head>
<body>
  <article class="reader">
    <h1 class="title">${escape(post.title)}</h1>
    ${if (meta.isEmpty()) "" else "<div class=\"meta\">$meta</div>"}
    ${subtitle?.let { "<p class=\"subtitle\">${escape(it)}</p>" }.orEmpty()}
    <div class="content">$body</div>
    ${footer(words)}
  </article>
  <script>$SPEAK_FROM_HERE</script>
  <script>window.xtaEmbedLabel = ${script(words.embedLabel)};$EMBEDS</script>
  <script>window.xtaWebOnly = {label: ${script(words.webOnlyLabel)}, link: ${script(words.openLabel)}, url: ${script(post.canonicalUrl.orEmpty())}};$WEB_ONLY</script>
</body>
</html>
"""
    }

    /**
     * XTA's articleReadingBridge: put once into the article's page, it sets
     * the type, moves to [point], and posts where the reader stands to
     * `XtaReading` as they scroll; run again, it only sets the type.
     */
    fun readingBridge(point: ArticleReadPoint, fontSizePx: Double, lineHeight: Double): String = """
(function() {
  if (window.xtaArticle) { window.xtaArticle.apply($fontSizePx, $lineHeight); return; }
  const saved = ${point.toJson()};
  const root = document.querySelector('.content') || document.querySelector('article') || document.body;
  const selector = 'p,h1,h2,h3,h4,li,blockquote,figure,pre,table';
  const blocks = Array.from(root.querySelectorAll(selector)).filter(function(block) { return !block.querySelector(selector); });
  let interacted = false, userScrolled = false, restoring = true, jumped = false, timer;
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
    startOver: function() { interacted = true; userScrolled = false; window.scrollTo(0, 0); send(); },
    jumpTo: function(node) {
      jumped = true; restoring = false; interacted = false; userScrolled = false;
      observer.disconnect();
      node.scrollIntoView({block: 'start', behavior: 'auto'});
      send();
    }
  };
  document.documentElement.style.fontSize = $fontSizePx + 'px';
  document.body.style.fontSize = $fontSizePx + 'px';
  document.body.style.lineHeight = $lineHeight;
  function interact() { interacted = true; restoring = false; }
  ['touchstart','wheel','keydown'].forEach(function(event) { window.addEventListener(event, interact, {passive: true}); });
  window.addEventListener('scroll', function() {
    if (interacted) userScrolled = true;
    clearTimeout(timer); timer = setTimeout(send, 200);
  }, {passive: true});
  const observer = new ResizeObserver(function() { if (!interacted && !jumped) move(saved); });
  observer.observe(document.body);
  requestAnimationFrame(function() { if (!jumped) move(saved); restoring = false; send(); });
  setTimeout(function() { observer.disconnect(); }, 3000);
})();
"""

    /** Back to the top of the article. */
    const val START_OVER = "window.xtaArticle && window.xtaArticle.startOver();"

    /** Reads from the selected paragraph through `XtaTts`; answers whether text was selected. */
    const val SPEAK_SELECTION = "window.xtaSpeakFromSelection ? window.xtaSpeakFromSelection() : false"

    /**
     * Scrolls to the passage marked [anchor] and marks it, as XTA's
     * substackArticleJumpJs; with [publisherAnchor], to the element the
     * publisher named so (an `id` or `<a name>`), as a footnote link does.
     */
    fun jump(anchor: String, publisherAnchor: Boolean = false): String = """
(function() {
  const root = document.querySelector('.content');
  if (!root) return;
  const anchor = ${script(anchor)};
  const publisher = $publisherAnchor;
  const nodes = root.querySelectorAll(publisher ? '[id],a[name]' : '[data-xta-block]');
  const node = Array.from(nodes).find(function(n) {
    return publisher ? n.id === anchor || n.getAttribute('name') === anchor : n.getAttribute('data-xta-block') === anchor;
  });
  if (!node) return;
  const old = root.querySelector('[data-xta-selected]');
  if (old) old.removeAttribute('data-xta-selected');
  node.setAttribute('data-xta-selected', 'true');
  if (window.xtaArticle && window.xtaArticle.jumpTo) window.xtaArticle.jumpTo(node);
  else node.scrollIntoView({block: 'start', behavior: 'auto'});
  node.setAttribute('tabindex', '-1');
  node.focus({preventScroll: true});
})();
"""

    /** The readable prose of the publication's live page, for Listen, as XTA pulls it. */
    const val LIVE_TEXT = """
(function() {
  var root = document.querySelector('div.available-content')
    || document.querySelector('article')
    || document.querySelector('[data-testid="post-content"]')
    || document.querySelector('.body.markup')
    || document.body;
  if (!root) return '';
  var clone = root.cloneNode(true);
  clone.querySelectorAll('script,style,nav,button,svg,iframe,form,.subscription-widget-wrap,.paywall').forEach(function(n){ n.remove(); });
  return (clone.innerText || '').replace(/\n{3,}/g, '\n\n').trim().slice(0, 120000);
})()
"""

    /** A script's string result as a web view hands it back, JSON-quoted, as plain text; null for none. */
    fun scriptResult(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed == "null" || trimmed == "undefined") return null
        if (trimmed.length < 2 || !trimmed.startsWith('"') || !trimmed.endsWith('"')) return trimmed
        return J.parse(trimmed).string ?: trimmed.substring(1, trimmed.length - 1)
    }

    private const val SANS = "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif"

    private fun cssStack(font: SubstackReaderFont): String = when (font) {
        SubstackReaderFont.SERIF -> "Georgia, \"Iowan Old Style\", \"Palatino Linotype\", Palatino, \"Times New Roman\", serif"
        SubstackReaderFont.SANS -> SANS
    }

    private fun footer(words: SubstackPageWords): String {
        val text = words.footer?.takeIf { it.isNotEmpty() } ?: return ""
        val link = words.footerLink?.takeIf { it.isNotEmpty() }
        val label = words.footerLinkLabel?.takeIf { it.isNotEmpty() }
        val action = if (link != null && label != null) "<p><a href=\"${escape(link)}\">${escape(label)}</a></p>" else ""
        return "<div class=\"preview-end\"><p>${escape(text)}</p>$action</div>"
    }

    /** [value] as a JavaScript string literal that cannot close its script tag. */
    internal fun script(value: String): String = JsonPrimitive(value).toString().replace("<", "\\u003c")

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** With text selected, `xtaSpeakFromSelection()` posts the rest of the article from the selected paragraph to `XtaTts`. */
    private const val SPEAK_FROM_HERE = """
(function(){
  if (window.__xtaTtsFromHere) return;
  window.__xtaTtsFromHere = true;
  function post(text){
    if (!text || !window.XtaTts || !XtaTts.postMessage) return;
    XtaTts.postMessage(text);
  }
  function textOf(b){
    if (!b) return '';
    var marks = b.querySelectorAll ? b.querySelectorAll('.footnote-anchor,.footnote-number') : [];
    var kept = [];
    for (var i = 0; i < marks.length; i++) { kept.push(marks[i].textContent); marks[i].textContent = ''; }
    var t = (b.innerText || '').trim();
    for (var j = 0; j < marks.length; j++) marks[j].textContent = kept[j];
    return t;
  }
  function blockOf(node){
    var n = node && node.nodeType === 3 ? node.parentElement : node;
    while (n && n !== document.body) {
      var t = n.tagName;
      if (t && /^(P|H1|H2|H3|H4|H5|H6|LI|BLOCKQUOTE|PRE|FIGCAPTION)$/.test(t)) return n;
      if (n.classList && (n.classList.contains('title') || n.classList.contains('subtitle'))) return n;
      n = n.parentElement;
    }
    return node;
  }
  function fromHere(el){
    var root = document.querySelector('article') || document.body;
    var blocks = root.querySelectorAll('h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,.title,.subtitle');
    var started = false;
    var parts = [];
    for (var i = 0; i < blocks.length; i++) {
      var b = blocks[i];
      if (b === el || b.contains(el) || (el && el.contains && el.contains(b))) started = true;
      if (started) {
        var t = textOf(b);
        if (t) parts.push(t);
      }
    }
    if (!parts.length) {
      var all = textOf(root);
      var needle = textOf(el).slice(0, 80);
      var at = needle ? all.indexOf(needle) : -1;
      post(at >= 0 ? all.slice(at) : (needle || all));
      return;
    }
    post(parts.join('\n\n'));
  }
  window.xtaSpeakFromSelection = function(){
    var s = window.getSelection && window.getSelection();
    if (!s || s.isCollapsed || !s.anchorNode) return false;
    fromHere(blockOf(s.anchorNode));
    return true;
  };
})();
"""

    /** Turns each held embed into a button that puts the original iframe back. */
    private const val EMBEDS = """
(function(){
  document.querySelectorAll('.xta-embed').forEach(function(box){
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'xta-embed-load';
    var host = box.getAttribute('data-host');
    button.textContent = [window.xtaEmbedLabel, host].filter(Boolean).join(' \u00b7 ');
    button.addEventListener('click', function(){
      var held = box.querySelector('template');
      if (held) box.replaceWith(held.content.cloneNode(true));
    });
    box.appendChild(button);
  });
})();
"""

    /** Says where a block drawn only by Substack's own scripts was, with a link to the post on the web. */
    private const val WEB_ONLY = """
(function(){
  var note = window.xtaWebOnly || {};
  document.querySelectorAll('.xta-web-only').forEach(function(box){
    var p = document.createElement('p');
    p.textContent = note.label || '';
    if (note.url && note.link) {
      var a = document.createElement('a');
      a.href = note.url;
      a.textContent = note.link;
      p.appendChild(document.createTextNode(' '));
      p.appendChild(a);
    }
    box.appendChild(p);
  });
})();
"""
}

/** Where a link tapped in the web-view reader goes, as XTA's reader decides (`_onNavigation`). */
sealed interface SubstackWebStep {
    /** The page follows it: its own reload, or anywhere on the publication's live page. */
    data object Follow : SubstackWebStep

    /** A place in this article: the page scrolls to the element the publisher named [anchor]. */
    data class Jump(val anchor: String) : SubstackWebStep

    /** Another post: it opens in the reader. */
    data class OpenPost(val link: SubstackPostLink) : SubstackWebStep

    /** Anything else leaves the article for the app's link rules. */
    data object OpenLink : SubstackWebStep
}

object SubstackWebNavigation {
    /** Where [url] goes from [post]'s page: the article's own ([live] false) or the publication's live page. */
    fun step(url: String, post: SubstackPost, live: Boolean, knownBaseUrls: Iterable<String> = emptyList()): SubstackWebStep {
        if (url.startsWith("about:blank") || url.startsWith("data:")) return SubstackWebStep.Follow
        val canonical = post.canonicalUrl
        if (live && canonical != null && url.substringBefore('#') == canonical.substringBefore('#')) return SubstackWebStep.Follow
        val fragment = SubstackReading.articleFragment(url, canonical)
        if (!live && fragment != null) return SubstackWebStep.Jump(fragment)
        val link = SubstackLinks.postLink(url, knownBaseUrls)
        val same = link != null && link.slug == post.slug && SubstackHosts.hostOf(link.publicationBase) == SubstackHosts.hostOf(post.publicationBaseUrl)
        if (link != null && !same) return SubstackWebStep.OpenPost(link)
        return if (live) SubstackWebStep.Follow else SubstackWebStep.OpenLink
    }
}
