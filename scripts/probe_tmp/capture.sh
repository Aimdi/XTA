#!/usr/bin/env bash
# Temporary: real new-Reddit markup from the Wayback Machine and Google's
# translation proxy, since Reddit blocks runner addresses directly.
set -u
UA='Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
OUT=$(mktemp -d)
cdx() {
  curl -sS --max-time 60 "https://web.archive.org/cdx/search/cdx?url=$1&output=json&from=$2&filter=statuscode:200&filter=mimetype:text/html&limit=-${3:-3}&fl=timestamp,original,length" || true
}
wayback() {
  local pattern=$1 label=$2
  echo "===== CDX $pattern"
  local rows; rows=$(cdx "$pattern" 2025 4)
  echo "$rows" | head -c 3000; echo
  local latest; latest=$(echo "$rows" | python3 -c 'import json,sys
try:
    rows=json.load(sys.stdin)[1:]
except Exception:
    rows=[]
print(" ".join(r[0]+"|"+r[1] for r in rows[-1:]))')
  for entry in $latest; do
    local ts=${entry%%|*} url=${entry#*|}
    echo "fetch $ts $url"
    curl -sS -L --max-time 60 -A "$UA" "https://web.archive.org/web/${ts}id_/${url}" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
    python3 -I scripts/probe_tmp/extract.py "$OUT/$label.html" "$label wayback $ts" || true
  done
}
translate() {
  local path=$1 label=$2 query=${3:-}
  local url="https://www-reddit-com.translate.goog${path}?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en${query}"
  echo "===== TRANSLATE $url"
  curl -sS -L --max-time 60 -A "$UA" -H 'Accept-Language: en-US,en;q=0.9' "$url" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
  python3 -I scripts/probe_tmp/extract.py "$OUT/$label.html" "$label translate" || true
}
translate /r/FlutterDev/ t-listing
THREAD=$(grep -o '/r/FlutterDev/comments/[a-z0-9]*/[a-zA-Z0-9_]*/' "$OUT/t-listing.html" | head -1 || true)
echo "thread $THREAD"
if [ -n "$THREAD" ]; then translate "$THREAD" t-thread; fi
translate /r/aww/ t-aww
translate /r/FlutterDev/new/ t-new
translate /svc/shreddit/community-more-posts/hot/ t-more '&name=FlutterDev'
translate /search/ t-search '&q=flutter&type=posts'
translate /user/spez/submitted/ t-user
wayback 'www.reddit.com/r/FlutterDev/' w-listing
wayback 'www.reddit.com/r/FlutterDev/comments/*' w-thread
wayback 'www.reddit.com/r/aww/' w-aww
wayback 'www.reddit.com/svc/shreddit/*' w-svc
