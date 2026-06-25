package com.xxsx.builder.voxel;

import java.util.ArrayList;
import java.util.List;

/** 颜色→Minecraft 方块映射，使用 LAB 色差匹配 */
public class BlockColorMapper {
    private static final List<BlockEntry> BLOCKS = new ArrayList<>();

    static {
        // === 混凝土 (Concrete) ===
        add("white_concrete",      249, 255, 254);
        add("orange_concrete",     240, 107,  23);
        add("magenta_concrete",    181,  29, 138);
        add("light_blue_concrete",  63, 175, 221);
        add("yellow_concrete",     254, 216,  61);
        add("lime_concrete",       128, 184,  66);
        add("pink_concrete",       237, 140, 170);
        add("gray_concrete",        71,  69,  60);
        add("light_gray_concrete", 157, 157, 151);
        add("cyan_concrete",        38, 115, 141);
        add("purple_concrete",     121,  42, 172);
        add("blue_concrete",        53,  57, 156);
        add("brown_concrete",       94,  58,  28);
        add("green_concrete",       75, 113,  63);
        add("red_concrete",        160,  47,  44);
        add("black_concrete",       25,  23,  22);

        // === 羊毛 (Wool) ===
        add("white_wool",          240, 240, 240);
        add("orange_wool",         242, 154,  46);
        add("magenta_wool",        179,  76, 194);
        add("light_blue_wool",     102, 153, 216);
        add("yellow_wool",         247, 232, 101);
        add("lime_wool",           178, 208,  81);
        add("pink_wool",           245, 189, 210);
        add("gray_wool",            76,  76,  76);
        add("light_gray_wool",     153, 153, 153);
        add("cyan_wool",            76, 127, 153);
        add("purple_wool",         127,  63, 178);
        add("blue_wool",            51,  51, 153);
        add("brown_wool",          102,  51,  26);
        add("green_wool",           51, 102,  51);
        add("red_wool",            153,  51,  51);
        add("black_wool",           51,  51,  51);

        // === 陶瓦 (Terracotta) ===
        add("white_terracotta",    209, 177, 161);
        add("orange_terracotta",   205, 107,  75);
        add("magenta_terracotta",  157,  98, 131);
        add("light_blue_terracotta",122, 132, 155);
        add("yellow_terracotta",   200, 141,  69);
        add("lime_terracotta",     139, 125,  75);
        add("pink_terracotta",     160, 125, 125);
        add("gray_terracotta",     118, 117, 107);
        add("light_gray_terracotta",167, 163, 157);
        add("cyan_terracotta",     117,  97,  88);
        add("purple_terracotta",   113,  83, 125);
        add("blue_terracotta",      91,  94, 122);
        add("brown_terracotta",     90,  61,  48);
        add("green_terracotta",     86,  69,  55);
        add("red_terracotta",      140,  59,  53);
        add("black_terracotta",     58,  44,  39);

        // === 玻璃 (Glass) ===
        add("white_stained_glass",       255, 255, 255);
        add("orange_stained_glass",      238, 118,  36);
        add("magenta_stained_glass",     189,  97, 183);
        add("light_blue_stained_glass",  104, 163, 212);
        add("yellow_stained_glass",      254, 231,  97);
        add("lime_stained_glass",        110, 175,  73);
        add("pink_stained_glass",        238, 150, 174);
        add("gray_stained_glass",         64,  59,  57);
        add("light_gray_stained_glass",  155, 155, 155);
        add("cyan_stained_glass",         37, 128, 154);
        add("purple_stained_glass",      150,  68, 198);
        add("blue_stained_glass",         52,  69, 177);
        add("brown_stained_glass",       110,  65,  37);
        add("green_stained_glass",       84, 129,  77);
        add("red_stained_glass",         152,  29,  36);
        add("black_stained_glass",       56,  56,  56);

        // 带釉陶瓦不用——图案复杂非纯色
        // 总共 64 种纯色方块（4类 x 16色）
    }

    private static void add(String block, int r, int g, int b) {
        float[] lab = rgbToLab(r, g, b);
        BLOCKS.add(new BlockEntry(block, r, g, b, lab));
    }

    /** 查找最接近目标颜色的方块 */
    public static BlockMatch findClosest(int r, int g, int b) {
        float[] targetLab = rgbToLab(r, g, b);
        BlockEntry best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockEntry entry : BLOCKS) {
            double dist = deltaE(targetLab, entry.lab);
            if (dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }
        if (best == null) return new BlockMatch("minecraft:white_concrete", 255, 255, 255);
        return new BlockMatch("minecraft:" + best.block, best.r, best.g, best.b);
    }

    // === CIE LAB Color Conversion ===

    private static float[] rgbToLab(int r, int g, int b) {
        float[] xyz = rgbToXyz(r, g, b);
        return xyzToLab(xyz[0], xyz[1], xyz[2]);
    }

    private static float[] rgbToXyz(int r, int g, int b) {
        double vr = srgbCompand(r / 255.0);
        double vg = srgbCompand(g / 255.0);
        double vb = srgbCompand(b / 255.0);
        double x = 0.4124564 * vr + 0.3575761 * vg + 0.1804375 * vb;
        double y = 0.2126729 * vr + 0.7151522 * vg + 0.0721750 * vb;
        double z = 0.0193339 * vr + 0.1191920 * vg + 0.9503041 * vb;
        return new float[]{(float) x, (float) y, (float) z};
    }

    private static double srgbCompand(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static float[] xyzToLab(float x, float y, float z) {
        double xn = x / 0.95047;
        double yn = y / 1.0;
        double zn = z / 1.08883;
        double fx = xn > 0.008856 ? Math.cbrt(xn) : (7.787 * xn + 16.0 / 116.0);
        double fy = yn > 0.008856 ? Math.cbrt(yn) : (7.787 * yn + 16.0 / 116.0);
        double fz = zn > 0.008856 ? Math.cbrt(zn) : (7.787 * zn + 16.0 / 116.0);
        return new float[]{(float) (116 * fy - 16), (float) (500 * (fx - fy)), (float) (200 * (fy - fz))};
    }

    /** CIE76 色差公式 */
    private static double deltaE(float[] lab1, float[] lab2) {
        double dl = lab1[0] - lab2[0];
        double da = lab1[1] - lab2[1];
        double db = lab1[2] - lab2[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    // === Data Classes ===

    public static class BlockMatch {
        public final String blockId;  // e.g. "minecraft:red_concrete"
        public final int r, g, b;

        public BlockMatch(String blockId, int r, int g, int b) {
            this.blockId = blockId;
            this.r = r; this.g = g; this.b = b;
        }
    }

    private static class BlockEntry {
        final String block;
        final int r, g, b;
        final float[] lab;  // CIE LAB

        BlockEntry(String block, int r, int g, int b, float[] lab) {
            this.block = block; this.r = r; this.g = g; this.b = b; this.lab = lab;
        }
    }
}
