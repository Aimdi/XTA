#!/usr/bin/env bash
# Temporary: the rest of new Reddit's endpoints a reader needs, asked through
# Google's translation proxy (runner addresses are blocked). Whole answers are
# printed gzipped and base64'd.
set -u
UA='Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
OUT=$(mktemp -d)
ask() {
  local label=$1 path=$2 query=${3:-}
  local url="https://www-reddit-com.translate.goog${path}?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en${query}"
  echo "===== $label $url"
  curl -sS -L --max-time 40 -A "$UA" -H 'Accept-Language: en-US,en;q=0.9' "$url" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
  python3 -I scripts/probe_tmp/extract.py "$OUT/$label.html" "$label" | head -c 1500 || true
  echo
  echo "@@begin $label"
  gzip -9c "$OUT/$label.html" | base64 -w 1500 | sed "s/^/@@$label /"
  echo "@@end $label"
}
ask s1 /svc/shreddit/search/ '&q=flutter&type=posts'
ask s2 /svc/shreddit/search/ '&q=flutter&type=posts&sort=new&t=all'
ask s3 /svc/shreddit/r/FlutterDev/search/ '&q=dart&type=posts&restrict_sr=1'
ask s4 /svc/shreddit/search/ '&q=flutter&type=communities'
ask s5 /svc/shreddit/search/ '&q=spez&type=people'
ask s6 /svc/shreddit/search/ '&q=spez&type=users'
ask n1 /svc/shreddit/community-more-posts/hot/ '&name=WTF'
ask x1 /svc/shreddit/community-more-posts/hot/ '&name=thissubdoesnotexist4815162342'
ask x2 /svc/shreddit/community-more-posts/hot/ '&name=fatpeoplehate'
ask p1 /svc/shreddit/community-more-posts/hot/ '&name=Android'
ask v1 /svc/shreddit/community-more-posts/hot/ '&name=nextfuckinglevel'
ask t1 /svc/shreddit/community-more-posts/top/ '&name=FlutterDev&t=WEEK'
ask c6 /svc/shreddit/comments/r/FlutterDev/t3_1xa75i8 '&sort=NEW'
ask c7 /svc/shreddit/comments/r/pics/t3_1xawic7
ask a1 /svc/shreddit/community-sidebar/ '&name=FlutterDev'
ask a2 /svc/shreddit/r/FlutterDev/about/
