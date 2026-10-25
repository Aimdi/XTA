"""Temporary: more footage of X's rebuilt Android app, for its motion.

Reads articles about the app for the X posts and videos they embed, searches
the web and YouTube for more, and prints contact sheets of every new video's
frames, gzipped and base64'd, for a reader that can look at pictures but not
play video. The three posts the first pass studied are skipped.
"""
import base64
import gzip
import json
import re
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
KNOWN = {'2064719054846427390', '2066984244854050990', '2079273272274026718'}
SHEETS_LEFT = [48]

# The launch video, already studied, under every account that posted it.
KNOWN |= {'2079276059250270240', '2079275369304150492'}
SEEN_VIDEOS = {'2079270544470650881'}

# X's Android engineers, as Nikita Bier named them on launch day, and the
# accounts that announced the rebuild.
HANDLES = [
    'attilablenesi', 'geruk', 'e_matsyuk', 'zhao0506', 'arkann1985', 'x_belous', 'adrian579086',
    'vcoolish', 'art_antonov', 'gilsjulio', 'theapache64', 'nikitabier', 'XEng', 'Engineering',
]
MIRRORS = ['https://xcancel.com', 'https://nitter.net', 'https://nitter.poast.org']

SEARCHES = [
    'X Android app new animation video',
    'X Lite Android beta video',
    'new X Android app screen recording',
    'X Android rebuilt smooth animations',
    'theapache64 X Android',
    'geruk X Android app',
    'e_matsyuk X Android',
    'X app Android like animation new',
    'X Android custom timelines pin topics',
    'X Android Jetpack Compose motion',
]
KEYWORDS = re.compile(r'android|animat|motion|compose|smooth|rebuil|new app|x lite|timeline|redesign|ui\b', re.I)


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


def sheets(label, video, fps, width=180, cols=8, rows=4):
    """Contact sheets of frames, each labelled with its time, within the budget."""
    pattern = OUT / f'{label}-%02d.jpg'
    vf = (
        f"fps={fps},scale={width}:-1,"
        f"drawtext=text='%{{pts\\:hms}}':x=4:y=4:fontsize=14:fontcolor=yellow:box=1:boxcolor=black@0.6,"
        f"tile={cols}x{rows}"
    )
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', str(video), '-vf', vf, '-q:v', '5', str(pattern)], check=False)
    made = sorted(OUT.glob(f'{label}-*.jpg'))
    for path in made:
        if SHEETS_LEFT[0] <= 0:
            print('sheet budget spent, skipped', path.stem)
            continue
        SHEETS_LEFT[0] -= 1
        emit(path.stem, path)
    return len(made)


def duration(video):
    probe = subprocess.run(
        ['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', str(video)],
        capture_output=True, text=True,
    )
    try:
        return float(probe.stdout.strip())
    except ValueError:
        return 0.0


def overview(label, video):
    """Two frames a second for a clip, fewer for a long review."""
    seconds = duration(video)
    fps = 2 if seconds <= 70 else 0.5 if seconds <= 300 else 0.2
    print(f'{label}: {seconds:.1f} s, {fps} frames a second')
    print('sheets', sheets(f'{label}-overview', video, fps))


def status_ids(html):
    return {m.group(2): m.group(1) for m in re.finditer(r'(?:x|twitter)\.com/([A-Za-z0-9_]{1,15})/status(?:es)?/(\d{15,20})', html)}


def youtube_ids(html):
    return set(re.findall(r'(?:youtube\.com/(?:watch\?v=|embed/|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})', html))


def post(status_id, author):
    print(f'===== POST {author} {status_id}')
    try:
        data = json.loads(get(f'https://api.fxtwitter.com/status/{status_id}'))
    except Exception as e:
        print('fxtwitter failed', e)
        return
    t = data.get('tweet') or {}
    text = (t.get('text') or '').replace('\n', ' ')
    videos = (t.get('media') or {}).get('videos') or []
    print('author', (t.get('author') or {}).get('screen_name'), 'date', t.get('created_at'), 'videos', len(videos))
    print('text', text[:600])
    if not KEYWORDS.search(text):
        return
    for i, v in enumerate(videos[:2]):
        source = re.search(r'/(\d{15,20})/', v.get('url') or '')
        if source and source.group(1) in SEEN_VIDEOS:
            print('video already seen', v.get('url'))
            continue
        if source:
            SEEN_VIDEOS.add(source.group(1))
        print('video', v.get('url'), v.get('duration'), v.get('width'), v.get('height'))
        path = OUT / f'p{status_id}-{i}.mp4'
        try:
            path.write_bytes(get(v['url'], binary=True, timeout=180))
            overview(f'x{status_id[-6:]}v{i}', path)
        except Exception as e:
            print('video failed', e)


def search(query):
    """Result pages from two engines, for the X posts they link."""
    pages = []
    for url in (
        'https://html.duckduckgo.com/html/?q=' + urllib.parse.quote(query),
        'https://www.bing.com/search?q=' + urllib.parse.quote(f'{query} site:x.com'),
    ):
        try:
            pages.append(get(url))
        except Exception as e:
            print('search failed', url, e)
    return urllib.parse.unquote(' '.join(pages))


def mirror_posts(handle):
    """The handle's recent posts from the first public mirror that answers."""
    for mirror in MIRRORS:
        try:
            html = get(f'{mirror}/{handle}', timeout=40)
        except Exception as e:
            print('mirror failed', mirror, handle, e)
            continue
        found = {m.group(1) for m in re.finditer(rf'/{handle}/status/(\d{{15,20}})', html, re.I)}
        print(f'===== MIRROR {mirror} {handle}', len(html), 'posts', len(found))
        if found:
            return {i: handle for i in found}
    return {}


def youtube(query, found):
    try:
        listing = subprocess.run(
            ['yt-dlp', '--flat-playlist', '-J', f'ytsearch8:{query}'], capture_output=True, text=True, timeout=120
        )
        entries = json.loads(listing.stdout or '{}').get('entries') or []
    except Exception as e:
        print('youtube search failed', query, e)
        return
    print(f'===== YOUTUBE {query}')
    for e in entries:
        print('yt', e.get('id'), e.get('duration'), (e.get('title') or '')[:120], e.get('channel'))
        title = (e.get('title') or '').lower()
        if (' x ' in f' {title} ' or 'twitter' in title) and 'android' in title and (e.get('duration') or 0) <= 900:
            found.add(e.get('id'))


def youtube_video(video_id):
    print(f'===== YOUTUBE VIDEO {video_id}')
    path = OUT / f'yt-{video_id}.mp4'
    run = subprocess.run(
        ['yt-dlp', '-f', 'bv*[height<=480][ext=mp4]+ba/b[height<=480]/b', '--merge-output-format', 'mp4',
         '-o', str(path), f'https://www.youtube.com/watch?v={video_id}'],
        capture_output=True, text=True, timeout=600,
    )
    print('yt-dlp', run.returncode, run.stderr.strip()[-400:])
    if path.exists():
        overview(f'yt{video_id[:6]}', path)


def main():
    statuses = {}
    for handle in HANDLES:
        statuses.update(mirror_posts(handle))
    for query in SEARCHES:
        found = status_ids(search(query))
        print(f'===== SEARCH {query}', found)
        statuses.update(found)
    print('===== ALL POSTS', len(statuses))
    for status_id, author in statuses.items():
        if status_id not in KNOWN:
            post(status_id, author)


main()
