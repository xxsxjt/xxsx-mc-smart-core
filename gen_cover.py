"""生成 MCMod 模组封面 720x450"""
from PIL import Image, ImageDraw, ImageFont
import os

W, H = 720, 450
img = Image.new('RGB', (W, H), (25, 25, 35))  # 深色背景
draw = ImageDraw.Draw(img)

# 渐变背景（上深下稍亮）
for y in range(H):
    r = int(25 + (60-25) * y/H)
    g = int(25 + (55-25) * y/H)
    b = int(35 + (75-35) * y/H)
    draw.rectangle([(0, y), (W, y)], fill=(r, g, b))

# 像素网格装饰
for x in range(0, W, 16):
    draw.line([(x, 0), (x, H)], fill=(35, 35, 48), width=1)
for y in range(0, H, 16):
    draw.line([(0, y), (W, y)], fill=(35, 35, 48), width=1)

# 标题区域背景
draw.rectangle([(30, 60), (690, 180)], fill=(40, 40, 55), outline=(100, 200, 255), width=2)

# Try to find a CJK font
font_title = None
font_sub = None
font_small = None
for fp in [
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/simhei.ttf",
    "C:/Windows/Fonts/simsun.ttc",
    "C:/Windows/Fonts/msyhbd.ttc",
]:
    if os.path.exists(fp):
        try:
            font_title = ImageFont.truetype(fp, 42)
            font_sub = ImageFont.truetype(fp, 18)
            font_small = ImageFont.truetype(fp, 14)
            break
        except:
            pass
if font_title is None:
    font_title = ImageFont.load_default()
    font_sub = font_title
    font_small = font_title

# 标题
draw.text((W//2, 95), "xxsx的智能核心", fill=(100, 200, 255), font=font_title, anchor="mm")

# 副标题
draw.text((W//2, 148), "xxsx's MC Smart Core — AI", fill=(180, 220, 255), font=font_sub, anchor="mm")

# 功能标签
tags = ["自然语言 AI 对话", "指令自主执行", "PMX 体素建筑", "知识库系统", "AGPL-3.0"]
tag_colors = [(100,180,255), (140,200,255), (180,160,255), (140,220,180), (200,180,140)]
x_start = 40
y_tag = 210
for i, (tag, col) in enumerate(zip(tags, tag_colors)):
    x = x_start + i * 130
    # Tag background
    draw.rounded_rectangle([(x, y_tag), (x+120, y_tag+32)], radius=6, fill=col, outline=col)
    # Tag text (dark)
    draw.text((x+60, y_tag+16), tag, fill=(20, 30, 50), font=font_small, anchor="mm")

# 底部信息
draw.rectangle([(30, 270), (690, 420)], fill=(30, 30, 45), outline=(60, 60, 80), width=1)
info_lines = [
    "Minecraft 1.20.1  Forge 47.2.0+  Java 17",
    "",
    "/ai <消息>  让 AI 理解你的意图，自主执行指令",
    "/ai model  切换 AI 模型  |  /ai addmodel  添加自己的 Key",
    "/ai build <PMX>  PMX 模型 → Minecraft 体素建筑",
    "",
    "内置免费共享 Key · 开箱即用 · GitHub: xxsxjt/xxsx-mc-smart-core",
]
for i, line in enumerate(info_lines):
    color = (160, 180, 200) if line else (80, 80, 100)
    draw.text((50, 285 + i*20), line, fill=color, font=font_small)

# 作者署名
draw.text((W//2, 438), "xxsx 2026", fill=(100, 120, 150), font=font_small, anchor="mm")

# 保存
out = "mod_cover.png"
img.save(out)
print(f"Cover saved: {out} ({W}x{H})")
print(f"Size: {os.path.getsize(out)} bytes")
