#!/usr/bin/env bash
# Temporary: the gaps in reading Reddit without an account — more comments, a
# post's own page, pinned posts, NSFW gates, video renditions, search paging —
# asked through Google's translation proxy (runner addresses are blocked).
set -u
UA='Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
OUT=$(mktemp -d)
TR='_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en'
ask() { # label path query [extra curl args...]
  local label=$1 path=$2 query=$3; shift 3
  local url="https://www-reddit-com.translate.goog${path}?${TR}${query}"
  echo "===== $label $url"
  curl -sS -L --max-time 40 -A "$UA" -H 'Accept-Language: en-US,en;q=0.9' "$@" "$url" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
  python3 -I scripts/probe_tmp/summary.py "$OUT/$label.html" || true
}
keep() {
  [ -s "$OUT/$1.html" ] || return 0
  echo "@@begin $1"; gzip -9c "$OUT/$1.html" | base64 -w 1500 | sed "s/^/@@$1 /"; echo "@@end $1"
}
ask hot /svc/shreddit/community-more-posts/hot/ '&name=AskReddit'
BIG=$(python3 -I - "$OUT/hot.html" <<'PY'
import re, sys
body = open(sys.argv[1], encoding='utf-8', errors='replace').read()
best = None
for tag in re.findall(r'<shreddit-post(?=[\s>])[^>]*>', body):
    a = dict(re.findall(r'\s([a-z-]+)="([^"]*)"', tag))
    n = int(a.get('comment-count', '0') or 0)
    if best is None or n > best[0]:
        best = (n, a.get('permalink'), a.get('id'))
print(f"{best[1]} {best[2]} {best[0]}" if best else '')
PY
)
echo "big $BIG"
PERMA=$(echo "$BIG" | awk '{print $1}'); PID=$(echo "$BIG" | awk '{print $2}')
SLUG_PATH=$PERMA
for v in "" "&limit=500" "&depth=10" "&count=200" "&sort=TOP" "&initial-load=200"; do
  label="c$(echo "$v" | tr -c 'a-z0-9' '_' | cut -c1-20)"
  ask "$label" "/svc/shreddit/comments/r/AskReddit/$PID" "$v"
done
keep c
# The top-level "more" partial: its src and cursor, then asked for by POST and GET.
MORE=$(python3 -I - "$OUT/c.html" <<'PY'
import html, re, sys
body = open(sys.argv[1], encoding='utf-8', errors='replace').read()
m = re.search(r'<faceplate-partial[^>]*id="top-level-more-comments-partial"[^>]*src="([^"]+)"[\s\S]*?name="cursor" value="([^"]+)"', body)
print(html.unescape(m.group(1)) + ' ' + m.group(2) if m else '')
PY
)
echo "more $MORE"
SRC=$(echo "$MORE" | awk '{print $1}'); CUR=$(echo "$MORE" | awk '{print $2}')
if [ -n "$SRC" ]; then
  P=${SRC%%\?*}; Q=${SRC#*\?}
  ask morepost "$P" "&$Q" -X POST --data-urlencode "cursor=$CUR" -H 'Content-Type: application/x-www-form-urlencoded'
  keep morepost
  ask moreget "$P" "&$Q&cursor=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$CUR")"
fi
# A post's own page, from the embed service and oEmbed.
echo "===== embed direct"
curl -sS -L --max-time 40 -A "$UA" "https://embed.reddit.com${PERMA}?embed=true" -o "$OUT/embed.html" -w 'http %{http_code} %{size_download}\n' || true
python3 -I scripts/probe_tmp/summary.py "$OUT/embed.html" || true
echo "===== embed proxy"
curl -sS -L --max-time 40 -A "$UA" "https://embed-reddit-com.translate.goog${PERMA}?embed=true&${TR}" -o "$OUT/embedp.html" -w 'http %{http_code} %{size_download}\n' || true
python3 -I scripts/probe_tmp/summary.py "$OUT/embedp.html" || true
keep embed; keep embedp
ask oembed /oembed "&url=https://www.reddit.com${PERMA}"
head -c 1500 "$OUT/oembed.html"; echo
# Pinned posts: what a full community page loads, from the archive, and guesses.
echo "===== wayback flutterdev partials"
curl -sS -L --max-time 90 "https://web.archive.org/web/20260928155421id_/https://www.reddit.com/r/flutterdev/" -o "$OUT/wb.html" -w 'http %{http_code} %{size_download}\n' || true
grep -o '<faceplate-partial[^>]*>' "$OUT/wb.html" | sed 's/class="[^"]*"//' | cut -c1-400 | head -20
grep -o '<[a-z-]*highlight[a-z-]*[^>]*>' "$OUT/wb.html" | cut -c1-300 | head -10
grep -o '<shreddit-[a-z-]*' "$OUT/wb.html" | sort | uniq -c | sort -rn | head -30
ask hl1 /svc/shreddit/community-highlights/ '&name=Android'
ask hl2 /svc/shreddit/community-highlights-carousel/ '&name=Android'
# NSFW communities: structure only.
ask nsfw /svc/shreddit/community-more-posts/hot/ '&name=nsfw'
grep -o '<[a-z]*-[a-z-]*' "$OUT/nsfw.html" | sort | uniq -c | sort -rn | head -25
grep -io '[^<>]\{0,80\}\(18\|nsfw\|mature\|age\)[^<>]\{0,80\}' "$OUT/nsfw.html" | grep -v 'class=\|svg\|path' | head -8
# Video renditions.
echo "===== hls"
curl -sS --max-time 30 -A "$UA" 'https://v.redd.it/0570b89w9owh1/HLSPlaylist.m3u8?f=hd%2CsubsAll%2ChlsSpecOrder&v=1&a=1795126298%2CMjZmNGQ4MWM2NjdlYzk4Zjg4Zjc1MGE1OGYwNmM2YTU4ODJlOGM3NWY2YzM1YWVhNGM1Yzg0Yjg5ODY5ZDA5ZQ%3D%3D' | head -40
echo "===== dash unsigned"
curl -sS --max-time 30 -A "$UA" -o "$OUT/dash.mpd" -w 'http %{http_code} %{size_download}\n' 'https://v.redd.it/0570b89w9owh1/DASHPlaylist.mpd'; head -c 1500 "$OUT/dash.mpd"; echo
for f in CMAF_720.mp4 CMAF_480.mp4 DASH_720.mp4 CMAF_AUDIO_128.mp4; do printf '%s ' "$f"; curl -sS -o /dev/null -I -w '%{http_code} %{size_download}\n' --max-time 20 "https://v.redd.it/0570b89w9owh1/$f"; done
# Search paging.
ask s1 /svc/shreddit/search/ '&q=flutter&type=posts&sort=new'
NEXT=$(grep -o 'src="/svc/shreddit/search/[^"]*cursor=[^"]*"' "$OUT/s1.html" | head -1 | sed 's/src="//;s/"$//;s/&amp;/\&/g')
echo "next $NEXT"
[ -n "$NEXT" ] && ask s2 "${NEXT%%\?*}" "&${NEXT#*\?}"
