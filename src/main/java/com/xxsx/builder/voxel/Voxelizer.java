package com.xxsx.builder.voxel;

import com.xxsx.builder.XxsxBuilder;
import org.joml.Vector3f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** 将 PMX 三角网格体素化为彩色方块网格 */
public class Voxelizer {

    /** 体素化结果 */
    public static class VoxelGrid {
        public final int width, height, depth;
        public final int offsetX, offsetY, offsetZ;
        /** 三维数组 [x][y][z] → ARGB 颜色，null 表示空 */
        public final int[][][] colors;

        /** 体素总数（非空） */
        public int filledCount = 0;

        public VoxelGrid(int w, int h, int d, int ox, int oy, int oz) {
            this.width = w; this.height = h; this.depth = d;
            this.offsetX = ox; this.offsetY = oy; this.offsetZ = oz;
            this.colors = new int[w][h][d];
        }
    }

    /**
     * 体素化 PMX 模型
     * @param model PMX 模型
     * @param scale 目标大小（最大轴向的格子数），支持浮点精细控制
     * @return 体素网格
     */
    public static VoxelGrid voxelize(PMXModel model, float scale) {
        // 1. 计算包围盒
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;
        for (Vector3f v : model.vertices) {
            if (v.x < minX) minX = v.x; if (v.y < minY) minY = v.y; if (v.z < minZ) minZ = v.z;
            if (v.x > maxX) maxX = v.x; if (v.y > maxY) maxY = v.y; if (v.z > maxZ) maxZ = v.z;
        }
        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new RuntimeException("模型包围盒无效: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }

        // 2. 计算缩放
        float maxDim = Math.max(sizeX, Math.max(sizeY, sizeZ));
        float s = scale / maxDim;

        int gridW = Math.max(1, Math.round(sizeX * s));
        int gridH = Math.max(1, Math.round(sizeY * s));
        int gridD = Math.max(1, Math.round(sizeZ * s));

        // 偏移使模型居中于网格
        int offsetX = 0;
        int offsetY = 0;
        int offsetZ = 0;

        XxsxBuilder.LOGGER.info("[Voxel] 体素网格: {}x{}x{}, 缩放: {}", gridW, gridH, gridD, s);

        VoxelGrid grid = new VoxelGrid(gridW, gridH, gridD, offsetX, offsetY, offsetZ);

        // 3. 将顶点缩放到网格空间
        List<Vector3f> scaledVerts = new ArrayList<>(model.vertices.size());
        for (Vector3f v : model.vertices) {
            float sx = (v.x - minX) * s;
            float sy = (v.y - minY) * s;
            float sz = (v.z - minZ) * s;
            scaledVerts.add(new Vector3f(sx, sy, sz));
        }

        // 4. 材质颜色 — 优先按面UV采样纹理
        List<float[]> matDiffuse = new ArrayList<>();
        for (PMXModel.PMXMaterial mat : model.materials) {
            matDiffuse.add(mat.getDiffuseRGB());
        }

        // 预加载所有纹理（支持 TGA + PNG）
        Map<Integer, byte[]> texCache = new HashMap<>();
        int texLoadOk = 0, texLoadFail = 0, texSkipped = 0;
        if (model.textureBaseDir != null) {
            for (PMXModel.PMXMaterial mat : model.materials) {
                String tp = mat.texturePath;
                if (tp == null || tp.isEmpty()) {
                    texSkipped++;
                    continue;
                }
                java.nio.file.Path fp = java.nio.file.Paths.get(model.textureBaseDir, tp);
                if (!texCache.containsKey(System.identityHashCode(mat))) {
                    try {
                        byte[] raw = loadTextureRGBA(fp);
                        if (raw != null) {
                            texCache.put(System.identityHashCode(mat), raw);
                            texLoadOk++;
                        } else {
                            texLoadFail++;
                        }
                    } catch (Exception e) {
                        XxsxBuilder.LOGGER.warn("[Voxel] 纹理加载失败: {} → {}", tp, e.getMessage());
                        texLoadFail++;
                    }
                }
            }
            XxsxBuilder.LOGGER.info("[Voxel] 预加载纹理: {}成功 {}失败 {}无贴图 / {}材质",
                texLoadOk, texLoadFail, texSkipped, model.materials.size());
        } else {
            XxsxBuilder.LOGGER.warn("[Voxel] textureBaseDir=null，无法加载贴图，将使用材质漫反射色");
        }

        // 5. 对每个三角形进行体素化（3D 重心坐标 + 逐体素 UV 插值 + 超采样抗锯齿）
        int faceCount = model.faces.size();
        int texHitCount = 0, texMissCount = 0;
        int texDataOk = 0, texDataNull = 0, uvOk = 0, uvNull = 0, noTexturePath = 0;
        for (int fi = 0; fi < faceCount; fi++) {
            int[] tri = model.faces.get(fi);
            Vector3f v0 = scaledVerts.get(tri[0]);
            Vector3f v1 = scaledVerts.get(tri[1]);
            Vector3f v2 = scaledVerts.get(tri[2]);

            // 三角形的网格空间 AABB
            int triMinX = clamp((int) Math.floor(min3(v0.x, v1.x, v2.x)), -1, gridW);
            int triMinY = clamp((int) Math.floor(min3(v0.y, v1.y, v2.y)), -1, gridH);
            int triMinZ = clamp((int) Math.floor(min3(v0.z, v1.z, v2.z)), -1, gridD);
            int triMaxX = clamp((int) Math.ceil(max3(v0.x, v1.x, v2.x)), -1, gridW);
            int triMaxY = clamp((int) Math.ceil(max3(v0.y, v1.y, v2.y)), -1, gridH);
            int triMaxZ = clamp((int) Math.ceil(max3(v0.z, v1.z, v2.z)), -1, gridD);

            // 材质和纹理数据
            int matIdx = fi < model.faceMaterials.size() ? model.faceMaterials.get(fi) : 0;
            float[] fallbackColor = matIdx < matDiffuse.size() ? matDiffuse.get(matIdx) : new float[]{0.8f, 0.8f, 0.8f};
            byte[] texData = null;
            Vector2f uv0 = null, uv1 = null, uv2 = null;
            int texW = 0, texH = 0, texBpp = 0, texPixelStart = 0;

            if (matIdx < model.materials.size()) {
                texData = texCache.get(System.identityHashCode(model.materials.get(matIdx)));
                if (texData != null && texData.length >= 18) {
                    int imgType = texData[2] & 0xFF;
                    texW = ((texData[13] & 0xFF) << 8) | (texData[12] & 0xFF);
                    texH = ((texData[15] & 0xFF) << 8) | (texData[14] & 0xFF);
                    texBpp = texData[16] & 0xFF;
                    texPixelStart = 18 + (texData[0] & 0xFF);
                    if (imgType != 2 || texW <= 0 || texH <= 0 || (texBpp != 24 && texBpp != 32)) {
                        texData = null; // 格式不支持
                    }
                } else {
                    texData = null;
                }
            }
            if (texData != null && tri[0] < model.uvs.size() && tri[1] < model.uvs.size() && tri[2] < model.uvs.size()) {
                uv0 = model.uvs.get(tri[0]);
                uv1 = model.uvs.get(tri[1]);
                uv2 = model.uvs.get(tri[2]);
                uvOk++;
            } else {
                uvNull++;
            }
            if (texData != null) texDataOk++;
            else {
                texDataNull++;
                if (matIdx < model.materials.size() && (model.materials.get(matIdx).texturePath == null || model.materials.get(matIdx).texturePath.isEmpty()))
                    noTexturePath++;
            }

            // 诊断：前5个面打印详细信息
            if (fi < 5) {
                String tp = matIdx < model.materials.size() ? model.materials.get(matIdx).texturePath : "?";
                XxsxBuilder.LOGGER.info("[Voxel-diag] 面#{} mat#{} tex={} texData={} uv={} 回退色=({},{},{})",
                    fi, matIdx, tp != null ? tp : "null",
                    texData != null ? "有" + texW + "x" + texH : "无",
                    uv0 != null ? "有" : "无",
                    String.format("%.2f", fallbackColor[0]),
                    String.format("%.2f", fallbackColor[1]),
                    String.format("%.2f", fallbackColor[2]));
            }

            // 预计算 3D 重心坐标常量 (double 精度，避免大数值时 float 精度崩坏)
            double e1x = v1.x - v0.x, e1y = v1.y - v0.y, e1z = v1.z - v0.z;
            double e2x = v2.x - v0.x, e2y = v2.y - v0.y, e2z = v2.z - v0.z;
            double d00 = e1x * e1x + e1y * e1y + e1z * e1z;
            double d01 = e1x * e2x + e1y * e2y + e1z * e2z;
            double d11 = e2x * e2x + e2y * e2y + e2z * e2z;
            double denom = d00 * d11 - d01 * d01;
            if (Math.abs(denom) < 1e-12) continue; // 退化三角形，跳过
            double invDenom = 1.0 / denom;

            // 纯材质预览，不模拟光照（MC 自带光照系统）

            // 遍历三角形 AABB 内的体素
            for (int vz = Math.max(0, triMinZ); vz <= Math.min(gridD - 1, triMaxZ); vz++) {
                for (int vy = Math.max(0, triMinY); vy <= Math.min(gridH - 1, triMaxY); vy++) {
                    for (int vx = Math.max(0, triMinX); vx <= Math.min(gridW - 1, triMaxX); vx++) {
                        if (grid.colors[vx][vy][vz] != 0) continue; // 已被其他三角形填充

                        // 快速路径：体素中心点在三角形内 (double 精度)
                        double cx = vx + 0.5, cy = vy + 0.5, cz = vz + 0.5;
                        double epx = cx - v0.x, epy = cy - v0.y, epz = cz - v0.z;
                        double d20 = epx * e1x + epy * e1y + epz * e1z;
                        double d21 = epx * e2x + epy * e2y + epz * e2z;
                        double bv = (d11 * d20 - d01 * d21) * invDenom;
                        double bw = (d00 * d21 - d01 * d20) * invDenom;

                        boolean inside = (bv >= -1e-6 && bw >= -1e-6 && bv + bw <= 1.0 + 1e-6);

                        // 超采样抗锯齿：中心不在三角形内时，检测 2×2×2 子采样点
                        if (!inside) {
                            int hits = 0;
                            final int SS = 2;
                            double sumV = 0, sumW = 0;
                            for (int sz = 0; sz < SS && hits < SS * SS; sz++) {
                                for (int sy = 0; sy < SS; sy++) {
                                    for (int sx = 0; sx < SS; sx++) {
                                        double spx = vx + (sx + 0.5) / SS;
                                        double spy = vy + (sy + 0.5) / SS;
                                        double spz = vz + (sz + 0.5) / SS;
                                        double sepx = spx - v0.x, sepy = spy - v0.y, sepz = spz - v0.z;
                                        double sd20 = sepx * e1x + sepy * e1y + sepz * e1z;
                                        double sd21 = sepx * e2x + sepy * e2y + sepz * e2z;
                                        double sv = (d11 * sd20 - d01 * sd21) * invDenom;
                                        double sw = (d00 * sd21 - d01 * sd20) * invDenom;
                                        if (sv >= -1e-6 && sw >= -1e-6 && sv + sw <= 1.0 + 1e-6) {
                                            hits++;
                                            sumV += sv;
                                            sumW += sw;
                                        }
                                    }
                                }
                            }
                            // 至少一半子采样命中才算覆盖
                            if (hits >= SS * SS * SS / 2) {
                                inside = true;
                                bv = sumV / hits;
                                bw = sumW / hits;
                            }
                        }

                        if (!inside) continue;

                        // 逐体素 UV 插值 + 纹理采样
                        float[] color = null;
                        if (uv0 != null && texData != null) {
                            double bu = 1.0 - bv - bw;
                            float iu = (float)(bu * uv0.x + bv * uv1.x + bw * uv2.x);
                            float iv = (float)(bu * uv0.y + bv * uv1.y + bw * uv2.y);
                            color = sampleTexAtUV(texData, texW, texH, texBpp, texPixelStart, iu, iv);
                            if (color != null) texHitCount++;
                            else texMissCount++;
                            // 诊断：前3个命中/未命中
                            if (texHitCount + texMissCount <= 3) {
                                XxsxBuilder.LOGGER.info("[Voxel-diag] 采样 uv=({},{}) -> color={}",
                                    String.format("%.3f", iu), String.format("%.3f", iv),
                                    color != null ? String.format("(%.2f,%.2f,%.2f)", color[0], color[1], color[2]) : "null");
                            }
                        }
                        if (color == null) {
                            if (uv0 == null || texData == null) texMissCount++;
                            color = fallbackColor;
                            // 诊断：前3个回退
                            if (texMissCount <= 3 && grid.filledCount <= 3) {
                                XxsxBuilder.LOGGER.info("[Voxel-diag] 回退色=({},{},{})",
                                    String.format("%.2f", fallbackColor[0]),
                                    String.format("%.2f", fallbackColor[1]),
                                    String.format("%.2f", fallbackColor[2]));
                            }
                        }

                        int packedColor = packColor(color);
                        grid.colors[vx][vy][vz] = packedColor;
                        grid.filledCount++;
                    }
                }
            }
        }

        XxsxBuilder.LOGGER.info("[Voxel] 体素化完成: {} 填充体素, 纹理采样 {}/{} 命中 | 纹理:{}有 {}无({}无路径) UV:{}有 {}无",
            grid.filledCount, texHitCount, texHitCount + texMissCount,
            texDataOk, texDataNull, noTexturePath, uvOk, uvNull);
        return grid;
    }

    private static float min3(float a, float b, float c) {
        return Math.min(a, Math.min(b, c));
    }

    private static float max3(float a, float b, float c) {
        return Math.max(a, Math.max(b, c));
    }

    /** 在指定 UV 坐标采样纹理。透明像素向中心偏移重试，避免回退到白色diffuse */
    private static float[] sampleTexAtUV(byte[] data, int w, int h, int bpp, int pixelStart,
                                          float u, float v) {
        try {
            float cu = u, cv = v;
            for (int retry = 0; retry < 8; retry++) {
                int px = clamp((int) (cu * (w - 1)), 0, w - 1);
                int py = clamp((int) ((1f - cv) * (h - 1)), 0, h - 1);
                int bpr = bpp / 8;
                int idx = pixelStart + (py * w + px) * bpr;
                if (idx + bpr > data.length) return null;
                if (bpp == 32) {
                    int a = data[idx + 3] & 0xFF;
                    if (a >= 128) { // 不透明，返回
                        int b = data[idx] & 0xFF;
                        int g = data[idx + 1] & 0xFF;
                        int r = data[idx + 2] & 0xFF;
                        return new float[]{r / 255f, g / 255f, b / 255f};
                    }
                } else {
                    int b = data[idx] & 0xFF;
                    int g = data[idx + 1] & 0xFF;
                    int r = data[idx + 2] & 0xFF;
                    return new float[]{r / 255f, g / 255f, b / 255f};
                }
                // 透明像素：向纹理中心(0.5,0.5)偏移一步重试
                cu = cu + (0.5f - cu) * 0.3f;
                cv = cv + (0.5f - cv) * 0.3f;
            }
            return null; // 8次都是透明，放弃
        } catch (Exception e) {
            return null;
        }
    }

    /** 加载纹理文件（支持 TGA + PNG），返回 TGA 兼容格式的字节数组 */
    private static byte[] loadTextureRGBA(java.nio.file.Path fp) throws Exception {
        String name = fp.getFileName().toString().toLowerCase();
        if (name.endsWith(".tga")) {
            byte[] d = java.nio.file.Files.readAllBytes(fp);
            if (d.length < 18) return null;
            int imgType = d[2] & 0xFF;
            int w = ((d[13] & 0xFF) << 8) | (d[12] & 0xFF);
            int h = ((d[15] & 0xFF) << 8) | (d[14] & 0xFF);
            if (imgType == 2 && w > 0 && h > 0) return d; // 无压缩 RGB TGA，原样返回
            XxsxBuilder.LOGGER.warn("[Voxel] TGA格式不支持: {} (type={})", name, imgType);
            return null;
        }
        // PNG / 其他 → 用 ImageIO 解码，转为 TGA 兼容格式（BGR 底部优先）
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(fp.toFile());
            if (img == null) {
                XxsxBuilder.LOGGER.warn("[Voxel] 无法解码图片: {}", name);
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w <= 0 || h <= 0) return null;

            // 构建 TGA 兼容字节数组: 18字节伪头部 + BGRA像素(底部优先)，保留alpha用于透明检测
            int bpp = 32;
            byte[] raw = new byte[18 + w * h * 4];
            raw[2] = 2; // imgType = 无压缩 RGB
            raw[12] = (byte)(w & 0xFF);
            raw[13] = (byte)((w >> 8) & 0xFF);
            raw[14] = (byte)(h & 0xFF);
            raw[15] = (byte)((h >> 8) & 0xFF);
            raw[16] = (byte)bpp;
            // raw[0] = 0 (ID length), raw[17] = 0 (descriptor)

            // 逐像素读取，底部优先存储 BGRA
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    int idx = 18 + ((h - 1 - y) * w + x) * 4;
                    raw[idx]     = (byte)(argb & 0xFF);         // B
                    raw[idx + 1] = (byte)((argb >> 8) & 0xFF);  // G
                    raw[idx + 2] = (byte)((argb >> 16) & 0xFF); // R
                    raw[idx + 3] = (byte)((argb >> 24) & 0xFF); // A
                }
            }
            return raw;
        } catch (Exception e) {
            XxsxBuilder.LOGGER.warn("[Voxel] PNG加载失败: {} → {}", name, e.getMessage());
            return null;
        }
    }

    private static int packColor(float[] rgb) {
        return packColor(rgb, 1.0f);
    }

    private static int packColor(float[] rgb, float lightFactor) {
        float maxC = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        // 检测范围：>10 说明存的是 0-255 字节值，只需应用光照
        if (maxC > 10.0f) {
            return (255 << 24) |
                (clamp((int)(rgb[0] * lightFactor), 0, 255) << 16) |
                (clamp((int)(rgb[1] * lightFactor), 0, 255) << 8) |
                clamp((int)(rgb[2] * lightFactor), 0, 255);
        }
        // 0-1 浮点：先放大到 0-255 再应用光照
        return (255 << 24) |
            (clamp((int)(rgb[0] * 255.0f * lightFactor), 0, 255) << 16) |
            (clamp((int)(rgb[1] * 255.0f * lightFactor), 0, 255) << 8) |
            clamp((int)(rgb[2] * 255.0f * lightFactor), 0, 255);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

}
