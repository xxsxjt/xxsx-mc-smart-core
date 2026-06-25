"""Check PMX texture colors"""
import os, struct
from PIL import Image

# Walk to find the PMX directory
pmx_dir = None
for root, dirs, files in os.walk(r'D:\_dx\_Games\MC\_agent\安魂曲'):
    for f in files:
        if f.endswith('.pmx'):
            pmx_dir = root
            break
    if pmx_dir:
        break

if not pmx_dir:
    # Try parent
    for root, dirs, files in os.walk(r'D:\_dx\_Games\MC\_agent'):
        if '安魂曲' in root or 'tex' in dirs:
            for f in files:
                if f.endswith('.pmx'):
                    pmx_dir = root
                    break
        if pmx_dir:
            break

if not pmx_dir:
    print("PMX directory not found")
    exit(1)

print(f"PMX dir: {pmx_dir}")
tex_dir = os.path.join(pmx_dir, 'tex')
if not os.path.isdir(tex_dir):
    print(f"No tex directory at {tex_dir}")
    exit(1)

textures = sorted([f for f in os.listdir(tex_dir) if f.lower().endswith(('.tga','.png','.jpg','.bmp'))])
print(f"Found {len(textures)} texture files")

for tf in textures[:5]:
    fp = os.path.join(tex_dir, tf)
    try:
        img = Image.open(fp).convert('RGB')
        # Get average color
        s = img.resize((1, 1), Image.LANCZOS)
        c = s.getpixel((0, 0))
        print(f"  {tf}: {img.size} avg=RGB({c[0]},{c[1]},{c[2]})")
    except Exception as e:
        print(f"  {tf}: FAILED - {e}")
