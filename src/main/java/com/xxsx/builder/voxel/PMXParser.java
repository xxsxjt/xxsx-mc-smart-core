package com.xxsx.builder.voxel;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.config.ModLogger;
import org.joml.Vector3f;
import org.joml.Vector2f;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PMX 2.0 二进制解析器。
 * 只读取体素化所需字段：顶点、法线、UV、三角面、材质颜色。
 */
public class PMXParser {
    private ByteBuffer buf;
    private String phase = "init";

    public PMXModel parse(Path filePath) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r");
             FileChannel ch = file.getChannel()) {
            long fileSize = ch.size();
            ModLogger.info("PMX parse: 文件大小 " + fileSize + " 字节");
            byte[] data = new byte[(int) fileSize];
            ch.read(ByteBuffer.wrap(data));
            buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        }
        try {
            PMXModel model = parseModel();
            model.textureBaseDir = filePath.getParent() != null ? filePath.getParent().toString() : ".";
            return model;
        } catch (Exception e) {
            throw new RuntimeException("解析失败 (phase=" + phase + ", pos=" + buf.position() + "/" + buf.capacity() + "): " + e.getMessage(), e);
        }
    }

    private PMXModel parseModel() {
        PMXModel model = new PMXModel();

        // 先 dump 整个文件头 64 字节
        buf.mark();
        byte[] fullHeader = new byte[Math.min(64, buf.capacity())];
        buf.get(fullHeader);
        buf.reset();
        StringBuilder hex0 = new StringBuilder();
        for (int i = 0; i < fullHeader.length; i++) {
            hex0.append(String.format("%02x ", fullHeader[i]));
        }
        ModLogger.info("PMX full header (0-63): " + hex0.toString());

        // === Header ===
        phase = "header magic";
        byte[] magic = new byte[4];
        buf.get(magic);
        String magicStr = new String(magic, StandardCharsets.US_ASCII);
        if (!"PMX ".equals(magicStr)) {
            throw new RuntimeException("非法的 PMX 文件: 魔术字='" + magicStr + "' 应为'PMX '");
        }

        phase = "header version";
        float version = buf.getFloat();
        ModLogger.info("PMX 版本: " + version);
        if (version < 2.0f || version > 2.1f) {
            throw new RuntimeException("不支持的 PMX 版本: " + version + " (仅支持 2.0~2.1)");
        }

        // 编码配置
        // PMX 2.0 规范: byte8=HeaderSize(总=8), byte9=编码, byte10=额外UV, byte11=顶点IdxSz ...
        phase = "header config";
        int headerSize = buf.get() & 0xFF;     // byte 8: 总=8
        int encoding = buf.get() & 0xFF;       // byte 9: 0=UTF16, 1=UTF8
        int additionalUVs = buf.get() & 0xFF;  // byte 10
        int vertexIdxSize = buf.get() & 0xFF;  // byte 11: 1,2,4
        int textureIdxSize = buf.get() & 0xFF; // byte 12
        int materialIdxSize = buf.get() & 0xFF;// byte 13
        int boneIdxSize = buf.get() & 0xFF;    // byte 14
        int morphIdxSize = buf.get() & 0xFF;   // byte 15
        int rigidIdxSize = buf.get() & 0xFF;   // byte 16
        Charset cs = (encoding == 1) ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE;
        ModLogger.info(String.format("PMX config: hdrSz=%d enc=%d addUV=%d vIdx=%d tIdx=%d mIdx=%d bIdx=%d",
                headerSize, encoding, additionalUVs, vertexIdxSize, textureIdxSize, materialIdxSize, boneIdxSize));
        ModLogger.info("PMX encoding: byte=" + encoding + " -> " + cs.name());

        // === Model Info（跳过，我们用不到模型名/注释，只须正确跨过这段）===
        phase = "model info skip";
        int si_remaining = 4; // PMX 2.0 标准规定 4 个字符串
        while (si_remaining-- > 0 && buf.remaining() >= 4) {
            int slen = buf.getInt();
            if (slen < 0 || slen > buf.remaining()) {
                // 字符串长度不合理→ model info 结束，回退 4 字节（vertex 数据开始）
                buf.position(buf.position() - 4);
                ModLogger.info("PMX model-info 提前结束: 第" + (5-si_remaining) + "个字符串长度=" + slen
                    + " 剩余=" + buf.remaining() + " pos=" + buf.position());
                break;
            }
            if (slen > 0) {
                buf.position(buf.position() + slen); // 跳过内容
            }
        }

        // === Vertices ===
        phase = "vertex count";
        int vertexCount = buf.getInt();
        int maxVert = buf.capacity() / 12;
        int scanStartPos = buf.position();

        if (vertexCount <= 0 || vertexCount > maxVert) {
            ModLogger.info("PMX 顶点数异常: " + vertexCount + "，从位置 " + scanStartPos + " 扫描中...");
            // 从当前位置按 4 字节步进扫描，找合理顶点数
            for (int offset = 0; offset < 4096; offset += 4) {
                if (buf.remaining() < 4) break;
                int candidate = buf.getInt();
                if (candidate > 0 && candidate < maxVert) {
                    long needed = (long) candidate * 38;
                    if (needed <= buf.remaining()) {
                        ModLogger.info("PMX 找到顶点数: " + candidate + " 在 pos=" + (buf.position()-4));
                        vertexCount = candidate;
                        break;
                    }
                }
            }
            // 如果没找到，用最大值兜底（会在后续读取中报错）
            if (vertexCount <= 0 || vertexCount > maxVert) {
                ModLogger.info("PMX 未找到合法顶点数，文件可能非标");
                throw new RuntimeException("无法定位顶点数据");
            }
        }
        ModLogger.info("PMX 顶点数: " + vertexCount);
        XxsxBuilder.LOGGER.info("[PMX] 顶点数: {}", vertexCount);

        for (int i = 0; i < vertexCount; i++) {
            phase = "vertex " + i + " position";
            float x = buf.getFloat(), y = buf.getFloat(), z = buf.getFloat();
            model.vertices.add(new Vector3f(x, y, z));

            phase = "vertex " + i + " normal";
            float nx = buf.getFloat(), ny = buf.getFloat(), nz = buf.getFloat();
            model.normals.add(new Vector3f(nx, ny, nz));

            phase = "vertex " + i + " uv";
            float u = buf.getFloat(), v = buf.getFloat();
            model.uvs.add(new Vector2f(u, v));

            // 跳过额外 UV
            phase = "vertex " + i + " extraUV";
            for (int j = 0; j < additionalUVs; j++) {
                buf.getFloat(); buf.getFloat(); buf.getFloat(); buf.getFloat();
            }

            // 权重变形
            phase = "vertex " + i + " deformType";
            int deformType = buf.get() & 0xFF;
            if (deformType == 0) { // BDEF1 — 1 bone, weight=1.0 (no weight float!)
                readIndex(boneIdxSize);
            } else if (deformType == 1) { // BDEF2
                readIndex(boneIdxSize); readIndex(boneIdxSize);
                buf.getFloat();
            } else if (deformType == 2) { // BDEF4
                readIndex(boneIdxSize); readIndex(boneIdxSize);
                readIndex(boneIdxSize); readIndex(boneIdxSize);
                buf.getFloat(); buf.getFloat(); buf.getFloat(); buf.getFloat();
            } else if (deformType == 3) { // SDEF
                readIndex(boneIdxSize); readIndex(boneIdxSize);
                buf.getFloat(); // weight
                buf.getFloat(); buf.getFloat(); buf.getFloat(); // C
                buf.getFloat(); buf.getFloat(); buf.getFloat(); // R0
                buf.getFloat(); buf.getFloat(); buf.getFloat(); // R1
            } else {
                // 未知变形类型（可能是 QDEF/PMX2.1），按 BDEF4 大小跳过后继续
                ModLogger.warn("顶点#" + i + " 未知deformType=" + deformType + "，按BDEF4跳过");
                readIndex(boneIdxSize); readIndex(boneIdxSize);
                readIndex(boneIdxSize); readIndex(boneIdxSize);
                buf.getFloat(); buf.getFloat(); buf.getFloat(); buf.getFloat();
            }

            phase = "vertex " + i + " edge";
            buf.getFloat(); // edge scale
        }

        // === Faces (三角面) ===
        phase = "face count";
        int faceVertexCount = buf.getInt();
        int faceCount = faceVertexCount / 3;
        ModLogger.info("PMX 面数: " + faceCount + " (顶点索引数: " + faceVertexCount + ")");
        XxsxBuilder.LOGGER.info("[PMX] 面数: {}", faceCount);

        for (int i = 0; i < faceCount; i++) {
            phase = "face " + i;
            int i0 = readIndex(vertexIdxSize);
            int i1 = readIndex(vertexIdxSize);
            int i2 = readIndex(vertexIdxSize);
            model.faces.add(new int[]{i0, i1, i2});
        }

        // === Textures ===
        phase = "texture count";
        int textureCount = buf.getInt();
        ModLogger.info("PMX 纹理数: " + textureCount);
        List<String> textures = new ArrayList<>();
        for (int i = 0; i < textureCount; i++) {
            phase = "texture " + i;
            textures.add(readString(cs));
        }

        // === Materials ===
        phase = "material count";
        int materialCount = buf.getInt();
        ModLogger.info("PMX 材质数: " + materialCount);
        XxsxBuilder.LOGGER.info("[PMX] 材质数: {}", materialCount);

        int faceIdx = 0;
        for (int i = 0; i < materialCount; i++) {
            phase = "material " + i + " name";
            String matName = readString(cs);
            String matNameEn = readString(cs);

            phase = "material " + i + " colors";
            float dr = buf.getFloat(), dg = buf.getFloat(), db = buf.getFloat(), da = buf.getFloat();
            float ar = buf.getFloat(), ag = buf.getFloat(), ab = buf.getFloat();
            float sr = buf.getFloat(), sg = buf.getFloat(), sb = buf.getFloat();
            float sp = buf.getFloat();
            int drawFlags = buf.get() & 0xFF; // Drawing flags (1 byte, between specular and edge)
            float[] edgeCol = new float[]{buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat()};
            float edgeSize = buf.getFloat();

            phase = "material " + i + " indices";
            int texIdx = readIndex(textureIdxSize);
            int spaTexIdx = readIndex(textureIdxSize);
            int spaFlag = buf.get() & 0xFF;
            int toonFlag = buf.get() & 0xFF;
            if (toonFlag == 0) readIndex(textureIdxSize);
            else readIndex(0); // internal toon index (1 byte)

            phase = "material " + i + " memo";
            readString(cs); // memo
            int faceVertCount = buf.getInt();

            String texPath = (texIdx >= 0 && texIdx < textures.size()) ? textures.get(texIdx) : "";
            model.materials.add(new PMXModel.PMXMaterial(
                    matName, new float[]{dr, dg, db, da},
                    new float[]{ar, ag, ab}, new float[]{sr, sg, sb},
                    sp, edgeCol, edgeSize, faceVertCount, texPath));

            // 记录该材质对应的面
            int facesForMaterial = faceVertCount / 3;
            for (int f = 0; f < facesForMaterial && faceIdx < model.faces.size(); f++) {
                model.faceMaterials.add(i);
                faceIdx++;
            }
        }

        ModLogger.info("PMX 解析完成: " + model.vertices.size() + "顶点, "
                + model.faces.size() + "面, " + model.materials.size() + "材质 (剩余字节: "
                + buf.remaining() + ")");
        XxsxBuilder.LOGGER.info("[PMX] 解析完成: {} 顶点, {} 三角形, {} 材质",
                model.vertices.size(), model.faces.size(), model.materials.size());
        return model;
    }

    private String readString(Charset cs) {
        if (buf.remaining() < 4) {
            throw new RuntimeException("字符串长度字段越界 (剩余 " + buf.remaining() + " 字节, 需要4)");
        }
        int len = buf.getInt();
        if (len <= 0) return "";
        // 合理性检查: 文本字符串通常不会超过 100KB
        if (len > 100_000) {
            // 出错时 dump 附近 48 字节帮助分析
            StringBuilder hex = new StringBuilder();
            int savedPos = buf.position();
            int dumpStart = Math.max(0, buf.position() - 16);
            int dumpLen = Math.min(48, buf.capacity() - dumpStart);
            buf.position(dumpStart);
            for (int i = 0; i < dumpLen; i++) {
                hex.append(String.format("%02x ", buf.get()));
            }
            buf.position(savedPos);
            throw new RuntimeException("字符串长度异常: " + len + " 字节 (位置 " + savedPos
                + ", 文件总大小 " + buf.capacity() + ")\n附近hex: " + hex.toString());
        }
        if (buf.remaining() < len) {
            throw new RuntimeException("字符串长度 " + len + " 超过剩余字节 " + buf.remaining());
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, cs);
    }

    private int readIndex(int size) {
        if (buf.remaining() < (size <= 1 ? 1 : size == 2 ? 2 : 4)) {
            throw new RuntimeException("索引读取超出边界 (size=" + size + ")");
        }
        if (size <= 1) return buf.get() & 0xFF;
        else if (size == 2) return buf.getShort() & 0xFFFF;
        else if (size == 4) return buf.getInt();
        return buf.get() & 0xFF;
    }
}
