"""Temporary: dump x.com's sign-in page so its script loading can be read."""
import base64
import gzip
from urllib.request import Request, urlopen

BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
request = Request("https://x.com/i/flow/login", headers={"User-Agent": BROWSER, "Accept-Language": "en-US,en;q=0.9"})
with urlopen(request, timeout=15) as response:
    body = response.read()
    print("status", response.status, "bytes", len(body))
    for key, value in response.headers.items():
        if key.lower() not in ("set-cookie",):
            print("header", key, value[:120])
packed = base64.b64encode(gzip.compress(body)).decode()
for i in range(0, len(packed), 4000):
    print("@@login " + packed[i:i + 4000])
