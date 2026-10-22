"""Temporary: counts of what a Reddit page holds."""
import re
import sys

body = open(sys.argv[1], encoding='utf-8', errors='replace').read()
title = re.search(r'<title[^>]*>([^<]*)</title>', body)
print(f'  {len(body)} chars, title {title.group(1).strip() if title else "-"}')
counts = {
    'posts': len(re.findall(r'<shreddit-post(?=[\s>])', body)),
    'comments': len(re.findall(r'<shreddit-comment(?=[\s>])', body)),
    'top-level': len(re.findall(r'<shreddit-comment(?=[\s>])[^>]*depth="0"', body)),
    'more-partials': len(re.findall(r'more-comments', body)),
    'top-more': len(re.findall(r'top-level-more-comments-partial', body)),
    'search-results': len(re.findall(r'view-events="search/view/', body)),
    'browser-check': body.count('name="solution"'),
    'blocked': body.lower().count('blocked by network security'),
}
print('  ' + ' '.join(f'{k}={v}' for k, v in counts.items()))
tree = re.search(r'<shreddit-comment-tree[^>]*>', body)
if tree:
    print('  ' + re.sub(r'class="[^"]*"', '', tree.group(0))[:300])
m = re.search(r'id="top-level-more-comments-partial"[^>]*src="([^"]+)"', body)
if m:
    print('  more ' + m.group(1)[:300])
