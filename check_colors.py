"""Read PMX material colors"""
import struct, os

# Find the PMX file
path = None
for root, dirs, files in os.walk(r'D:\_dx\_Games\MC\_agent'):
    for f in files:
        if f.endswith('.pmx'):
            path = os.path.join(root, f)
            break
    if path: break
if not path:
    print("PMX not found")
    exit(1)

print(f"File: {os.path.getsize(path)} bytes")
with open(path, 'rb') as f:
    buf = f.read()

pos = 17
enc = buf[9]
add_uv = buf[10]
v_idx = buf[11]
t_idx = buf[12]
m_idx = buf[13]
b_idx = buf[14]

# Skip model info strings
for _ in range(4):
    slen = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4
    if slen > 0: pos += slen

# Vertices
vc = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4
print(f"Vertices: {vc}")

# Skip all vertices
for vi in range(vc):
    pos += 32 + add_uv * 16 + 1  # base + deform type byte
    deform = buf[pos-1]
    if deform == 0: pos += b_idx
    elif deform == 1: pos += b_idx * 2 + 4
    elif deform == 2: pos += b_idx * 4 + 16
    elif deform == 3: pos += b_idx * 2 + 4 + 36
    else: pos += b_idx * 4 + 16
    pos += 4  # edge

# Faces
fc = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4
print(f"Faces (indices): {fc} = {fc//3} triangles")
pos += fc * v_idx

# Textures
tc = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4
print(f"Textures: {tc}")
for i in range(tc):
    slen = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4
    if slen > 0: pos += slen

# === MATERIALS ===
mc = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4
print(f"\n=== {mc} Materials ===")

colors_seen = {}
for i in range(mc):
    # names
    for _ in range(2):
        slen = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4
        if slen > 0: pos += slen
    # diffuse RGBA
    dr = struct.unpack('<f', buf[pos:pos+4])[0]; pos += 4
    dg = struct.unpack('<f', buf[pos:pos+4])[0]; pos += 4
    db = struct.unpack('<f', buf[pos:pos+4])[0]; pos += 4
    da = struct.unpack('<f', buf[pos:pos+4])[0]; pos += 4
    # ambient (3 floats)
    ar = struct.unpack('<f', buf[pos:pos+4])[0]; pos += 4
    ag = struct.unpack('<f', buf[pos:pos+4])[0]; pos += 4
    ab = struct.unpack('<f', buf[pos:pos+4])[0]; pos += 4
    # specular (3f + power float)
    pos += 16
    # edge color (4f) + edge size (1f)
    pos += 20
    # tex index (t_idx bytes)
    tex = 0
    if t_idx == 1: tex = buf[pos]; pos += 1
    elif t_idx == 2: tex = struct.unpack('<H', buf[pos:pos+2])[0]; pos += 2
    elif t_idx == 4: tex = struct.unpack('<I', buf[pos:pos+4])[0]; pos += 4
    else: pos += 1
    # spa tex
    pos += t_idx if t_idx > 0 else 1
    # spa flag
    pos += 1
    # toon
    toon = buf[pos]; pos += 1
    if toon == 0: pos += t_idx if t_idx > 0 else 1
    else: pos += 1
    # memo
    slen = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4
    if slen > 0: pos += slen
    # face vertex count (per material)
    fvc = struct.unpack('<i', buf[pos:pos+4])[0]; pos += 4

    maxc = max(dr, dg, db)
    r_type = "BYTE-range(>10)" if maxc > 10 else "float(0-1)"
    color_key = f"{int(dr)},{int(dg)},{int(db)}"
    if color_key not in colors_seen:
        colors_seen[color_key] = 0
    colors_seen[color_key] += 1

    if i < 30:
        print(f"  Mat#{i}: RGB({dr:.1f},{dg:.1f},{db:.1f}) [{r_type}] faces={fvc//3} tex={tex}")

print(f"\nUnique colors: {len(colors_seen)}")
for c, n in sorted(colors_seen.items(), key=lambda x: -x[1])[:10]:
    r,g,b = map(int, c.split(','))
    print(f"  RGB({r:3d},{g:3d},{b:3d}) x{n} materials")

# Check: if all bytes, what does our packColor produce?
print("\n=== Testing packColor ===")
for c, n in list(sorted(colors_seen.items(), key=lambda x: -x[1]))[:5]:
    r,g,b = map(int, c.split(','))
    maxc = max(r,g,b)
    if maxc > 10:
        # byte range → our new code: just lightFactor
        for lf in [0.7, 1.0]:
            pr = min(255, max(0, int(r * lf)))
            pg = min(255, max(0, int(g * lf)))
            pb = min(255, max(0, int(b * lf)))
            print(f"  ({r},{g},{b}) * {lf} = ({pr},{pg},{pb})")
    else:
        for lf in [0.7, 1.0]:
            pr = min(255, max(0, int(r * 255 * lf)))
            pg = min(255, max(0, int(g * 255 * lf)))
            pb = min(255, max(0, int(b * 255 * lf)))
            print(f"  ({r:.2f},{g:.2f},{b:.2f}) *255*{lf} = ({pr},{pg},{pb})")
