"""Temporary: what Reddit's embed page of a post holds, by kind of post.

Prints the structure of each embed: title, body length, pictures, video,
links, counts and the screen-view JSON. For an NSFW post only the marks and
counts are printed, never its words or links.
"""
import re
import sys
from collections import Counter
from html import unescape

body = open(sys.argv[1], encoding='utf-8', errors='replace').read()
quiet = len(sys.argv) > 2 and sys.argv[2] == 'quiet'
start = body.find('<body')
page = re.sub(r'(?is)<(script|style|svg|template)[^>]*>.*?</\1>', '', body[start:] if start >= 0 else body)
print('  tags', Counter(re.findall(r'<((?:shreddit|faceplate|gallery)-[a-z-]+)', page)).most_common(14))
screen = re.search(r'<shreddit-screenview-data data="([^"]+)"', page)
print('  screenview', unescape(screen.group(1))[:400] if screen else None)
title = re.search(r'<h1[^>]*>([\s\S]*?)</h1>', page)
print('  title', bool(title) if quiet else (unescape(title.group(1)).strip()[:120] if title else None))
rt = re.search(r'id="t3_[a-z0-9]+-post-rtjson-content"[^>]*>([\s\S]*?)</div>\s*</div>', page)
text = re.sub(r'\s+', ' ', unescape(re.sub(r'<[^>]+>', ' ', rt.group(1)))).strip() if rt else ''
print('  body chars', len(text), 'ends', '-' if quiet else text[-160:])
print('  read-more', page.count('read-more-button'), 'overflow', 'overflow-cover' in page)
print('  numbers', re.findall(r'<faceplate-number number="(\d+)"', page)[:4])
print('  view comments', re.findall(r'View (\d[\d,.kK]*) comments?', page)[:2])
print('  timeago', re.findall(r'<faceplate-timeago ts="([^"]+)"', page)[:2])
print('  author', None if quiet else re.findall(r'Posted by </span>\s*<a[^>]*>([^<]+)</a>', page)[:1])
imgs = re.findall(r'<img[^>]+src="([^"]+)"', page)
print('  images', len(imgs), [] if quiet else [unescape(i)[:110] for i in imgs[:5]])
print('  players', [re.sub(r'class="[^"]*"|style="[^"]*"', '', p)[:900] for p in re.findall(r'<shreddit-player[^>]*>', page)][:1] if not quiet else page.count('<shreddit-player'))
if not quiet:
    for a in re.findall(r'<a[^>]+href="([^"]+)"[^>]*>', page):
        target = unescape(a)
        if 'reddit.com' not in target.split('u=')[-1][:40] and 'translate.google' in target:
            print('  outlink', target[:200])
print('  nsfw marks', Counter(re.findall(r'(is-nsfw-blocked|nsfw-blocked|blurred|over18|nsfw)', page)).most_common(6))
