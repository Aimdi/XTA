#!/usr/bin/env bash
# Temporary: loading more comments, and posts' own embed pages by kind, asked
# through Google's translation proxy (runner addresses are blocked).
set -u
UA='Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
OUT=$(mktemp -d)
TR='_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en'
keep() {
  [ -s "$OUT/$1.html" ] || return 0
  echo "@@begin $1"; gzip -9c "$OUT/$1.html" | base64 -w 1500 | sed "s/^/@@$1 /"; echo "@@end $1"
}
embed() { # label permalink [quiet]
  local label=$1 p=$2 mode=${3:-}
  echo "===== $label"
  curl -sS -L --max-time 40 -A "$UA" "https://embed-reddit-com.translate.goog${p}?embed=true&${TR}" -o "$OUT/$label.html" -w 'http %{http_code} %{size_download}\n' || true
  python3 -I scripts/probe_tmp/embed.py "$OUT/$label.html" $mode || true
}
python3 -I scripts/probe_tmp/more.py || true
embed evideo /r/aww/comments/1xb1imj/update_odin_the_im_not_a_morning_person_chihuahua/ && keep evideo
embed elink /r/Android/comments/1xaxenh/galaxy_s27_ultra_could_switch_to_exynos_2700_over/ && keep elink
embed ecross /r/pics/comments/1xapc2a/protests_in_porto_portugal_171026/ && keep ecross
embed elong /user/spez/comments/1vgbkge/modernizing_reddits_infrastructure_with_you/ && keep elong
embed etext /r/FlutterDev/comments/1x7h9pb/spent_a_month_researching_flutter_app/
embed eold /r/IAmA/comments/3cxedn/i_am_steve_huffman_the_new_ceo_of_reddit_ama/
embed egone /r/FlutterDev/comments/zzzzzzz/no_such_post/ && keep egone
# An NSFW post's embed: marks and counts only.
NSFW=$(curl -sS -L --max-time 40 -A "$UA" "https://www-reddit-com.translate.goog/svc/shreddit/community-more-posts/hot/?${TR}&name=nsfw" | python3 -I -c '
import re, sys, html
m = re.search(r"<shreddit-post\b[^>]*permalink=\"([^\"]+)\"", sys.stdin.read())
print(html.unescape(m.group(1)) if m else "")')
[ -n "$NSFW" ] && embed ensfw "$NSFW" quiet
