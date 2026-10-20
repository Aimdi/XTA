#!/usr/bin/env bash
# Temporary: which of new Reddit's own endpoints serve threads, search and
# profiles, asked through Google's translation proxy (runner addresses are
# blocked). Whole answers are printed gzipped and base64'd.
set -u
UA='Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
OUT=$(mktemp -d)
ask() {
  local label=$1 path=$2 query=${3:-}
  local url="https://www-reddit-com.translate.goog${path}?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en${query}"
  echo "===== $label $url"
  curl -sS -L --max-time 40 -A "$UA" -H 'Accept-Language: en-US,en;q=0.9' "$url" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
  python3 -I scripts/probe_tmp/extract.py "$OUT/$label.html" "$label" | head -c 3500 || true
  echo
}
keep() {
  local label=$1
  [ -s "$OUT/$label.html" ] || return 0
  echo "@@begin $label"
  gzip -9c "$OUT/$label.html" | base64 -w 1500 | sed "s/^/@@$label /"
  echo "@@end $label"
}
T=t3_1xa75i8
ask c1 /svc/shreddit/comments/r/FlutterDev/$T
ask c2 /svc/shreddit/comments/FlutterDev/$T
ask c3 /svc/shreddit/more-comments/FlutterDev/$T '&top-level=1'
ask c4 /svc/shreddit/comments/r/FlutterDev/$T '&render-mode=partial&sort=CONFIDENCE'
ask c5 /svc/shreddit/post-comments/FlutterDev/$T
ask s1 /svc/shreddit/search/ '&q=flutter&type=posts'
ask s2 /svc/shreddit/search-more-posts/ '&q=flutter'
ask s3 /svc/shreddit/r/FlutterDev/search/ '&q=dart&type=posts'
ask u1 /svc/shreddit/profiles/profile_posts-more-posts/new/ '&name=spez'
ask u2 /svc/shreddit/profiles/profile_overview-more-posts/new/ '&name=spez'
ask u3 /svc/shreddit/profiles/profile_submitted-more-posts/new/ '&name=spez'
ask g1 /svc/shreddit/community-more-posts/hot/ '&name=pics'
ask n1 /svc/shreddit/community-more-posts/hot/ '&name=WTF'
ask x1 /svc/shreddit/community-more-posts/hot/ '&name=thissubdoesnotexist4815162342'
ask x2 /svc/shreddit/community-more-posts/hot/ '&name=fatpeoplehate'
ask ch /r/FlutterDev/comments/1xa75i8/
for label in c1 c2 c3 c4 c5 s1 s2 s3 u1 u2 u3; do
  if grep -q '<shreddit-post\|<shreddit-comment ' "$OUT/$label.html" 2>/dev/null; then keep "$label"; fi
done
keep g1
keep ch
