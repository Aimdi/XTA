"""Temporary: which scripts x.com's sign-in page loads, and where its signer is named."""
import json
import re
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
HEADERS = {"User-Agent": BROWSER, "Accept-Language": "en-US,en;q=0.9", "Referer": "https://x.com"}
INDEX = re.compile(r"""(?:^|[,{])\s*["']?(\d+)["']?\s*:\s*["']ondemand\.s["']""", re.M)
INDICES = re.compile(r"(\(\w{1}\[(\d{1,2})\],\s*16\))+", re.M)


def fetch(url):
    try:
        with urlopen(Request(url, headers=HEADERS), timeout=15) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace")
    except (URLError, TimeoutError, OSError) as error:
        return None, repr(error)


for page in ("https://x.com/i/flow/login", "https://x.com/login", "https://x.com/i/flow/signup", "https://x.com/tos"):
    status, html = fetch(page)
    scripts = re.findall(r'<script[^>]+src="([^"]+)"', html)
    preloads = re.findall(r'<link[^>]+rel="(?:modulepreload|preload)"[^>]+href="([^"]+)"', html)
    frames = re.findall(r'<svg id="loading-x-anim-\d+"[^>]*>(.*?)</svg>', html, re.S)
    paths = [re.findall(r'<path d="([^"]+)"', f) for f in frames]
    print(json.dumps({
        "page": page, "status": status, "bytes": len(html),
        "verification": bool(re.search(r'name="twitter-site-verification"\s+content="[^"]+"', html)),
        "frames": len(frames), "paths_per_frame": [len(p) for p in paths],
        "second_path_has_C": [len(p) > 1 and "C" in p[1] for p in paths],
        "inline_index": bool(INDEX.search(html)),
        "scripts": [s.split("?")[0] for s in scripts],
        "preloads": [s.split("?")[0] for s in preloads][:20],
    }))
    if page != "https://x.com/i/flow/login":
        continue
    for src in scripts:
        if "abs.twimg.com" not in src:
            continue
        status, js = fetch(src)
        index = INDEX.search(js)
        entry = {"script": src.split("/")[-1], "status": status, "bytes": len(js),
                 "ondemand_text": js.count("ondemand.s"), "sign_o_text": js.count("sign.o"),
                 "index": index[1] if index else None}
        if index:
            hashed = re.search(r"""(?:^|[,{])\s*["']?%s["']?\s*:\s*["']([a-zA-Z0-9_-]+)["']""" % re.escape(index[1]), js, re.M)
            entry["hash"] = hashed[1] if hashed else None
            if hashed:
                url = f"https://abs.twimg.com/responsive-web/client-web/ondemand.s.{hashed[1]}a.js"
                status, signer = fetch(url)
                entry["signer_status"] = status
                entry["signer_indices"] = len(INDICES.findall(signer))
        refs = sorted(set(re.findall(r"""["'`]([^"'`\s$]*(?:sign\.o|ondemand\.s)[^"'`\s$]*\.js)["'`]""", js)))
        entry["literal_refs"] = refs[:5]
        print(json.dumps(entry))
