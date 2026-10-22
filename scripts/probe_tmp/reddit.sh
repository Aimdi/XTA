#!/usr/bin/env bash
# Temporary: whole comments pieces of two big threads, kept as they are served,
# to see where Reddit puts what it holds back.
set -u
UA='Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
OUT=$(mktemp -d)
TR='_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en'
keep() {
  [ -s "$OUT/$1.html" ] || return 0
  echo "@@begin $1"; gzip -9c "$OUT/$1.html" | base64 -w 1500 | sed "s/^/@@$1 /"; echo "@@end $1"
}
BIG=$(curl -sS -L --max-time 40 -A "$UA" "https://www-reddit-com.translate.goog/svc/shreddit/community-more-posts/hot/?${TR}&name=AskReddit" | python3 -I -c '
import re, sys, html
best = max(((int(c), html.unescape(p)) for p, c in re.findall(r"<shreddit-post\b[^>]*permalink=\"([^\"]+)\"[^>]*comment-count=\"(\d+)\"", sys.stdin.read())), default=None)
print(best[1] if best else "")')
echo "big $BIG"
P=$(echo "$BIG" | cut -d/ -f3); ID=$(echo "$BIG" | cut -d/ -f5)
curl -sS -L --max-time 40 -A "$UA" "https://www-reddit-com.translate.goog/svc/shreddit/comments/r/$P/t3_$ID?${TR}" -o "$OUT/cbig.html" -w 'http %{http_code} %{size_download}\n'
python3 -I scripts/probe_tmp/summary.py "$OUT/cbig.html"
keep cbig
curl -sS -L --max-time 40 -A "$UA" "https://www-reddit-com.translate.goog/svc/shreddit/comments/r/pics/t3_1xawic7?${TR}" -o "$OUT/cpics.html" -w 'http %{http_code} %{size_download}\n'
python3 -I scripts/probe_tmp/summary.py "$OUT/cpics.html"
keep cpics
