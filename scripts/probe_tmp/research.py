"""Temporary: what X's rebuilt Android app looks like and how it moves.

Prints each post's text and media, and article text, then contact sheets of
video frames and a few pictures, gzipped and base64'd, for a reader that can
look at pictures but not play video.
"""
import base64
import gzip
import json
import re
import subprocess
import sys
import urllib.request
from html import unescape
from pathlib import Path

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'


def get(url, binary=False, timeout=60):
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept-Language': 'en-US,en;q=0.9'})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read()
    return data if binary else data.decode('utf-8', 'replace')


def emit(label, path):
    data = gzip.compress(Path(path).read_bytes(), 9)
    text = base64.b64encode(data).decode()
    print(f'@@begin {label}')
    for i in range(0, len(text), 1500):
        print(f'@@{label} {text[i:i + 1500]}')
    print(f'@@end {label}')


def sheets(label, video, fps, width, cols, rows, start=0, length=None):
    """Contact sheets of frames, each frame labelled with its time."""
    pattern = OUT / f'{label}-%02d.jpg'
    vf = f"fps={fps},scale={width}:-1,drawtext=text='%{{pts\\:hms}}':x=4:y=4:fontsize=14:fontcolor=yellow:box=1:boxcolor=black@0.6,tile={cols}x{rows}"
    cmd = ['ffmpeg', '-loglevel', 'error', '-ss', str(start)]
    if length:
        cmd += ['-t', str(length)]
    cmd += ['-i', str(video), '-vf', vf, '-q:v', '4', str(pattern)]
    subprocess.run(cmd, check=False)
    made = sorted(OUT.glob(f'{label}-*.jpg'))
    for path in made[:8]:
        emit(path.stem, path)
    return len(made)


def tweet(status_id, label, detail=None):
    print(f'===== POST {label} {status_id}')
    try:
        data = json.loads(get(f'https://api.fxtwitter.com/status/{status_id}'))
    except Exception as e:
        print('fxtwitter failed', e)
        return
    t = data.get('tweet') or {}
    print('author', (t.get('author') or {}).get('screen_name'), 'date', t.get('created_at'))
    print('text', t.get('text'))
    media = t.get('media') or {}
    for i, photo in enumerate((media.get('photos') or [])[:4]):
        print('photo', photo.get('url'), photo.get('width'), photo.get('height'))
        try:
            p = OUT / f'{label}-photo{i}.jpg'
            p.write_bytes(get(photo['url'] + ('' if '?' in photo['url'] else '?name=small'), binary=True))
            subprocess.run(['convert', str(p), '-resize', '900x900>', '-quality', '80', str(p)], check=False)
            emit(f'{label}-photo{i}', p)
        except Exception as e:
            print('photo failed', e)
    for i, video in enumerate((media.get('videos') or [])[:1]):
        print('video', video.get('url'), video.get('width'), video.get('height'), video.get('duration'))
        try:
            v = OUT / f'{label}.mp4'
            v.write_bytes(get(video['url'], binary=True, timeout=120))
            print('video bytes', v.stat().st_size)
            if not detail:
                n = sheets(f'{label}-overview', v, fps=2, width=180, cols=8, rows=4)
                print('overview sheets', n)
            if detail:
                for j, (start, length, *rest) in enumerate(detail):
                    width, cols = rest if rest else (240, 6)
                    sheets(f"{label}-detail{j}", v, fps=12, width=width, cols=cols, rows=4, start=start, length=length)
        except Exception as e:
            print('video failed', e)


def article(url, label):
    print(f'===== ARTICLE {label} {url}')
    try:
        page = get(url)
    except Exception as e:
        print('fetch failed', e)
        return
    body = re.sub(r'(?is)<(script|style|noscript|svg)[^>]*>.*?</\1>', ' ', page)
    paragraphs = [unescape(re.sub(r'<[^>]+>', ' ', p)).strip() for p in re.findall(r'(?is)<p[^>]*>(.*?)</p>', body)]
    text = '\n'.join(re.sub(r'\s+', ' ', p) for p in paragraphs if len(p) > 40)
    print(text[:7000])
    images = re.findall(r'<img[^>]+src="(https://[^"]+\.(?:jpg|jpeg|png|webp)[^"]*)"', page)
    print('images', images[:12])


tweet('2064719054846427390', 'ice', detail=[(0.4, 1.6), (8.4, 1.6), (10.2, 1.6), (11.8, 1.6), (21.6, 1.6), (23.8, 1.6)])
tweet('2079273272274026718', 'bier', detail=[(10.0, 2.0, 480, 3), (14.0, 2.0, 480, 3), (16.5, 2.0, 480, 3), (18.5, 2.0, 480, 3)])
