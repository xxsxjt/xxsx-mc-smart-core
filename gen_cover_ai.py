"""生成封面 — 内容集中在画面中段，适配 16:10 裁剪"""
import urllib.request, json, os
from PIL import Image

KEY = "sk-jBP1D6Z4FFmEimaDKyqmQMjfZuXzxHqedgcCwGzYAxo4Rwp0"
OUT = "mod_cover_v1.png"

prompt = (
    "All visual content MUST be in the center horizontal strip (middle 60%). "
    "Top 20% and bottom 20% must be plain dark blue background ONLY — nothing else. "
    "This is important: the image will be cropped to 16:10 widescreen, "
    "so keep all text and graphics away from top and bottom edges. "
    "Minecraft mod cover. Dark blue-purple background with faint circuit traces. "
    "Large pixel-art text 'XXSX' in bright cyan neon glow, centered horizontally. "
    "Below it: 'Smart Core' in smaller white pixel letters. "
    "Clean professional. No text near top or bottom edges."
)

print("Generating...")
body = json.dumps({"model": "agnes-image-2.0-flash", "prompt": prompt, "n": 1}).encode()
req = urllib.request.Request("https://apihub.agnes-ai.com/v1/images/generations",
    data=body, headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
r = json.loads(urllib.request.urlopen(req, timeout=120).read())
url = r["data"][0]["url"]
urllib.request.urlretrieve(url, OUT)
img = Image.open(OUT)
print(f"Original: {img.size} ({os.path.getsize(OUT)} bytes)")

# Center crop to 16:10 then resize
w, h = img.size
crop_h = int(w / 1.6)  # 640 for 1024w
top = (h - crop_h) // 2
img = img.crop((0, top, w, top + crop_h))
img = img.resize((720, 450), Image.LANCZOS)
img.save(OUT)
print(f"Cropped to 16:10: {img.size} — no stretching, pure center crop")
print(f"Final: {os.path.getsize(OUT)} bytes")
print("Done!")
