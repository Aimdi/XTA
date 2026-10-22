"""Temporary: whether a logged-out reader can load a big thread's next comments.

Fetches a thread's comments piece through Google's translation proxy, prints
the top-level "more comments" partial, then asks for it by POST and by GET,
with and without the page's form fields, and prints what came back.
"""
import gzip
import base64
import http.cookiejar
import re
import sys
import urllib.parse
import urllib.request
from html import unescape

UA = 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
PROXY = 'https://www-reddit-com.translate.goog'
TR = {'_x_tr_sl': 'auto', '_x_tr_tl': 'en', '_x_tr_hl': 'en'}
jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def ask(path, query=None, data=None, headers=None):
    q = dict(TR)
    q.update(query or {})
    url = f'{PROXY}{path}?{urllib.parse.urlencode(q)}'
    h = {'User-Agent': UA, 'Accept-Language': 'en-US,en;q=0.9'}
    h.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=h, method='POST' if data is not None else 'GET')
    try:
        with opener.open(req, timeout=40) as r:
            return r.status, r.read().decode('utf-8', 'replace'), url
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'replace'), url
    except Exception as e:
        return -1, str(e), url


def counts(body):
    return {
        'comments': len(re.findall(r'<shreddit-comment(?=[\s>])', body)),
        'top-level': len(re.findall(r'<shreddit-comment(?=[\s>])[^>]*depth="0"', body)),
        'top-more': body.count('top-level-more-comments-partial'),
        'children-more': len(re.findall(r'slot="children"', body)),
        'browser-check': body.count('name="solution"'),
        'blocked': body.lower().count('blocked by network security'),
    }


def emit(label, body):
    text = base64.b64encode(gzip.compress(body.encode(), 9)).decode()
    print(f'@@begin {label}')
    for i in range(0, len(text), 1500):
        print(f'@@{label} {text[i:i + 1500]}')
    print(f'@@end {label}')


def partial_of(body):
    m = re.search(r'<faceplate-partial\b[^>]*id="top-level-more-comments-partial"[^>]*>([\s\S]*?)</faceplate-partial>', body)
    if not m:
        return None
    opening = re.sub(r'class="[^"]*"', '', m.group(0)[:m.group(0).find('>') + 1])
    inputs = dict((unescape(n), unescape(v)) for n, v in re.findall(r'<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"', m.group(1)))
    src = unescape(re.search(r'src="([^"]+)"', opening).group(1))
    return opening, inputs, src


def biggest_thread():
    status, body, _ = ask('/svc/shreddit/community-more-posts/hot/', {'name': 'AskReddit'})
    best = None
    for a in re.findall(r'<shreddit-post\b([^>]*)>', body):
        count = re.search(r'comment-count="(\d+)"', a)
        link = re.search(r'permalink="([^"]+)"', a)
        if count and link and (best is None or int(count.group(1)) > best[0]):
            best = (int(count.group(1)), unescape(link.group(1)))
    return best


threads = ['/svc/shreddit/comments/r/pics/t3_1xawic7']
big = biggest_thread()
print('AskReddit biggest', big)
if big:
    parts = big[1].strip('/').split('/')
    threads.append(f'/svc/shreddit/comments/r/{parts[1]}/t3_{parts[3]}')

kept = False
for path in threads:
    print('=====', path)
    status, body, url = ask(path)
    print('first', status, len(body), counts(body))
    found = partial_of(body)
    if not found:
        print('no top-level partial')
        continue
    opening, inputs, src = found
    print('partial', opening[:1200])
    print('inputs', {k: v[:80] for k, v in inputs.items()})
    print('cookies', [c.name for c in jar])
    p = urllib.parse.urlsplit(src)
    query = dict(urllib.parse.parse_qsl(p.query))
    form = urllib.parse.urlencode(inputs).encode()
    bare = urllib.parse.urlencode({k: v for k, v in inputs.items() if 'csrf' not in k}).encode()
    attempts = [
        ('post-form', lambda: ask(p.path, query, form, {'Content-Type': 'application/x-www-form-urlencoded'})),
        ('post-no-csrf', lambda: ask(p.path, query, bare, {'Content-Type': 'application/x-www-form-urlencoded'})),
        ('get-query', lambda: ask(p.path, {**query, **inputs})),
    ]
    for label, run in attempts:
        status, answer, url = run()
        print(label, status, len(answer), counts(answer) if status == 200 else answer[:300].replace('\n', ' '))
        if status == 200 and not kept and counts(answer)['comments'] > 0:
            emit('more' + label.replace('-', ''), answer)
            kept = True
