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

        // === 陶瓦 (Terracotta) — 真实纹理色，非名字色 ===
        add("white_terracotta",      209, 177, 161); // 暖米色
        add("light_gray_terracotta", 135, 107,  98); // 棕褐
        add("gray_terracotta",        57,  41,  35); // 暗棕
        add("black_terracotta",       37,  22,  16); // 近黑
        add("brown_terracotta",       76,  50,  35); // 深棕
        add("red_terracotta",        142,  60,  46); // 暗红
        add("orange_terracotta",     159,  82,  36); // 暗橙
        add("yellow_terracotta",     186, 133,  36); // 土黄
        add("lime_terracotta",       103, 117,  53); // 橄榄绿
        add("green_terracotta",       76,  82,  42); // 暗绿
        add("cyan_terracotta",        87,  92,  92); // 青灰
        add("light_blue_terracotta", 112, 108, 138); // 灰蓝
        add("blue_terracotta",        76,  62,  92); // 暗紫
        add("purple_terracotta",     122,  73,  88); // 紫褐
        add("magenta_terracotta",    149,  87, 108); // 暗品红
        add("pink_terracotta",       160,  77,  78); // 暗红粉

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

        // === 暖白/米色系 ===
        add("bone_block",             228, 221, 207); // 暖奶油
        add("snow",                   253, 253, 253); // 纯白
        add("smooth_quartz",          236, 229, 222); // 冷白
        add("calcite",                224, 226, 221); // 灰白

        // === 沙/土/棕系 ===
        add("smooth_sandstone",       209, 200, 157); // 暖沙
        add("end_stone",              221, 223, 165); // 淡黄绿
        add("dripstone_block",        141, 104,  67); // 棕褐
        add("mud_bricks",             157, 132, 109); // 浅棕
        add("packed_mud",             144, 123,  98); // 中棕
        add("mud",                     50,  42,  37); // 深棕

        // === 紫系 ===
        add("purpur_block",           169, 125, 165); // 暗紫
        add("amethyst_block",         138, 102, 195); // 亮紫

        // === 青/蓝绿系 ===
        add("prismarine",              92, 131, 127); // 青绿
        add("prismarine_bricks",       98, 155, 150); // 亮青
        add("dark_prismarine",         56,  90,  75); // 暗青
        add("warped_wart_block",       38, 120, 120); // 深青

        // === 金/橙系 ===
        add("glowstone",              181, 148,  83); // 暖金
        add("hay_block",              180, 137,  33); // 金黄
        add("honeycomb_block",        197, 139,  55); // 金橙
        add("shroomlight",            245, 148,  69); // 亮橙
        add("gold_block",             255, 214,   0); // 纯金
        add("raw_gold_block",         190, 133,  39); // 暗金
        add("ochre_froglight",        245, 196,  96); // 浅金

        // === 暗色系 ===
        add("nether_bricks",           45,  23,  27); // 暗红
        add("nether_wart_block",      114,  32,  34); // 血红
        add("obsidian",                16,  14,  25); // 近黑
        add("tuff",                   108, 108, 102); // 中灰
        add("dried_kelp_block",        37,  54,  32); // 暗绿
        add("moss_block",              91, 118,  48); // 草绿
        add("magma_block",            158,  81,  41); // 焦橙
        add("red_nether_bricks",       70,   7,   9); // 深红
        add("crying_obsidian",         32,  16,  60); // 暗紫

        // === 木色/肤色系（补肉色到棕色过渡）===
        add("birch_planks",           196, 179, 123); // 浅米
        add("oak_planks",             184, 148,  95); // 暖棕
        add("jungle_planks",          167, 120,  85); // 中棕
        add("spruce_planks",          115,  85,  49); // 深棕
        add("dark_oak_planks",         61,  40,  18); // 暗棕
        add("cherry_planks",          221, 183, 171); // 淡粉
        add("mangrove_planks",        117,  54,  48); // 红棕
        add("bamboo_planks",          227, 219, 122); // 黄绿
        add("crimson_planks",         101,  48,  70); // 紫红
        add("warped_planks",           43, 104,  99); // 青绿

        // === 石/砖/砂岩系 ===
        add("bricks",                 165, 108,  78); // 砖红
        add("sandstone",              216, 202, 156); // 沙色
        add("red_sandstone",          186,  99,  29); // 橙棕
        add("terracotta",             152,  94,  67); // 素陶瓦
        add("granite",                154, 105,  79); // 花岗岩
        add("andesite",               138, 138, 135); // 安山岩
        add("diorite",                198, 198, 198); // 闪长岩
        add("basalt",                 116, 116, 122); // 玄武岩

        // === 铜/金属系 ===
        add("copper_block",           199, 123,  82); // 铜橙
        add("raw_iron_block",         216, 175, 147); // 浅肉色
        add("raw_copper_block",       186, 137,  94); // 铜棕

        // 总共 135 种纯色方块
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
