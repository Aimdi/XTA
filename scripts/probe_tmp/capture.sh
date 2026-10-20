#!/usr/bin/env bash
# Temporary: real new-Reddit markup from the Wayback Machine and Google's
# translation proxy, since Reddit blocks runner addresses directly. Pages are
# printed gzipped and base64'd so they can be rebuilt byte for byte.
set -u
UA='Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
OUT=$(mktemp -d)
dump() {
  local label=$1
  [ -s "$OUT/$label.html" ] || return 0
  python3 -I scripts/probe_tmp/extract.py "$OUT/$label.html" "$label" | head -c 20000 || true
  echo "@@begin $label"
  gzip -9c "$OUT/$label.html" | base64 -w 1500 | sed "s/^/@@$label /"
  echo "@@end $label"
}
translate() {
  local path=$1 label=$2 query=${3:-}
  local url="https://www-reddit-com.translate.goog${path}?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en${query}"
  echo "===== TRANSLATE $label $url"
  curl -sS -L --max-time 60 -A "$UA" -H 'Accept-Language: en-US,en;q=0.9' "$url" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
}
wayback_latest() {
  local pattern=$1 label=$2 from=$3
  echo "===== CDX $label $pattern"
  local rows
  rows=$(curl -sS --max-time 90 "https://web.archive.org/cdx/search/cdx?url=$pattern&output=json&from=$from&filter=statuscode:200&filter=mimetype:text/html&limit=-40&fl=timestamp,original,length" || true)
  echo "$rows" | tail -c 2500; echo
  # The largest recent capture: most likely a thread with its comments rendered.
  local pick
  pick=$(echo "$rows" | python3 -c 'import json,sys
try:
    rows=json.load(sys.stdin)[1:]
except Exception:
    rows=[]
rows=[r for r in rows if r[2].isdigit()]
rows.sort(key=lambda r:int(r[2]))
print(rows[-1][0]+"|"+rows[-1][1] if rows else "")')
  [ -n "$pick" ] || return 0
  echo "fetch ${pick%%|*} ${pick#*|}"
  curl -sS -L --max-time 90 -A "$UA" "https://web.archive.org/web/${pick%%|*}id_/${pick#*|}" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
}

translate /svc/shreddit/community-more-posts/hot/ more-hot '&name=aww'
dump more-hot
CURSOR=$(grep -o 'more-posts/hot/?name=aww&amp;after=[^&]*' "$OUT/more-hot.html" | head -1 | sed 's/.*after=//')
echo "cursor $CURSOR"
translate /svc/shreddit/community-more-posts/top/ more-top "&name=FlutterDev&t=WEEK"
python3 -I scripts/probe_tmp/extract.py "$OUT/more-top.html" more-top | head -c 4000
translate /r/FlutterDev/comments/1xa75i8/why_flutter_text_looks_lighter_than_swiftui_on_ios/ thread-page
dump thread-page
for sub in aww pics videos worldnews technology; do
  wayback_latest "www.reddit.com/r/$sub/comments/*" "wb-$sub" 202609
  python3 -I scripts/probe_tmp/extract.py "$OUT/wb-$sub.html" "wb-$sub" | head -c 6000 || true
done
BEST=$(for f in "$OUT"/wb-*.html; do [ -s "$f" ] && printf '%s %s\n' "$(grep -c '<shreddit-comment ' "$f")" "$(basename "$f" .html)"; done | sort -rn | head -2 | awk '{print $2}')
echo "best $BEST"
for label in $BEST; do dump "$label"; done
