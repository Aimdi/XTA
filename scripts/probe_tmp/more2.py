"""Temporary: other ways to the next comments, and embeds by bare id."""
import re
import urllib.parse
import urllib.request
from html import unescape

UA = 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
TR = '_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en'


def fetch(url, data=None, headers=None):
    h = {'User-Agent': UA, 'Accept-Language': 'en-US,en;q=0.9'}
    h.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=h, method='POST' if data is not None else 'GET')
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            return r.status, r.read().decode('utf-8', 'replace'), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'replace'), dict(e.headers)
    except Exception as e:
        return -1, str(e), {}


def counts(body):
    return {
        'comments': len(re.findall(r'<shreddit-comment(?=[\s>])', body)),
        'top-level': len(re.findall(r'<shreddit-comment(?=[\s>])[^>]*depth="0"', body)),
        'top-more': body.count('top-level-more-comments-partial'),
        'check': body.count('name="solution"'),
        'blocked': body.lower().count('blocked by network security'),
        'tree': body.count('<shreddit-comment-tree'),
    }


def translated(path, query=''):
    sep = '&' if query else ''
    return f'https://www-reddit-com.translate.goog{path}?{TR}{sep}{query}'


status, body, _ = fetch(translated('/svc/shreddit/community-more-posts/hot/', 'name=AskReddit'))
best = max(((int(c), unescape(p)) for p, c in re.findall(r'<shreddit-post\b[^>]*permalink="([^"]+)"[^>]*comment-count="(\d+)"', body)), default=None)
print('biggest', best)
parts = best[1].strip('/').split('/')
piece = f'/svc/shreddit/comments/r/{parts[1]}/t3_{parts[3]}'
status, body, headers = fetch(translated(piece))
print('first', status, counts(body))
m = re.search(r'<faceplate-partial\b[^>]*id="top-level-more-comments-partial"[^>]*src="([^"]+)"[\s\S]*?name="cursor" value="([^"]+)"', body)
src, cursor = unescape(m.group(1)), unescape(m.group(2))
print('src', src, 'cursor', cursor[:40])
print('tail of tree', re.sub(r'class="[^"]*"', '', body[body.rfind('</shreddit-comment>'):][:1500]))

target = 'https://www.reddit.com' + src
form = urllib.parse.urlencode({'cursor': cursor}).encode()
ctype = {'Content-Type': 'application/x-www-form-urlencoded'}
for name, url in [
    ('corsproxy', 'https://corsproxy.io/?url=' + urllib.parse.quote(target, safe='')),
    ('thingproxy', 'https://thingproxy.freeboard.io/fetch/' + target),
    ('corseu', 'https://cors.eu.org/' + target),
]:
    status, answer, _ = fetch(url, form, ctype)
    print('POST', name, status, len(answer), counts(answer) if status == 200 else answer[:200].replace('\n', ' '))
    if status == 200 and counts(answer)['comments']:
        print('@@sample', name)
        print(re.sub(r'class="[^"]*"', '', answer[:3000]))
        print(re.sub(r'class="[^"]*"', '', answer[-2500:]))

for label, query in [
    ('cursor', 'cursor=' + urllib.parse.quote(cursor, safe='')),
    ('offset', 'top-level=1&commentPositionOffset=10&cursor=' + urllib.parse.quote(cursor, safe='')),
    ('limit', 'limit=50'),
    ('depth', 'depth=1&limit=100'),
]:
    status, answer, _ = fetch(translated(piece, query))
    print('GET piece', label, status, len(answer), counts(answer))

status, answer, _ = fetch(translated(src.split('?')[0], src.split('?')[1] + '&cursor=' + urllib.parse.quote(cursor, safe='')))
title = re.search(r'<title[^>]*>([^<]*)', answer)
print('GET more', status, len(answer), counts(answer), title.group(1) if title else None)
print(re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', re.sub(r'(?is)<(script|style|svg|template)[^>]*>.*?</\1>', '', answer)))[:600])

for path in ['/comments/1x9anef/', '/gallery/1x9anef', '/r/pics/comments/1x9anef/']:
    status, answer, _ = fetch(f'https://embed-reddit-com.translate.goog{path}?embed=true&{TR}')
    print('embed', path, status, len(answer), 'gallery-pages', len(re.findall(r'slot="page-\d+"', answer)), 'title', bool(re.search(r'id="embed-title"', answer)))
