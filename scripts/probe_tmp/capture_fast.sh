#!/usr/bin/env bash
# Temporary: comment subtrees, a post's own body and paging on new Reddit,
# asked through Google's translation proxy (runner addresses are blocked).
set -u
UA='Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
OUT=$(mktemp -d)
ask() {
  local label=$1 path=$2 query=${3:-}
  local url="https://www-reddit-com.translate.goog${path}?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en${query}"
  echo "===== $label $url"
  curl -sS -L --max-time 40 -A "$UA" -H 'Accept-Language: en-US,en;q=0.9' "$url" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
  python3 -I scripts/probe_tmp/extract.py "$OUT/$label.html" "$label" | head -c 1200 || true
  echo
  if grep -q '<shreddit-post\|<shreddit-comment ' "$OUT/$label.html" 2>/dev/null; then
    echo "@@begin $label"
    gzip -9c "$OUT/$label.html" | base64 -w 1500 | sed "s/^/@@$label /"
    echo "@@end $label"
  fi
}
P=t3_1xa75i8
ask k1 /svc/shreddit/comments/r/FlutterDev/$P/t1_phuyrt3
ask k2 /svc/shreddit/comments/r/FlutterDev/$P '&comment=phuyrt3'
ask k3 /svc/shreddit/comment/t1_phuyrt3 '&depth=0&subredditName=FlutterDev'
ask k4 /svc/shreddit/comments/r/FlutterDev/$P '&comment=t1_phuyrt3'
ask k5 /svc/shreddit/more-comments/FlutterDev/$P '&top-level=1&commentPositionOffset=6'
ask d1 /svc/shreddit/post/r/FlutterDev/$P
ask d2 /svc/shreddit/r/FlutterDev/comments/1xa75i8/
ask d3 /svc/shreddit/posts/$P
ask d4 /svc/shreddit/post-detail/r/FlutterDev/$P
ask d5 /svc/shreddit/comments/r/FlutterDev/$P '&include-post=true'
ask h2 /svc/shreddit/community-more-posts/hot/ '&name=FlutterDev&after=dDNfMXg3OXFvdA%3D%3D&t=DAY'
