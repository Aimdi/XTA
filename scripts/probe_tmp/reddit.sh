#!/usr/bin/env bash
# Temporary: NSFW marks, loading more comments, and a post's own body, asked
# through Google's translation proxy (runner addresses are blocked).
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
# NSFW: how a post is marked, without printing any of its content.
ask nsfw /svc/shreddit/community-more-posts/hot/ '&name=nsfw'
python3 -I scripts/probe_tmp/marks.py "$OUT/nsfw.html"
ask mixed /svc/shreddit/community-more-posts/hot/ '&name=popular'
python3 -I scripts/probe_tmp/marks.py "$OUT/mixed.html"
ask spoil /svc/shreddit/community-more-posts/hot/ '&name=anime'
python3 -I scripts/probe_tmp/marks.py "$OUT/spoil.html"
# More comments: the top-level partial, by POST and by GET.
ask c /svc/shreddit/comments/r/FlutterDev/t3_1xa75i8 ''
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
  ENC=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$CUR")
  ask moreget "$P" "&$Q&cursor=$ENC"
  keep moreget
fi
# A post's own body and media, from the embed service.
for p in /r/FlutterDev/comments/1xa75i8/why_flutter_text_looks_lighter_than_swiftui_on_ios/ /r/pics/comments/1xawic7/early_voting_today_in_north_dallas_oc/ /r/pics/comments/1xb4rqw/i_painted_an_ocean_front_tennis_court_scene_set/; do
  label="embed$(echo "$p" | cut -d/ -f5)"
  echo "===== $label"
  curl -sS -L --max-time 40 -A "$UA" "https://embed-reddit-com.translate.goog${p}?embed=true&${TR}" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
  python3 -I - "$OUT/$label.html" <<'PY'
import re, sys
from collections import Counter
body = open(sys.argv[1], encoding='utf-8', errors='replace').read()
print(Counter(re.findall(r'<((?:shreddit|faceplate)-[a-z-]+)', body)).most_common(15))
b = body.find('<body')
text = re.sub(r'(?is)<(script|style|svg)[^>]*>.*?</\1>', ' ', body[b:])
text = re.sub(r'<[^>]+>', ' ', text)
print(re.sub(r'\s+', ' ', text)[:800])
print(re.findall(r'<img[^>]+src="([^"]+)"', body)[:6])
PY
  keep "$label"
done
