package com.xxsx.builder.voxel;

import com.xxsx.builder.XxsxBuilder;
import org.joml.Vector3f;
import org.joml.Vector2f;

import java.util.*;
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
     * @param scale 目标大小（最大轴向的格子数）
     * @return 体素网格
     */
    public static VoxelGrid voxelize(PMXModel model, int scale) {
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

        // 预加载所有纹理
        Map<Integer, byte[]> texCache = new HashMap<>();
        if (model.textureBaseDir != null) {
            for (PMXModel.PMXMaterial mat : model.materials) {
                String tp = mat.texturePath;
                if (tp == null || tp.isEmpty()) continue;
                java.nio.file.Path fp = java.nio.file.Paths.get(model.textureBaseDir, tp);
                if (!texCache.containsKey(mat.hashCode())) {
                    try {
                        byte[] d = java.nio.file.Files.readAllBytes(fp);
                        texCache.put(System.identityHashCode(mat), d);
                    } catch (Exception ignored) {}
                }
            }
            XxsxBuilder.LOGGER.info("[Voxel] 预加载纹理: {}/{}", texCache.size(), model.materials.size());
        }

        // 5. 对每个三角形进行体素化（表面体素化）
        // 使用保守光栅化：计算每个三角形的 AABB，遍历其中的体素测试包含关系
        int faceCount = model.faces.size();
        for (int fi = 0; fi < faceCount; fi++) {
            int[] tri = model.faces.get(fi);
            Vector3f v0 = scaledVerts.get(tri[0]);
            Vector3f v1 = scaledVerts.get(tri[1]);
            Vector3f v2 = scaledVerts.get(tri[2]);

            // 三角形的网格空间 AABB
            int triMinX = clamp((int) Math.floor(Math.min(v0.x, Math.min(v1.x, v2.x))), 0, gridW - 1);
            int triMinY = clamp((int) Math.floor(Math.min(v0.y, Math.min(v1.y, v2.y))), 0, gridH - 1);
            int triMinZ = clamp((int) Math.floor(Math.min(v0.z, Math.min(v1.z, v2.z))), 0, gridD - 1);
            int triMaxX = clamp((int) Math.ceil(Math.max(v0.x, Math.max(v1.x, v2.x))), 0, gridW - 1);
            int triMaxY = clamp((int) Math.ceil(Math.max(v0.y, Math.max(v1.y, v2.y))), 0, gridH - 1);
            int triMaxZ = clamp((int) Math.ceil(Math.max(v0.z, Math.max(v1.z, v2.z))), 0, gridD - 1);

            // 颜色：优先纹理采样，回退材质diffuse
            int matIdx = fi < model.faceMaterials.size() ? model.faceMaterials.get(fi) : 0;
            float[] color;
            // 尝试对面中心UV采样纹理
            color = sampleTexAtUV(model, matIdx, tri, texCache, matDiffuse);
            if (color == null)
                color = matIdx < matDiffuse.size() ? matDiffuse.get(matIdx) : new float[]{0.8f, 0.8f, 0.8f};
            // 法线着色
            float fnx = (v1.y - v0.y)*(v2.z - v0.z) - (v1.z - v0.z)*(v2.y - v0.y);
            float fny = (v1.z - v0.z)*(v2.x - v0.x) - (v1.x - v0.x)*(v2.z - v0.z);
            float fnz = (v1.x - v0.x)*(v2.y - v0.y) - (v1.y - v0.y)*(v2.x - v0.x);
            float fnLen = (float)Math.sqrt(fnx*fnx + fny*fny + fnz*fnz);
            float lightFactor = 0.6f + 0.4f * (fnLen > 0 ? Math.abs(fny) / fnLen : 0.5f); // 0.6~1.0 亮度变化
            int packedColor = packColor(color, lightFactor);

            // 遍历三角形内的体素
            for (int vz = triMinZ; vz <= triMaxZ; vz++) {
                for (int vy = triMinY; vy <= triMaxY; vy++) {
                    for (int vx = triMinX; vx <= triMaxX; vx++) {
                        float cx = vx + 0.5f, cy = vy + 0.5f, cz = vz + 0.5f;
                        if (grid.colors[vx][vy][vz] == 0 && pointInTriangle(cx, cy, cz, v0, v1, v2)) {
                            grid.colors[vx][vy][vz] = packedColor;
                            grid.filledCount++;
                        }
                    }
                }
            }
        }

        XxsxBuilder.LOGGER.info("[Voxel] 体素化完成: {} 填充体素", grid.filledCount);
        return grid;
    }

    /** 判断点是否在三角形内（3D 投影到 2D） */
    private static boolean pointInTriangle(float px, float py, float pz,
                                            Vector3f v0, Vector3f v1, Vector3f v2) {
        // 计算法线，选择投影轴（法线绝对值最大的分量）
        float nx = (v1.y - v0.y) * (v2.z - v0.z) - (v1.z - v0.z) * (v2.y - v0.y);
        float ny = (v1.z - v0.z) * (v2.x - v0.x) - (v1.x - v0.x) * (v2.z - v0.z);
        float nz = (v1.x - v0.x) * (v2.y - v0.y) - (v1.y - v0.y) * (v2.x - v0.x);

        // 选择法线分量最大的轴作为投影平面
        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);

        float u0, u1, u2, up;
        float vv0, vv1, vv2, vp;

        if (ax >= ay && ax >= az) {
            // YZ 平面投影
            u0 = v0.y; u1 = v1.y; u2 = v2.y; up = py;
            vv0 = v0.z; vv1 = v1.z; vv2 = v2.z; vp = pz;
        } else if (ay >= ax && ay >= az) {
            // XZ 平面投影
            u0 = v0.x; u1 = v1.x; u2 = v2.x; up = px;
            vv0 = v0.z; vv1 = v1.z; vv2 = v2.z; vp = pz;
        } else {
            // XY 平面投影
            u0 = v0.x; u1 = v1.x; u2 = v2.x; up = px;
            vv0 = v0.y; vv1 = v1.y; vv2 = v2.y; vp = py;
        }

        // 重心坐标系测试
        double du0 = up - u0, du1 = u1 - u0, du2 = u2 - u0;
        double dv0 = vp - vv0, dv1 = vv1 - vv0, dv2 = vv2 - vv0;
        double dot00 = du1 * du1 + dv1 * dv1;
        double dot01 = du1 * du2 + dv1 * dv2;
        double dot02 = du1 * du0 + dv1 * dv0;
        double dot11 = du2 * du2 + dv2 * dv2;
        double dot12 = du2 * du0 + dv2 * dv0;
        double inv = 1.0 / (dot00 * dot11 - dot01 * dot01);
        double u = (dot11 * dot02 - dot01 * dot12) * inv;
        double v = (dot00 * dot12 - dot01 * dot02) * inv;
        return (u >= 0) && (v >= 0) && (u + v < 1);
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

    /** 在三角形面中心 UV 采样纹理颜色 */
    private static float[] sampleTexAtUV(PMXModel model, int matIdx, int[] tri,
                                          Map<Integer, byte[]> texCache, List<float[]> fallback) {
        try {
            if (matIdx >= model.materials.size()) return null;
            PMXModel.PMXMaterial mat = model.materials.get(matIdx);
            if (mat.texturePath == null || mat.texturePath.isEmpty()) return null;
            byte[] data = texCache.get(System.identityHashCode(mat));
            if (data == null || data.length < 18) return null;

            // TGA header
            int w = ((data[13] & 0xFF) << 8) | (data[12] & 0xFF);
            int h = ((data[15] & 0xFF) << 8) | (data[14] & 0xFF);
            int bpp = data[16] & 0xFF;
            if (w <= 0 || h <= 0 || (bpp != 24 && bpp != 32)) return null;
            int pixelStart = 18 + (data[0] & 0xFF);

            // 计算三角形中心 UV
            if (tri[0] >= model.uvs.size() || tri[1] >= model.uvs.size() || tri[2] >= model.uvs.size())
                return null;
            org.joml.Vector2f uv0 = model.uvs.get(tri[0]);
            org.joml.Vector2f uv1 = model.uvs.get(tri[1]);
            org.joml.Vector2f uv2 = model.uvs.get(tri[2]);
            float cu = (uv0.x + uv1.x + uv2.x) / 3f;
            float cv = (uv0.y + uv1.y + uv2.y) / 3f;

            // UV → 像素坐标（TGA 底部优先）
            int px = clamp((int)(cu * (w - 1)), 0, w - 1);
            int py = clamp((int)((1f - cv) * (h - 1)), 0, h - 1); // TGA bottom-up
            int bpr = bpp / 8;
            int idx = pixelStart + (py * w + px) * bpr;
            if (idx + bpr > data.length) return null;

            int b = data[idx] & 0xFF;
            int g = data[idx + 1] & 0xFF;
            int r = data[idx + 2] & 0xFF;
            return new float[]{r / 255f, g / 255f, b / 255f};
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取纹理平均色。TGA 无压缩格式。 */
    private static float[] readTextureAverage(String texPath) {
        if (texPath == null || texPath.isEmpty()) return null;
        try {
            // 从 PMX 模型的纹理路径读取
            // texPath 可能是 Unicode 路径
            java.nio.file.Path p = java.nio.file.Paths.get(texPath.replace("\\", "/"));
            if (!java.nio.file.Files.exists(p)) return null;

            byte[] data = java.nio.file.Files.readAllBytes(p);
            if (data.length < 18) return null;

            // TGA header: width at bytes 12-13, height at 14-15, bpp at 16
            int w = ((data[13] & 0xFF) << 8) | (data[12] & 0xFF);
            int h = ((data[15] & 0xFF) << 8) | (data[14] & 0xFF);
            int bpp = data[16] & 0xFF;
            int imgType = data[2] & 0xFF;
            if (w <= 0 || h <= 0 || (bpp != 24 && bpp != 32)) return null;

            int pixelStart = 18 + (data[0] & 0xFF);
            if (imgType != 2) return null; // 只支持无压缩 RGB

            int bytesPerPixel = bpp / 8;
            long sumR = 0, sumG = 0, sumB = 0;
            int count = 0;
            int totalPixels = w * h;
            // 采样：从中间区域均匀取点（TGA 底部先存，跳过头尾）
            int skip = Math.max(1, totalPixels / 5000); // 最多 5000 个采样点
            int startOffset = totalPixels / 10; // 跳过底部 10%（可能是黑边）
            int endOffset = totalPixels - startOffset; // 跳过顶部 10%
            for (int i = startOffset; i < endOffset && count < 5000; i += skip) {
                int idx = pixelStart + (i * bytesPerPixel);
                if (idx + bytesPerPixel > data.length) break;
                int b = data[idx] & 0xFF;
                int g = data[idx + 1] & 0xFF;
                int r = data[idx + 2] & 0xFF;
                sumR += r; sumG += g; sumB += b;
                count++;
            }

            if (count == 0) return null;
            return new float[]{sumR / (255f * count), sumG / (255f * count), sumB / (255f * count)};
        } catch (Exception e) {
            return null;
        }
    }
}
